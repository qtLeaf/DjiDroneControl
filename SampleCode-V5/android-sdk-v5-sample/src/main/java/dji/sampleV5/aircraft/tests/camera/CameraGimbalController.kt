package dji.sampleV5.aircraft.tests.camera

import dji.sdk.keyvalue.key.GimbalKey
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.gimbal.GimbalAngleRotation
import dji.v5.manager.KeyManager
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.callback.CommonCallbacks.CompletionCallbackWithParam
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.sdk.keyvalue.value.gimbal.GimbalAngleRotationMode
import dji.v5.et.create

class CameraGimbalController(
    private val onDebug: (String) -> Unit
) {


    private val gimbalIndex = ComponentIndexType.LEFT_OR_MAIN
    private val cameraIndex = ComponentIndexType.LEFT_OR_MAIN

    private fun debug(msg: String) {
        onDebug(msg)
    }

    /**
     * Rotate gimbal with absolute angles
     */
    fun rotateTo(
        pitch: Double,
        yaw: Double,
        roll: Double = 0.0
    ) {

        val rotation = GimbalAngleRotation().apply {
            this.pitch = pitch
            this.yaw = yaw
            this.roll = roll
            this.duration = 0.5
            mode = GimbalAngleRotationMode.ABSOLUTE_ANGLE

        }

        val key = GimbalKey.KeyRotateByAngle.create(gimbalIndex)

        KeyManager.getInstance().performAction(
            key,
            rotation,
            object : CompletionCallbackWithParam<EmptyMsg> {

                override fun onSuccess(param: EmptyMsg?) {
                    debug("Gimbal rotated to P:$pitch Y:$yaw")
                }

                override fun onFailure(error: IDJIError) {
                    debug("Gimbal rotation failed: ${error.description()}")
                }
            }
        )
    }

    /**
     * Relative movement (useful for joystick-like commands)
     */
    fun rotateRelative(
        pitch: Double,
        yaw: Double
    ) {

        val rotation = GimbalAngleRotation().apply {
            this.pitch = pitch
            this.yaw = yaw
            this.duration = 0.3
            mode = GimbalAngleRotationMode.ABSOLUTE_ANGLE

        }

        val key = GimbalKey.KeyRotateByAngle.create(gimbalIndex)

        KeyManager.getInstance().performAction(
            key,
            rotation,
            object : CompletionCallbackWithParam<EmptyMsg> {

                override fun onSuccess(param: EmptyMsg?) {
                    debug("Relative gimbal move P:$pitch Y:$yaw")
                }

                override fun onFailure(error: IDJIError) {
                    debug("Relative rotation failed: ${error.description()}")
                }
            }
        )
    }

    /**
     * Reset gimbal (look forward)
     */
    fun reset() {

        rotateTo(
            pitch = 0.0,
            yaw = 0.0,
            roll = 0.0
        )
    }

    /**
     * Camera zoom control
     */
    fun setZoom(zoom: Double) {

        val zoomKey = CameraKey.KeyCameraZoomRatios.create(cameraIndex)

        KeyManager.getInstance().setValue(
            zoomKey,
            zoom,
            object : CommonCallbacks.CompletionCallback {

                override fun onSuccess() {
                    debug("Zoom set to $zoom")
                }

                override fun onFailure(error: IDJIError) {
                    debug("Zoom failed: ${error.description()}")
                }
            }
        )
    }

    /**
     * Zoom step helper
     */
    fun zoomIn(step: Double = 0.5) {

        val zoomKey = CameraKey.KeyCameraZoomRatios.create(cameraIndex)
        val current = KeyManager.getInstance().getValue(zoomKey) ?: return

        setZoom(current + step)
    }

    fun zoomOut(step: Double = 0.5) {

        val zoomKey = CameraKey.KeyCameraZoomRatios.create(cameraIndex)
        val current = KeyManager.getInstance().getValue(zoomKey) ?: return

        setZoom((current - step).coerceAtLeast(1.0))
    }
}