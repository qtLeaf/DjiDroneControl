package dji.sampleV5.aircraft.tests

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.TextureView

import dji.sampleV5.aircraft.models.BasicAircraftControlVM
import dji.sampleV5.aircraft.models.SimulatorVM
import dji.sampleV5.aircraft.models.VirtualStickVM
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.v5.manager.KeyManager
import dji.sdk.keyvalue.key.CameraKey
import dji.v5.common.callback.CommonCallbacks
import dji.sdk.keyvalue.value.common.EmptyMsg

import dji.sampleV5.aircraft.tests.config.MqttConfig
import dji.sampleV5.aircraft.tests.control.VirtualFlightController
import dji.sampleV5.aircraft.tests.network.MqttPublisher
import dji.sampleV5.aircraft.tests.network.MqttSubscriber
import dji.sdk.keyvalue.value.camera.CameraMode
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.callback.CommonCallbacks.CompletionCallbackWithParam

import dji.v5.common.error.IDJIError
import dji.v5.et.create
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.datacenter.media.MediaFile
import dji.v5.manager.datacenter.media.MediaFileDownloadListener
import dji.v5.manager.datacenter.media.MediaFileListDataSource
import dji.v5.manager.datacenter.media.PullMediaFileListParam
import dji.v5.manager.interfaces.ICameraStreamManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream


import java.io.File
import android.util.Base64
import android.widget.Toast
import dji.sampleV5.aircraft.tests.camera.CameraGimbalController
import dji.sampleV5.aircraft.tests.factorCorrection.DroneCorrector
import dji.sampleV5.aircraft.tests.factorCorrection.ModelLoader
import dji.sampleV5.aircraft.tests.factorCorrection.moveCor
import dji.sampleV5.aircraft.tests.factorCorrection.rotationCor
import dji.sampleV5.aircraft.tests.navigation.WayPointNavigation
import dji.sdk.keyvalue.key.GimbalKey
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.sdk.keyvalue.value.gimbal.GimbalAngleRotation
import dji.v5.et.get
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.time.times

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

//mosquitto -c ~/mosquitto.conf
//nano ~/mosquitto.conf
//  listener 1883 0.0.0.0
//  allow_anonymous true

/**
 * Central controller used for automated drone testing and remote control.
 *
 * Responsibilities:
 * - Manage MQTT communication with a remote PC
 * - Periodically publish drone telemetry (location and attitude)
 * - Receive remote commands and translate them into drone actions
 * - Control drone movement using Virtual Stick
 * - Capture camera frames and send them via MQTT
 * - Control gimbal orientation and zoom
 *
 * The class acts as a bridge between:
 * - DJI SDK flight and camera APIs
 * - An MQTT-based remote control protocol
 *
 * Communication Model:
 * PC  <--MQTT-->  Drone
 *
 * Incoming commands are received through MQTT and handled in
 * [handleRemoteCommand].
 *
 * Telemetry is periodically published using [telemetryTask].
 *
 * @param basicAircraftControlVM DJI view model for aircraft basic controls
 * @param virtualStickVM DJI view model for virtual stick control
 * @param simulatorVM simulator interface used for testing
 * @param context Android context used for accessing media and system services
 * @param onDebug callback used to output debug messages
 */
