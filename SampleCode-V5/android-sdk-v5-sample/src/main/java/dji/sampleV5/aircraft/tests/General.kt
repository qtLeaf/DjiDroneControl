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
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import java.util.concurrent.atomic.AtomicBoolean

//mosquitto -c ~/mosquitto.conf
//nano ~/mosquitto.conf
//  listener 1883 0.0.0.0
//  allow_anonymous true

class General(
    private val basicAircraftControlVM: BasicAircraftControlVM,
    private val virtualStickVM: VirtualStickVM,
    private val simulatorVM: SimulatorVM,
    private val context: Context,
    private val onDebug: (String) -> Unit
) {

    private val mqttPublisher = MqttPublisher(
        brokerIp = MqttConfig.HOST,
        brokerPort = MqttConfig.PORT
    )


    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val cameraIndex = ComponentIndexType.LEFT_OR_MAIN

    private val captureNextFrame = AtomicBoolean(false)

    private val cameraStreamManager: ICameraStreamManager by lazy {
        MediaDataCenter.getInstance().cameraStreamManager
    }

    fun startTelemetryTest() {
        if (running) {
            debug("Test already started")
            return
        }

        debug("Connection MQTT to ${MqttConfig.HOST} : ${MqttConfig.PORT}")

        try {
            mqttPublisher.connect()
            running = true

            mqttSubscriber = MqttSubscriber(
                brokerIp = MqttConfig.HOST,
                brokerPort = MqttConfig.PORT,
                onCommand = { payload ->
                    // activates for each msg arrive
                    handleRemoteCommand(payload)
                },
                onDebug = { msg -> debug(msg) }
            )
            mqttSubscriber?.connect()

            //initMediaManager(cameraIndex)
            telemetryTask.run()
            startCameraFrameListener()
            //photoTask.run()
            //virtualStickTest.run()
            debug("Test started")

        } catch (e: Exception) {
            debug("Error MQTT: ${e.message}")
        }
    }

    fun stopTelemetryTest() {
        if (!running) return

        running = false
        handler.removeCallbacks(telemetryTask)
        mqttPublisher.disconnect()
        debug("Test stopped")
    }

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
                    mqttPublisher.publishTelemetry(location, attitude)
                    /*val json = mqttPublisher.publishTelemetry(location, attitude)
                    debug("TX: $json")
                     */
                }

            } catch (e: Exception) {
                debug("Error telemetry: ${e.message}")
            }

            handler.postDelayed(this, 200) // 1000ms - 1 Hz / 200ms - 5Hz
        }
    }

    private val vfc = VirtualFlightController(
        basicAircraftControlVM,
        virtualStickVM,
        simulatorVM
    )
    private val virtualStickTest = object : Runnable {
        override fun run() {
            if (!running) return

            debug("Requesting Virtual Stick")

            // make sure there are no residues from previous sessions
            virtualStickVM.disableVirtualStick(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    enableVSStep()
                }
                override fun onFailure(error: IDJIError) {
                    enableVSStep() // proceeds anyway
                }
            })
        }

        private fun enableVSStep() {
            virtualStickVM.enableVirtualStick(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    debug("Enable command sent. Verifying state...")
                    // need 1-2 sec to update the state
                    handler.postDelayed({ checkStateAndStart() }, 1500)
                }

                override fun onFailure(error: IDJIError) {
                    debug("Failed to enable VS: ${error.description()}")
                }
            })
        }

        private var retryCount = 0
        private fun checkStateAndStart() {
            val state = virtualStickVM.currentVirtualStickStateInfo.value?.state

            if (state?.isVirtualStickEnable == true) {
                debug("VS ENABLED Starting flight sequence.")
                retryCount = 0
                startFlightSequence()
            } else {
                if (retryCount < 10) { // synchronize the data
                    retryCount++
                    debug("Waiting for GPS/Firmware (Attempt $retryCount/10)")
                    handler.postDelayed({ checkStateAndStart() }, 1000)
                } else {
                    debug("CRITICAL: Cannot enable VS. Check GPS signal -- other problem.")
                    stopTelemetryTest()
                }
            }
        }

        private fun startFlightSequence() {
            debug("Initiating Automatic Takeoff...")

            // takeOff with safety check
            vfc.takeOff(
                onOk = {
                    debug("Takeoff successful. Waiting to reach safe altitude...")
                    // wait a 6-8 sec for hovering (1.2m)
                    //handler.postDelayed({ moveForward() }, 8000)
                    handler.postDelayed({ land() }, 8000)
                    //handler.postDelayed({ orbitStep() }, 8000)

                },
                onErr = {
                    debug("takeoff FAILED: ${it.description()}")
                    stopTelemetryTest()
                }
            )
        }

        private fun moveForward() {
            if (!running) return
            debug("Moving forward")

            vfc.setSpeed(0.1) // 10% of max speed
            vfc.forward(0.1f)

            handler.postDelayed({
                vfc.stop()
                debug("Stopping")
                handler.postDelayed({ land() }, 2000)
            }, 1000) // move for 1 sec
        }

        private fun orbitStep() {
            if (!running) return
            debug("Start Orbit")

            // low speed
            vfc.setSpeed(0.1)

            vfc.right(0.2f) // moving lateral
            vfc.rotateLeft(0.15f) // opposite direction to lock front

            handler.postDelayed({
                vfc.stop()
                debug("Movement completed. Stableizing")
                handler.postDelayed({ land() }, 2000)
            }, 5000)
        }

        private fun land() {
            if (!running) return
            debug("Executing Landing via VFC...")

            vfc.land(
                onOk = {
                    debug("Landing initiated successfully")
                    stopTelemetryTest()
                },
                onErr = { error ->
                    debug("Landing error: ${error.description()}. EMERGENCY: Forcing descent.")
                    //in case of fail -> force descent
                    vfc.down(0.5f)
                }
            )
        }
    }

    private fun startCameraFrameListener() {
        cameraStreamManager.addFrameListener(
            cameraIndex,
            ICameraStreamManager.FrameFormat.YUV420_888
        ) { data, width, height, offset, length, format ->

            if (width <= 0 || height <= 0 || data.isEmpty()) {
                return@addFrameListener
            }

            if (!captureNextFrame.get()) return@addFrameListener

            captureNextFrame.set(false)

            try {
                val jpeg = convertFrameToJpeg(data, width, height)
                mqttPublisher.publishPhoto(jpeg)
                debug("Photo sent to MQTT (${width}x${height})")
            } catch (e: Exception) {
                debug("Frame capture error: ${e.message}")
            }
        }
    }

    private fun convertFrameToJpeg(
        data: ByteArray,
        width: Int,
        height: Int
    ): ByteArray {

        val yuv = YuvImage(
            data,
            ImageFormat.NV21,
            width,
            height,
            null
        )

        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(
            Rect(0, 0, width, height),
            60,
            out
        )

        return out.toByteArray()
    }



    /*
    private fun prepareCameraAndShoot() {
        val cameraModeKey = CameraKey.KeyCameraMode.create(cameraIndex)

        // Assicuriamoci di essere in modalità PHOTO
        KeyManager.getInstance().setValue(cameraModeKey, CameraMode.PHOTO_NORMAL, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                debug("Camera mode set to PHOTO. Waiting 1s to stabilize...")
                // Diamo tempo al sistema di stabilizzare la modalità prima di scattare
                handler.postDelayed({
                    shootPhoto()
                }, 1000)
            }

            override fun onFailure(error: IDJIError) {
                debug("Failed to set camera mode: ${error.description()}")
            }
        })
    }

    private fun shootPhoto() {
        val shootPhotoKey = CameraKey.KeyStartShootPhoto.create(cameraIndex)

        debug("Inviando comando StartShootPhoto...")
        KeyManager.getInstance().performAction(shootPhotoKey, object : CompletionCallbackWithParam<EmptyMsg> {
            override fun onSuccess(p0: EmptyMsg?) {
                debug("Photo taken successfully!")
                handler.postDelayed({ listenForNewPhoto() }, 2000)
            }

            override fun onFailure(error: IDJIError) {
                // Se l'errore è null, proviamo a capire se è un problema di storage
                val errorMsg = error.description() ?: "Unknown Error (null)"
                debug("Shoot photo failed: $errorMsg")

                if (errorMsg == "null") {
                    debug("Suggerimento: Verifica che la MicroSD sia inserita o che lo storage interno non sia pieno.")
                }
            }
        })
    }

    private fun listenForNewPhoto() {
        val mediaManager = MediaDataCenter.getInstance().mediaManager

        // Parametri per recuperare la lista file (prendiamo gli ultimi 5 per sicurezza)
        val pullParam = PullMediaFileListParam.Builder()
            .count(5)
            .mediaFileIndex(0)
            .build()


        mediaManager.pullMediaFileListFromCamera( pullParam, object : CommonCallbacks.CompletionCallback {

            override fun onSuccess() {
                debug("Pull success. Fetching files...")

                val mediaFiles = mediaManager.mediaFileListData.data

                val lastFile = mediaFiles?.firstOrNull()

                if (lastFile != null) {
                    debug("File found: ${lastFile.fileName}")
                    downloadMediaFile(lastFile)
                } else {
                    debug("List is empty after pull")
                }
            }

            override fun onFailure(error: IDJIError) {
                debug("Failed to pull media list: ${error.description()}")
            }
        })
    }

    private fun downloadMediaFile(mediaFile: MediaFile) {
        // Definiamo dove salvare il file nello smartphone
        val destFile = File(context.cacheDir, mediaFile.fileName)

        mediaFile.pullOriginalMediaFileFromCamera(0L, object : MediaFileDownloadListener {
            override fun onStart() {
                debug("Download started: ${mediaFile.fileName}")
            }

            override fun onProgress(total: Long, current: Long) {
            }

            override fun onRealtimeDataUpdate(data: ByteArray?, position: Long) {
            }

            override fun onFinish() {
                debug("File downloaded: ${destFile.absolutePath}")
                // 3. QUI PUOI INVIARE IL FILE (es. via MQTT come Base64 o caricamento HTTP)
                debug("Download completato: ${destFile.absolutePath}")
                sendPhotoToMqtt(destFile)
            }

            override fun onFailure(error: IDJIError) {
                debug("Download failed: ${error.description()}")
            }
        })
    }

    private fun sendPhotoToMqtt(file: File) {
        // Esempio: Leggi il file e invialo (Attenzione: MQTT non è ideale per file grandi, meglio Base64 o upload URL)
        val bytes = file.readBytes()
        debug("Photo ready to be sent. Size: ${bytes.size} bytes")
        // Implementa qui la tua logica di pubblicazione (es. mqttPublisher.publishPhoto(bytes))
    }
    */

    private var mqttSubscriber: MqttSubscriber? = null

    private fun handleRemoteCommand(payload: String) {//coming msgs
        try {
            val json = JSONObject(payload)
            val action = json.optString("action").lowercase()

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
                "takeoff" -> executeTakeoff()
                "land" -> executeLanding()
                "photo" -> executeTakePhoto()

                else -> debug("Unknown action: $action")
            }
        } catch (e: Exception) {
            debug("Error parsing command: ${e.message}")
        }
    }

    private fun sendPing(originalTimestamp: Long) {
        val response = JSONObject().apply {
            put("action", "ping")
            put("timestamp", originalTimestamp)
            put("drone_received_at", System.currentTimeMillis())
        }
        mqttPublisher.publish("drone/ping", response.toString())
    }

    private fun executeTakeoff() {
        debug("Remote Command: TAKEOFF")
        //virtualStickTest.run()
        /*vfc.takeOff(
            onOk = { debug("Takeoff Successful") },
            onErr = { debug("Takeoff Failed: ${it.description()}") }
        )
        */
    }

    private fun executeLanding() {
        debug("Remote Command: LAND")
        vfc.land(
            onOk = { debug("Landing Successful") },
            onErr = { debug("Landing Failed: ${it.description()}") }
        )
    }

    private fun executeTakePhoto() {
        debug("Remote Command: PHOTO")
        captureNextFrame.set(true)
    }

    // Update the function to accept the PC's initial timestamp
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

        mqttPublisher.publish("drone/ping_test", payload.toString())
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

    fun isRunning(): Boolean = running


    private fun debug(msg: String) {
        onDebug(msg)
    }

}
