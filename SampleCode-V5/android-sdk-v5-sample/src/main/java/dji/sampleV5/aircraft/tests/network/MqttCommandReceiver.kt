package dji.sampleV5.aircraft.tests.network

import android.content.Context
import dji.sampleV5.aircraft.tests.General
import dji.sampleV5.aircraft.tests.config.MqttConfig
import dji.sampleV5.aircraft.tests.control.VirtualFlightController
import org.eclipse.paho.client.mqttv3.MqttClient
import org.json.JSONObject
/*
class MqttCommandReceiver(
    context: Context,
    private val general: General,
    private val controller: VirtualFlightController,
    private val onDebug: (String) -> Unit
) {

    private val subscriber = MqttSubscriber(
        context = context,
        onCommand = { json -> handle(json) },
        onDebug = onDebug
    )

    fun start() {
        subscriber.connect()
    }

    private fun handle(json: String) {
        val obj = JSONObject(json)
        when (obj.getString("cmd")) {

            "takeoff" -> controller.takeOff()

            "land" -> controller.land()

            "stick" -> controller.sendVirtualStick(
                roll = obj.getDouble("roll").toFloat(),
                pitch = obj.getDouble("pitch").toFloat(),
                yaw = obj.getDouble("yaw").toFloat(),
                throttle = obj.getDouble("throttle").toFloat()
            )

            "photo" -> {
                onDebug("Comando foto ricevuto")
                takePhotoAndSend()
            }
        }
    }

    private fun takePhotoAndSend() {
        cameraVM.takePhoto { file ->
            general.sendPhoto(file.readBytes())
        }
    }
}*/
