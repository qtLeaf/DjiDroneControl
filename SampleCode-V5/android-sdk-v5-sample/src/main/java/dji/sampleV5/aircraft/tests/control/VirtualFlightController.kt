package dji.sampleV5.aircraft.tests.control

import dji.sampleV5.aircraft.models.BasicAircraftControlVM
import dji.sampleV5.aircraft.models.SimulatorVM
import dji.sampleV5.aircraft.models.VirtualStickVM
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.virtualstick.Stick
import kotlin.math.abs

class VirtualFlightController(
    // injected from outside
    private val basicAircraftControlVM: BasicAircraftControlVM,
    private val virtualStickVM: VirtualStickVM,
    private val simulatorVM: SimulatorVM,

) {

    private val deviation: Double = 0.02
    private val max = Stick.MAX_STICK_POSITION_ABS

    // ----- Speed -----

    fun setSpeed(level: Double) {
        var safe = level.coerceIn(0.1, 1.0)
        virtualStickVM.setSpeedLevel(safe)
    }

    // ----- Movement -----

    fun forward(power: Float = 0.5f) {
        virtualStickVM.setRightPosition(0, normalize(power))
    }

    fun backward(power: Float = 0.5f) {
        virtualStickVM.setRightPosition(0, -normalize(power))
    }

    fun right(power: Float = 0.5f) {
        virtualStickVM.setRightPosition(normalize(power), 0)
    }

    fun left(power: Float = 0.5f) {
        virtualStickVM.setRightPosition(-normalize(power), 0)
    }

    fun rotateRight(power: Float = 0.5f) {
        virtualStickVM.setLeftPosition(normalize(power), 0)
    }

    fun rotateLeft(power: Float = 0.5f) {
        virtualStickVM.setLeftPosition(-normalize(power), 0)
    }

    fun up(power: Float = 0.5f) {
        virtualStickVM.setLeftPosition(0, normalize(power))
    }

    fun down(power: Float = 0.5f) {
        virtualStickVM.setLeftPosition(0, -normalize(power))
    }

    fun stop() {
        virtualStickVM.setLeftPosition(0, 0)
        virtualStickVM.setRightPosition(0, 0)
    }

    // ----- Takeoff / Landing -----

    fun takeOff(
        onOk: (() -> Unit)? = null,
        onErr: ((IDJIError) -> Unit)? = null
    ) {
        val state = virtualStickVM.currentVirtualStickStateInfo.value?.state

        if (state?.isVirtualStickEnable != true) {
            ToastUtils.showToast("Virtual Stick not enabled")
            return
        }

        basicAircraftControlVM.startTakeOff(object :
            CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {

            override fun onSuccess(t: EmptyMsg?) {
                onOk?.invoke()
            }

            override fun onFailure(error: IDJIError) {
                onErr?.invoke(error)
            }
        })
    }

    fun land(
        onOk: (() -> Unit)? = null,
        onErr: ((IDJIError) -> Unit)? = null
    ) {
        val state = virtualStickVM.currentVirtualStickStateInfo.value?.state

        if (state?.isVirtualStickEnable != true) {
            ToastUtils.showToast("Virtual Stick not enabled")
            return
        }

        basicAircraftControlVM.startLanding(object :
            CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {

            override fun onSuccess(t: EmptyMsg?) {
                stop()
                onOk?.invoke()
            }

            override fun onFailure(error: IDJIError) {
                onErr?.invoke(error)
            }
        })
    }

    // ----- Helpers -----

    private fun normalize(value: Float): Int {
        val absVal = if (abs(value) < deviation) 0f else value
        return (absVal.coerceIn(-1f, 1f) * max).toInt()
    }

}
