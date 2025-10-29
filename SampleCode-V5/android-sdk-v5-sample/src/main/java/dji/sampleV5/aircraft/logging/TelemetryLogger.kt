package dji.sampleV5.aircraft.logging

import android.content.Context
import android.util.Log
import dji.v5.manager.KeyManager
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.BatteryKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.Attitude
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.sdk.keyvalue.value.flightcontroller.GPSSignalLevel
import dji.sdk.keyvalue.value.flightcontroller.IMUState
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class TelemetryLogger(private val context: Context) {

    private val tag = "TelemetryLogger"
    private var writer: BufferedWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // --- DJI Keys ---
    private val keyAttitude = KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude)
    private val keyGPS = KeyTools.createKey(FlightControllerKey.KeyGPSSignalLevel)
    private val keyIMU = KeyTools.createKey(FlightControllerKey.KeyIMUCalibrationInfo)
    private val keyBattery = KeyTools.createKey(BatteryKey.KeyChargeRemainingInPercent)
    private val keyLocation = KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D)

    private val keyManager = KeyManager.getInstance()

    fun startLogging() {
        val file = File(context.getExternalFilesDir(null), "telemetry_log.txt")
        writer = BufferedWriter(FileWriter(file, true))
        Log.i(tag, "Logging telemetry to: ${file.absolutePath}")

        // --- Attitude listener ---
        keyManager.listen(keyAttitude, this) { _, newValue ->
            if (newValue is Attitude) {
                record("Attitude: roll=${newValue.roll}, pitch=${newValue.pitch}, yaw=${newValue.yaw}")
            }
        }

        // --- GPS signal listener ---
        keyManager.listen(keyGPS, this) { _, newValue ->
            if (newValue is GPSSignalLevel) {
                record("GPS Signal: ${newValue.name}")
            }
        }

        // --- IMU listener ---
        keyManager.listen(keyIMU, this) { _, newValue ->
            if (newValue is IMUState) {
                record(
                    "IMU: gyro=${newValue.gyroscopeState}, acc=${newValue.accelerometerState}, " +
                            "compass=${newValue.compassSensorValue}"
                )
            }
        }

        // --- Battery listener ---
        keyManager.listen(keyBattery, this) { _, newValue ->
            if (newValue is Int) {
                record("Battery: $newValue%")
            }
        }

        // --- Location listener ---
        keyManager.listen(keyLocation, this) { _, newValue ->
            if (newValue is LocationCoordinate3D) {
                record("Location: lat=${newValue.latitude}, lon=${newValue.longitude}, alt=${newValue.altitude}")
            }
        }
    }

    private fun record(data: String) {
        try {
            val timestamp = dateFormat.format(Date())
            writer?.apply {
                write("$timestamp | $data\n")
                flush()
            }
        } catch (e: IOException) {
            Log.e(tag, "Error writing telemetry: ${e.message}")
        }
    }

    fun stopLogging() {
        try {
            writer?.close()
        } catch (e: IOException) {
            Log.e(tag, "Error closing telemetry log: ${e.message}")
        }
        keyManager.cancelListen(this)
    }
}