class General(
    private val basicAircraftControlVM: BasicAircraftControlVM,
    private val virtualStickVM: VirtualStickVM,
    private val simulatorVM: SimulatorVM,
    private val context: Context,
    private val onDebug: (String) -> Unit,
) {

    private var mqttPublisher : MqttPublisher? = null

    private var mqttSubscriber: MqttSubscriber? = null

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val cameraIndex = ComponentIndexType.LEFT_OR_MAIN

    private val captureNextFrame = AtomicBoolean(false)

    private val cameraStreamManager: ICameraStreamManager by lazy {
        MediaDataCenter.getInstance().cameraStreamManager
    }

    /**
     * Starts the telemetry and remote control system.
     *
     * Actions performed:
     * - Establish MQTT connection
     * - Start the telemetry publishing loop
     * - Subscribe to remote commands
     * - Start camera frame listener
     *
     * If the system is already running, the method exits without restarting it.
     */
    fun startTelemetryTest() {
        if (running) return

        debug("Starting services for ${MqttConfig.HOST}...")

        // Connect to Publisher
        try {
            mqttPublisher = MqttPublisher()
            mqttPublisher?.connect()
        } catch (e: Exception) {
            debug("Publisher issue: ${e.message}")
        }

        // Connect to Subscriber
        try {
            mqttSubscriber = MqttSubscriber(
                onCommand = { payload ->
                    // activates for each msg arrive
                    handleRemoteCommand(payload)
                },
                onDebug = { msg -> debug(msg) }
            )

            mqttSubscriber?.connect()
            running = true
            setFlightLimit()

            telemetryTask.run()
            startCameraFrameListener()
            debug("Test started successfully")
        } catch (e: Exception) {
            debug("Subscriber critical error: ${e.message}")
        }
    }

    /**
     * Stops the telemetry system and closes the MQTT connection.
     *
     * This will:
     * - Stop telemetry publishing
     * - Remove scheduled tasks
     * - Disconnect MQTT publisher
     */
    fun stopTelemetryTest() {
        if (!running) return

        running = false
        handler.removeCallbacks(telemetryTask)
        mqttPublisher?.disconnect()
        mqttSubscriber?.disconnect()
        debug("Test stopped")
    }


    /**
     * Periodic telemetry publisher.
     *
     * Every 200 ms it reads:
     * - aircraft location
     * - aircraft attitude
     *
     * and sends them to the MQTT broker using [mqttPublisher].
     */
    private val telemetryTask = object : Runnable {
        override fun run() {
            if (!running) return

            try {
                val locationKey = KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D)
                val attitudeKey = KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude)

                val location = KeyManager.getInstance().getValue(locationKey)
                val attitude = KeyManager.getInstance().getValue(attitudeKey)

                /* debug for keys
                if (location == null) {
                    debug("Location is NULL (GPS?)")
                }
                if (attitude == null) {
                    debug("Attitude is NULL (IMU?)")
                }*/

                if (location == null || attitude == null) {
                    //debug("Telemetry not available -- location or attitude problems")
                } else {
                    mqttPublisher?.publishTelemetry(location, attitude)
                    /* for debug
                    val json = mqttPublisher.publishTelemetry(location, attitude)
                    debug("TX: $json")
                     */
                }
            } catch (e: Exception) {
                debug("Error telemetry: ${e.message}")
            }

            handler.postDelayed(this, 200) // 1000ms - 1 Hz / 200ms - 5Hz
        }
    }

    /**
     * Controller for virtual stick movements.
     * Handles the conversion of high-level commands (e.g., "forward") into
     * precise roll, pitch, yaw, and vertical throttle values sent to the drone.
     */
    private val vfc = VirtualFlightController(
        basicAircraftControlVM,
        virtualStickVM,
        simulatorVM,
        deadZone = 0.0005f,//mimum speed
        onDebug = { msg -> debug(msg) }
    )

    /**
     * waypoint navigation.
     * handles the insertion of latitude, longitude and altitude and it translate this data into
     * command (e.g., "forward") to move the drone to the waypoint addressed
     */
    private val wpn= WayPointNavigation(
        vfc=vfc,
        onDebug={msg: String -> debug(msg)}
    )

    /**
     * Starts a camera frame listener using DJI CameraStreamManager.
     *
     * Frames are monitored in YUV format. When [captureNextFrame] is toggled to true
     * by an MQTT command, the next available frame is intercepted, corrected for
     * color alignment (YUV Planar to NV21 or YUV420_888), compressed to JPEG, and published.
     */
    private fun startCameraFrameListener() {
        //debug("Camera Frame Listener initialized")
        cameraStreamManager.addFrameListener(
            cameraIndex,
            ICameraStreamManager.FrameFormat.YUV420_888
            //ICameraStreamManager.FrameFormat.NV21
        ) { data, width, height, _, _, _ -> //this is the frame who arrives

            // Only process if the "photo" command was recently received
            if (!captureNextFrame.get()) return@addFrameListener

            // Reset the flag immediately to capture a single shot
            captureNextFrame.set(false)

            if (data == null || data.isEmpty()) {
                debug("Capture Error: Received empty buffer")
                return@addFrameListener
            }

            var realWidth = width
            var realHeight = height

            if (realWidth <= 0 || realHeight <= 0) {
                when (data.size) {
                    1382400 -> { realWidth = 1280; realHeight = 720 }   // 720p
                    3110400 -> { realWidth = 1920; realHeight = 1080 }  // 1080p
                    else -> {
                        debug("Capture Error: Unknown buffer size (${data.size})")
                        return@addFrameListener
                    }
                }
            }

            // Perform compression and network transmission in a background thread
            // to prevent stuttering in the drone's video feed.
            Thread {
                try {
                    val jpeg = convertYuvToJpeg(data, realWidth, realHeight)
                    if (jpeg != null) {
                        mqttPublisher?.publishPhoto(jpeg)
                        debug("Photo sent-  Resolution: ${realWidth}x${realHeight}")
                    } else {
                        debug("Conversion Error: JPEG compression failed")
                    }
                } catch (e: Exception) {
                    debug("Processing Error: ${e.message}")
                }
            }.start()
        }
    }

    /**
     * Converts raw YUV data from the DJI SDK into a JPEG ByteArray.
     * This method specifically fixes the "purple/green" tint by reordering
     * Planar YUV420 pixels into the Interleaved NV21 format required by Android.
     */
    private fun convertYuvToJpeg(data: ByteArray, width: Int, height: Int): ByteArray? {
        return try {
            val out = ByteArrayOutputStream()
            val frameSize = width * height
            val expectedSize = frameSize * 3 / 2

            // Prepare a new buffer for the NV21 format
            val nv21 = ByteArray(expectedSize)

            //copy the Y (Luminance) plane - identical in both formats
            System.arraycopy(data, 0, nv21, 0, frameSize)

            // interleave the U and V (Chroma) planes
            // DJI sends Y-U-V (Planar). NV21 expects Y-VU-VU (Interleaved).
            val uPlane = frameSize
            val vPlane = frameSize + (frameSize / 4)

            for (i in 0 until (frameSize / 4)) {
                // NV21 pattern: V, then U
                nv21[frameSize + i * 2] = data[vPlane + i]
                nv21[frameSize + i * 2 + 1] = data[uPlane + i]
            }

            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                width,
                height,
                null
            )

            // Compress at 70% quality to balance clarity and MQTT message size
            if (yuvImage.compressToJpeg(Rect(0, 0, width, height), 70, out)) {
                out.toByteArray()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private val gimbalController = CameraGimbalController { msg -> debug(msg) }

    /**
     * Entry point for all incoming MQTT messages from the "drone/commands" topic.
     * * @param payload A JSON string containing:
     * - "action": (String) The command name (e.g., "forward", "gimbal", "photo").
     * - "duration": (Double, optional) Time in milliseconds for movement.
     * - "speed": (Double, optional) Normalized speed between 0.0 and 1.0.
     * - "pitch"/"yaw": (Double, optional) Target angles for gimbal control.
     *
     * Flight control:
     * - takeoff
     * - land
     * - stop
     * - forward / backward
     * - left / right
     * - up / down
     * - rotateleft / rotateright
     *
     * Camera:
     * - photo
     * - zoom
     *
     * Gimbal:
     * - gimbal (pitch, yaw)
     *
     * Diagnostics:
     * - ping
     * - p-photo
     */
    private fun handleRemoteCommand(payload: String) {//coming msg
        try {
            val json = JSONObject(payload)
            val action = json.optString("action").lowercase()

            val duration =  json.optDouble("duration", -1.0)
            val speed = json.optDouble("speed", -1.0)

            when (action) {
                "p-photo" ->{
                    val pcTimestamp = json.optLong("timestamp")
                    val droneReceivedTime = System.currentTimeMillis() // Start timing HERE
                    executeGalleryPingTest(pcTimestamp, droneReceivedTime)
                }
                "ping" -> {
                    val timestamp = json.optLong("timestamp")
                    sendPing(timestamp)
                }

                //---- DRONE MOVEMENT -----
                "enablevs" -> executeVSEnable()
                "disablevs" -> executeVSDisable()

                "takeoff" -> executeTakeoff()
                "land" -> executeLanding()
                "stop" -> {
                    debug("Remote Command: STOP")
                    vfc.stop()
                }

                "forward", "backwards", "right", "left" ->{
                    if (duration > 0 && speed > 0 && speed <= 1.0){
                        debug("Remote Command: $action (with ML)")
                        CoroutineScope(Dispatchers.Main).launch{
                            vfc.moveCor(
                                action=action,
                                targetTime = duration.toLong(),
                                power = speed.toFloat(),
                                predictor = movePredictor,
                                onDebug={msg ->debug(msg)
                                    Toast.makeText(context, "ML: $msg",Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }else{
                        debug("invalid movement parameters")
                    }
                }
                "rotateright","rotateleft" ->{
                    if (duration > 0 && speed > 0 && speed <= 1.0){
                        debug("Remote Command: $action (with ML)")
                        CoroutineScope(Dispatchers.Main).launch{
                            vfc.rotationCor(
                                action=action,
                                targetTime = duration.toLong(),
                                power = speed.toFloat(),
                                predictor = rotatePredictor,
                                onDebug={msg ->debug(msg)
                                    Toast.makeText(context, "ML: $msg",Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }else{
                        debug("invalid movement parameters")
                    }
                }
                /** Normal movement without the correction
                "forward" -> {
                    if (duration > 0 && speed > 0 && speed <= 1.0) {
                        debug("Remote Command: FORWARD")
                        vfc.forward(speed.toFloat())

                        handler.postDelayed({
                            vfc.stop()
                        }, duration.toLong())
                    } else {
                        debug("Invalid forward parameters")
                    }}
                "backwards" -> {
                    if (duration > 0 && speed > 0 && speed <= 1.0) {
                        debug("Remote Command: BACKWARD")
                        vfc.backward(speed.toFloat())

                        handler.postDelayed({
                            vfc.stop()
                        }, duration.toLong())
                    } else {
                        debug("Invalid forward parameters")
                    }}
                "right" -> {
                    if (duration > 0 && speed > 0 && speed <= 1.0) {
                        debug("Remote Command: ROTATE RIGHT")
                        vfc.right(speed.toFloat())

                        handler.postDelayed({
                            vfc.stop()
                        }, duration.toLong())
                    } else {
                        debug("Invalid forward parameters")
                    }}
                "left" -> {
                    if (duration > 0 && speed > 0 && speed <= 1.0) {
                        debug("Remote Command: ROTATE LEFT")
                        vfc.left(speed.toFloat())

                        handler.postDelayed({
                            vfc.stop()
                        }, duration.toLong())
                    } else {
                        debug("Invalid forward parameters")
                    }}
                "rotateright" -> {
                    if (duration > 0 && speed > 0 && speed <= 1.0) {
                        debug("Remote Command: ROTATE RIGHT")
                        vfc.rotateRight(speed.toFloat())

                        handler.postDelayed({
                            vfc.stop()
                        }, duration.toLong())
                    } else {
                        debug("Invalid forward parameters")
                    }}
                "rotateleft" -> {
                    if (duration > 0 && speed > 0 && speed <= 1.0) {
                        debug("Remote Command: ROTATE LEFT")
                        vfc.rotateLeft(speed.toFloat())

                        handler.postDelayed({
                            vfc.stop()
                        }, duration.toLong())
                    } else {
                        debug("Invalid forward parameters")
                    }}
                */


                "up" -> {
                    if (duration > 0 && speed > 0 && speed <= 1.0) {
                        debug("Remote Command: UP")
                        vfc.up(speed.toFloat())

                        handler.postDelayed({
                            vfc.stop()
                        }, duration.toLong())
                    } else {
                        debug("Invalid forward parameters")
                    }}
                "down" -> {
                    if (duration > 0 && speed > 0 && speed <= 1.0) {
                        debug("Remote Command: DOWN")
                        vfc.down(speed.toFloat())
                        handler.postDelayed({
                            vfc.stop()
                        }, duration.toLong())
                    } else {
                        debug("Invalid forward parameters")
                    }}
                "orbit" -> {
                    if (duration > 0) {
                        debug("Remote Command: Start Orbit")
                        vfc.right(0.2f) // moving lateral
                        vfc.rotateLeft(0.15f) // opposite direction to lock front
                        handler.postDelayed({
                            vfc.stop()
                            debug("Movement completed")
                        }, duration.toLong())
                    }
                }
                // WAYPOINT NAVIGATION
                "goto" -> {
                    val lat = json.optDouble("lat")
                    val lon = json.optDouble("lon")
                    val alt = json.optDouble("alt")
                    debug("Remote Command: GOTO $lat $lon $alt")
                    wpn.gotogps(lat, lon, alt)

                }
                //---- CAMERA GIMBAL -----
                "gimbal" -> {
                    val pitch = json.optDouble("pitch", 0.0)
                    val yaw = json.optDouble("yaw", 0.0)
                    debug("Remote Command: GIMBAL P:$pitch Y:$yaw")
                    gimbalController.rotateTo(
                        pitch = pitch,
                        yaw = yaw
                    )
                }
                "zoom" -> {
                    val zoom = json.optDouble("value", 1.0)
                    debug("Remote Command: ZOOM $zoom")
                    gimbalController.setZoom(zoom)
                }

                "photo" -> executeTakePhoto()

                else -> debug("Unknown action: $action")
            }
        } catch (e: Exception) {
            debug("Error parsing command: ${e.message}")
        }
    }

    /**
     * Sends a ping response back to the PC to measure communication latency.
     *
     * The response includes:
     * - original PC timestamp
     * - time when the drone received the message
     */
    private fun sendPing(originalTimestamp: Long) {
        val response = JSONObject().apply {
            put("action", "ping")
            put("timestamp", originalTimestamp)
            put("drone_received_at", System.currentTimeMillis())
        }
        mqttPublisher?.publish("drone/ping", response.toString())
    }

    /**
     * Execute takeoff + Enable Virtual Stick
     */
    private fun executeTakeoff() {
        debug("Remote Command: TAKEOFF")
        vfc.takeOff(
            onOk = { debug("Takeoff successful") },
            onErr = { debug("Takeoff Failed: ${it.description()}") }
        )

    }

    /**
     * Execute landing
     */
    private fun executeLanding() {
        debug("Remote Command: LAND")
        vfc.land(
            onOk = { debug("Landing successful") },
            onErr = { debug("Landing Failed: ${it.description()}") }
        )
    }

    /**
     * Enable Virtual Stick
     */
    private fun executeVSEnable(){
        debug("Remote Command: ENABLE VS")
        virtualStickVM.enableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                debug("VS enabled successfully")
            }
            override fun onFailure(error: IDJIError) {
                debug("Failed to enable VS")
            }
        })
    }

    /**
     * Disable Virtual Stick
     */
    private fun executeVSDisable(){
        debug("Remote Command: DISABLE VS")
        virtualStickVM.disableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                debug("VS disabled successfully")
            }
            override fun onFailure(error: IDJIError) {
                debug("Failed to disable")
            }
        })
    }

    /**
     * Triggers a high-speed "Live Stream" photo capture.
     *
     * Instead of switching the camera to a dedicated photo mode (which is slow
     * and interrupts the video feed), this method sets [captureNextFrame] to true.
     *
     * The [startCameraFrameListener] will then intercept the very next raw YUV
     * frame from the live video stream, convert it to JPEG, and send it via MQTT.
     */
    private fun executeTakePhoto() {
        debug("Remote Command: PHOTO")
        captureNextFrame.set(true)
    }

    // should be updated without the drone location but calculate the execute photo time (removing the gallery access)
    /**
     * Executes an end-to-end latency and data throughput test.
     * * This test:
     * 1. Retrieves the most recent high-resolution photo from the drone's local gallery.
     * 2. Resizes the image to 720p to manage MQTT payload size.
     * 3. Calculates processing time on the drone.
     * 4. Bundles the image, GPS coordinates, and timing data into a JSON response
     * sent to "drone/ping_test".
     */
    private fun executeGalleryPingTest(pcTimestamp: Long, droneReceivedTime: Long) {

        val photoBytes = getLastPhotoFromGallery() ?: return

        val takeLoc= getLocation() ?: return

        // Resize and compress
        val originalBitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
        val targetWidth = 1280
        val targetHeight = (originalBitmap.height.toFloat() / originalBitmap.width.toFloat() * targetWidth).toInt()
        val resized = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)

        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 85, out)
        val finalJpeg = out.toByteArray()

        val droneTotalTime = System.currentTimeMillis() - droneReceivedTime

        val payload = JSONObject().apply {
            put("action", "ping_response")
            put("pc_timestamp", pcTimestamp) //send back the PC's original time
            put("drone_proc_ms", droneTotalTime) //how long the drone worked since the request arrived
            put("drone_location", takeLoc)//location to know the time between remote and dorne
            put("photo_base64", Base64.encodeToString(finalJpeg, Base64.NO_WRAP))
        }

        mqttPublisher?.publish("drone/ping_test", payload.toString())
    }

    private fun getLastPhotoFromGallery(): ByteArray? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val path = it.getString(0)
                return File(path).readBytes()
            }
        }
        return null
    }
    private fun getLocation(): LocationCoordinate3D? {
        val locationKey = KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D)

        val location = KeyManager.getInstance().getValue(locationKey)

        return location
    }

    private fun setFlightLimit(){
        KeyManager.getInstance().setValue(KeyTools.createKey(FlightControllerKey.KeyMaxRadiusCanFlyAndGoHome),10.0, null)
        KeyManager.getInstance().setValue(KeyTools.createKey(FlightControllerKey.KeyLimitMaxFlightHeightInMeter), 2, null)
    }

    /**
     * Returns whether the telemetry test system is currently active.
     */
    fun isRunning(): Boolean = running


    private fun debug(msg: String) {
        onDebug(msg)
    }

    /**
     * ML predictors
     * Upload JSON file from 'assets' directory
     */

    private val movePredictor by lazy{
        val params = ModelLoader.LoadFromAssets(context, "move_model.json")
        DroneCorrector(params)
    }

    private val rotatePredictor by lazy{
        val params = ModelLoader.LoadFromAssets(context, "rotate_model.json")
        DroneCorrector(params)
    }

}