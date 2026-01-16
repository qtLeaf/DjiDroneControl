package dji.sampleV5.aircraft.tests.network

import dji.sdk.keyvalue.value.common.Attitude
import dji.v5.manager.KeyManager
import dji.sdk.keyvalue.value.flightcontroller.GPSSignalLevel
import dji.sdk.keyvalue.value.common.LocationCoordinate3D

import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage

import org.json.JSONObject
import android.util.Base64
import java.io.File

class MqttPublisher(
    brokerIp: String = "192.168.1.3",
    brokerPort: Int = 1883
) {

    private val brokerUrl = "tcp://$brokerIp:$brokerPort"
    private val clientId = MqttClient.generateClientId()
    private val client = MqttClient(brokerUrl, clientId, null)

    fun connect() {
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 5
        }
        client.connect(options)
    }

    fun disconnect() {
        if (client.isConnected) {
            client.disconnect()
        }
    }

    // ---------- TELEMETRY ----------

    fun publishTelemetry(
        location: LocationCoordinate3D,
        attitude: Attitude
    ): String {
        val json = JSONObject().apply {
            put("lat", location.getLatitude())
            put("lon", location.getLongitude())
            put("altitude", location.getAltitude())
            put("yaw", attitude.getYaw())
            put("pitch", attitude.getPitch())
            put("roll", attitude.getRoll())
            put("timestamp", System.currentTimeMillis())
        }

        publish("drone/telemetry", json.toString())

        return json.toString()
    }

    // ---------- PHOTO ----------

    fun publishPhoto(file: File) {
        val bytes = file.readBytes()
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val json = JSONObject().apply {
            put("filename", file.name)
            put("data", encoded)
        }

        publish("drone/photo", json.toString())
    }

    // ---------- INTERNAL ----------

    private fun publish(topic: String, payload: String) {
        if (!client.isConnected) return

        val message = MqttMessage(payload.toByteArray()).apply {
            qos = 1
            isRetained = false
        }
        client.publish(topic, message)
    }
}
