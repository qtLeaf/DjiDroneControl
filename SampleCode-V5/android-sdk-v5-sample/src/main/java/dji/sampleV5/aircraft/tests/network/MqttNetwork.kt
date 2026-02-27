package dji.sampleV5.aircraft.tests.network

import android.content.Context
import dji.sdk.keyvalue.value.common.Attitude
import dji.sdk.keyvalue.value.common.LocationCoordinate3D

import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage

import org.json.JSONObject
import android.util.Base64
import dji.sampleV5.aircraft.tests.config.MqttConfig
import org.eclipse.paho.client.mqttv3.MqttCallback
import java.io.File

class MqttPublisher(
    brokerIp: String = MqttConfig.HOST,
    brokerPort: Int = MqttConfig.PORT
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

    fun publishPhoto(bytes: ByteArray, filename: String = "frame.jpg") {
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val json = JSONObject().apply {
            put("filename", filename)
            put("data", encoded)
        }

        publish("drone/photo", json.toString())
    }


    // ---------- INTERNAL ----------

    fun publish(topic: String, payload: String) {
        if (!client.isConnected) return

        val message = MqttMessage(payload.toByteArray()).apply {
            qos = 1
            isRetained = false
        }
        client.publish(topic, message)
    }
}

class MqttSubscriber(
    brokerIp: String = MqttConfig.HOST,
    brokerPort: Int = MqttConfig.PORT,
    private val onCommand: (String) -> Unit,
    private val onDebug: (String) -> Unit
) {
    private val brokerUrl = "tcp://$brokerIp:$brokerPort"
    private val clientId = MqttClient.generateClientId() + "_sub"
    private val client = MqttClient(brokerUrl, clientId, null)

    fun connect() {
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 5
            keepAliveInterval = 60
        }

        client.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                onDebug("Subscriber connection lost: ${cause?.message}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.toString() ?: ""
                onDebug("RX Topic: $topic | Msg: $payload")
                onCommand(payload)
            }

            override fun deliveryComplete(token: org.eclipse.paho.client.mqttv3.IMqttDeliveryToken?) {}
        })

        client.connect(options)
        client.subscribe("drone/commands", 1)
        onDebug("Subscribed to drone/commands")
    }

    fun disconnect() {
        if (client.isConnected) client.disconnect()
    }
}