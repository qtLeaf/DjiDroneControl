package dji.sampleV5.aircraft.tests.camera

import android.view.Surface
import dji.sdk.keyvalue.key.CameraKey
import dji.v5.ux.core.base.DJISDKModel
import dji.v5.ux.core.communication.ObservableInMemoryKeyedStore
import dji.v5.ux.core.module.FlatCameraModule
import dji.v5.ux.core.widget.fpv.FPVStreamSourceListener
import dji.v5.ux.core.widget.fpv.FPVWidget
import dji.v5.ux.core.widget.fpv.FPVWidgetModel
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.common.CameraLensType
import dji.v5.manager.interfaces.ICameraStreamManager


class CameraTest(
    fpvWidget: FPVWidget
) {
/*
    private val fpvModel = FPVWidgetModel(
        DJISDKModel.getInstance(),
        ObservableInMemoryKeyedStore.getInstance(),
        FlatCameraModule()
    )

    init {
        // Set listener for stream source updates
        fpvModel.streamSourceListener = object : FPVStreamSourceListener {
            override fun onStreamSourceUpdated(
                cameraIndex: ComponentIndexType,
                lensType: CameraLensType
            ) {
                // Simple log or UI update
                println("Stream source updated: $cameraIndex / $lensType")
            }
        }

        // Attach model to FPV widget
        // FPVWidget requires its own model reference
        fpvWidget.widgetModel = fpvModel
        fpvModel.setup()
    }

    fun attachSurface(surface: Surface, width: Int, height: Int) {
        // Adds a Surface for video stream
        fpvModel.putCameraStreamSurface(
            surface,
            width,
            height,
            ICameraStreamManager.ScaleType.CENTER_CROP
        )
    }

    fun detachSurface(surface: Surface) {
        // Removes the surface
        fpvModel.removeCameraStreamSurface(surface)
    }

    fun enableVisionAssist() {
        // Enables additional vision assist feed
        fpvModel.enableVisionAssist()
    }

    fun getCameraInfo(): String {
        // Returns the processed camera info text
        val side = fpvModel.cameraSideProcessor.value
        val msg = fpvModel.displayMsgProcessor.value
        return "Camera side: $side | Info: $msg"
    }

    fun hasViewChanged(): Boolean {
        // Reads the video view changed processor
        return fpvModel.hasVideoViewChanged.blockingFirst()
    }

    fun forceSourceRefresh() {
        // Forces internal refresh if needed
        fpvModel.updateCameraSource(
            fpvModel.cameraIndex,
            fpvModel.lensType
        )
    }

    fun cleanup() {
        fpvModel.cleanup()
    }


    fun zoomIn() {
        CameraKey.KeyOpticalZoomFactor
            .create(fpvModel.cameraIndex, fpvModel.lensType)
            .set(1) {}
    }

    fun zoomOut() {
        CameraKey.KeyStartZoom
            .create(fpvModel.cameraIndex, fpvModel.lensType)
            .set(-1) {}
    }

    fun zoomStop() {
        CameraKey.KeyStopZoom
            .create(fpvModel.cameraIndex, fpvModel.lensType)
            .action {}
    }
*/
}
