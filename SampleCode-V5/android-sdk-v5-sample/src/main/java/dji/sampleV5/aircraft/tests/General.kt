package dji.sampleV5.aircraft.tests

import android.os.Handler
import android.os.Looper
import dji.sampleV5.aircraft.models.BasicAircraftControlVM
import dji.sampleV5.aircraft.models.SimulatorVM
import dji.sampleV5.aircraft.models.VirtualStickVM
import dji.sampleV5.aircraft.tests.network.MqttPublisher
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools

import dji.v5.manager.KeyManager


//mosquitto -c ~/mosquitto.conf
//nano ~/mosquitto.conf
//listener 1883 0.0.0.0
//allow_anonymous true

class General(
    private val basicAircraftControlVM: BasicAircraftControlVM,
    private val virtualStickVM: VirtualStickVM,
    private val simulatorVM: SimulatorVM,
    private val mqttHost: String,
    private val mqttPort: Int,
    private val onDebug: (String) -> Unit
) {

    private val mqttPublisher = MqttPublisher(
        brokerIp = mqttHost,
        brokerPort = mqttPort
    )

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    fun startTelemetryTest() {
        if (running) {
            debug("Test already started")
            return
        }

        debug("Connection MQTT to $mqttHost:$mqttPort")

        try {
            mqttPublisher.connect()
            running = true
            telemetryTask.run()
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
                    debug("Location is NULL (No GPS fix?)")
                }
                if (attitude == null) {
                    debug("Attitude is NULL (IMU not ready?)")
                }*/

                if (location == null || attitude == null) {
                    debug("Telemetry not available")
                } else {
                    val json = mqttPublisher.publishTelemetry(location, attitude)
                    debug("TX: $json")
                }

            } catch (e: Exception) {
                debug("Error telemetry: ${e.message}")
            }

            handler.postDelayed(this, 200) // 1000ms - 1 Hz / 200ms - 5Hz
        }
    }

    fun isRunning(): Boolean = running


    private fun debug(msg: String) {
        onDebug(msg)
    }

}
