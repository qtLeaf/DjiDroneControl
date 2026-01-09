package dji.sampleV5.aircraft.tests

import dji.sampleV5.aircraft.tests.control.VirtualFlightController
import dji.sampleV5.aircraft.tests.network.MqttPublisher

import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.sdk.keyvalue.value.common.Attitude

import dji.sampleV5.aircraft.models.BasicAircraftControlVM
import dji.sampleV5.aircraft.models.SimulatorVM
import dji.sampleV5.aircraft.models.VirtualStickVM

import android.os.Handler
import android.os.Looper

import java.util.Locale

class General (
    private val basicAircraftControlVM: BasicAircraftControlVM,
    private val virtualStickVM: VirtualStickVM,
    private val simulatorVM: SimulatorVM
){
    private val mqttPublisher = MqttPublisher(
        brokerIp = "192.168.1.100",
        brokerPort = 1883
    )
    private val controller= VirtualFlightController(
        basicAircraftControlVM,
        virtualStickVM,
        simulatorVM
    )

    private var running = false

    fun startTest() {
        if (running) return
        running = true

        Thread {
            mqttPublisher.connect()
            controller.takeOff()

            Handler(Looper.getMainLooper()).postDelayed({
                controller.land()
                running = false
            }, 2000)
        }.start()
    }



}
