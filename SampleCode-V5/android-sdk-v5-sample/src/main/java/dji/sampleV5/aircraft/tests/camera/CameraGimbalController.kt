package dji.sampleV5.aircraft.tests.camera

import dji.v5.ux.gimbal.GimbalFineTuneWidgetModel
import dji.sdk.keyvalue.value.gimbal.PostureFineTuneAxis

class CameraGimbalController {

    private val model = GimbalFineTuneWidgetModel(
        dji.v5.ux.core.base.DJISDKModel.getInstance(),
        dji.v5.ux.core.communication.ObservableInMemoryKeyedStore.getInstance()
    )

    init {
        model.setup()
    }

    fun tiltPitch(amount: Double) {
        model.fineTunePosture(PostureFineTuneAxis.PITCH_AXIS, amount).subscribe()
    }

    fun panYaw(amount: Double) {
        model.fineTunePosture(PostureFineTuneAxis.YAW_AXIS, amount).subscribe()
    }

    fun moveRoll(amount: Double) {
        model.fineTunePosture(PostureFineTuneAxis.ROLL_AXIS, amount).subscribe()
    }

    fun cleanup() {
        model.cleanup()
    }
}
