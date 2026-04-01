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


/**
 * Handles outgoing MQTT communication from the drone to the remote broker.
 * This class is responsible for serializing telemetry data and camera frames
 * into JSON format and publishing them to specific topics.
 *
 * @property brokerIp The IP address of the MQTT broker (defaults to [MqttConfig.HOST]).
 * @property brokerPort The port of the MQTT broker (defaults to [MqttConfig.PORT]).
 */
class MqttPublisher(
    brokerIp: String = MqttConfig.HOST,
    brokerPort: Int = MqttConfig.PORT
) {

    private val brokerUrl = "tcp://$brokerIp:$brokerPort"
    private val clientId = MqttClient.generateClientId()
    private val client = MqttClient(brokerUrl, clientId, null)

    /**
     * Establishes a connection to the MQTT broker with a 5-second timeout.
     */
    fun connect() {
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 5
        }
        client.connect(options)
    }

    /**
     * Safely disconnects the client from the broker if currently connected.
     */
    fun disconnect() {
        if (client.isConnected) {
            client.disconnect()
        }
    }

    // ---------- TELEMETRY ----------

    /**
     * Publishes aircraft status data to the "drone/telemetry" topic.
     * @param location The 3D coordinates (Lat, Lon, Alt) from the Flight Controller.
     * @param attitude The aircraft orientation (Pitch, Roll, Yaw).
     * @return The JSON string that was published.
     */
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

    /**
     * Encodes a JPEG frame to Base64 and publishes it to the "drone/photo" topic.
     * @param bytes The raw JPEG data.
     * @param filename A descriptive name for the frame.
     */
    fun publishPhoto(bytes: ByteArray, filename: String = "frame.jpg") {
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val json = JSONObject().apply {
            put("filename", filename)
            put("data", encoded)
        }

        publish("drone/photo", json.toString())
    }


    // ---------- INTERNAL ----------

    /**
     * Generic publish method using QoS 1 (at least once delivery).
     * @param topic The target MQTT topic.
     * @param payload The string content to send.
     */
    fun publish(topic: String, payload: String) {
        if (!client.isConnected) return

        val message = MqttMessage(payload.toByteArray()).apply {
            qos = 1
            isRetained = false
        }
        client.publish(topic, message)
    }
}

/**
 * Listens for incoming commands from the remote PC via MQTT.
 * * This class operates asynchronously and triggers the [onCommand] callback
 * whenever a message is received on the "drone/commands" topic.
 *
 * @param onCommand Callback function to handle the received JSON payload.
 * @param onDebug Callback function for logging connection status and debug info.
 */
class MqttSubscriber(
    brokerIp: String = MqttConfig.HOST,
    brokerPort: Int = MqttConfig.PORT,
    private val onCommand: (String) -> Unit,
    private val onDebug: (String) -> Unit
) {
    private val brokerUrl = "tcp://$brokerIp:$brokerPort"
    private val clientId = MqttClient.generateClientId() + "_sub"
    private val client = MqttClient(brokerUrl, clientId, null)

    /**
    * Connects to the broker and subscribes to the command topic.
    * Sets up the [MqttCallback] to handle incoming messages and connection loss.
    */
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

    /**
     * Terminates the subscriber session.
     */
    fun disconnect() {
        if (client.isConnected) client.disconnect()
    }
}