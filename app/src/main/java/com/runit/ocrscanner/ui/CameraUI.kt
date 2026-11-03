package com.runit.ocrscanner.ui


import android.graphics.RectF
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runit.ocrscanner.OcrEngine
import com.runit.ocrscanner.model.ScanBox
import java.util.concurrent.Executors

@OptIn(TransformExperimental::class)
@Composable
fun CameraUI(
    enabled: Boolean,
    flash: Boolean,
    scale: Float = 2f,
    intervalMs: Long = 3000L,
    scanBox: ScanBox,
    onResult: (String) -> Unit
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnResult by rememberUpdatedState(onResult)

    val engine = remember {
        OcrEngine(onResult = { result ->
            ContextCompat.getMainExecutor(context).execute {
                currentOnResult(result)
            }
        }, onError = { error ->
            Log.e("OcrEngine", "OCR failed", error)
        })
    }

    SideEffect {
        engine.enabled = enabled
        engine.scale = scale
        engine.intervalMs = intervalMs
    }

    val executor = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }


    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val currentScanBox by rememberUpdatedState(scanBox)

    LaunchedEffect(camera, flash) {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(flash)
            }
        }
    }

    DisposableEffect(lifecycleOwner, viewSize) {
        if (viewSize.width == 0 && viewSize.height == 0) {
            onDispose { }
        } else {
            var disposed = false
            var provider: ProcessCameraProvider? = null
            var analysis: ImageAnalysis? = null
            val future = ProcessCameraProvider.getInstance(context)

            future.addListener(
                {
                    if (disposed) return@addListener
                    try {

                        val cameraProvider = future.get()
                        provider = cameraProvider

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build()

                        analysis = imageAnalysis

                        imageAnalysis.setAnalyzer(executor) { proxy ->
                            try {
                                engine.process(
                                    proxy,
                                    scanBox,
                                    viewSize.width,
                                    viewSize.height
                                )
                            } catch (e: Exception) {
                                Log.e("CameraUI", "ScanRect error", e)
                                proxy.close()
                            }
                        }

                        cameraProvider.unbindAll()

                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                        )

                    } catch (e: Exception) {
                        Log.e("CameraUI", "Camera failed", e)
                    }
                }, ContextCompat.getMainExecutor(context)
            )

            onDispose {
                disposed = true
                engine.enabled = false
                analysis?.clearAnalyzer()
                provider?.unbindAll()
                camera = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it })
}



