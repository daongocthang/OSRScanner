package com.runit.ocrscanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.runit.ocrscanner.model.ScanArea
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile

class OcrEngine(
    private val onResult: (String) -> Unit,
    private val onError: (Exception) -> Unit
) {
    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Volatile
    var enabled = true

    @Volatile
    var scale = 2f
        set(value) {
            field = value.coerceIn(1f, 6f)
        }

    @Volatile
    var intervalMs = 3000L
        set(value) {
            field = value.coerceAtLeast(0L)
        }

    private var lastScanTime = 0L

    /**
     * Process a CameraX frame.
     * The ImageProxy is always closed after processing.
     */
    fun process(
        proxy: ImageProxy,
        scanArea: ScanArea?,
        viewWidth: Int,
        viewHeight: Int
    ) {
        if (
            closed.get() ||
            !enabled ||
            scanArea == null
        ) {
            proxy.close()
            return
        }


        // Only one OCR request at a time.
        if (!busy.compareAndSet(false, true)) {
            proxy.close()
            return
        }

        val now = SystemClock.elapsedRealtime()

        // Skip frames until the next OCR interval.
        if (now - lastScanTime < intervalMs) {
            busy.set(false)
            proxy.close()
            return
        }

        lastScanTime = now

        val bitmaps = mutableListOf<Bitmap>()

        try {
            val source = proxy.toBitmap()
            bitmaps.add(source)
            // 0. Crop with ImageProxy.cropRect
            val cameraCrop = cropCamera(
                source,
                proxy.cropRect
            )
            if (cameraCrop !== source) {
                bitmaps.add(cameraCrop)
            }


            // 1. Rotate
            val upright = rotate(cameraCrop, proxy.imageInfo.rotationDegrees)
            if (upright !== source) {
                bitmaps.add(upright)
            }

            // 2. Crop with ScanArea
            val cropped = cropForOCR(upright, scanArea, viewWidth, viewHeight)
            bitmaps.add(cropped)

            // 3. Resize
            val scaled = resize(cropped, scale)
            if (scaled !== cropped) {
                bitmaps.add(scaled)
            }

            // 4. OCR
            val image = InputImage.fromBitmap(scaled, 0)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (!closed.get() && enabled) {
                        onResult(result.text)
                    }

                }
                .addOnFailureListener { error ->
                    if (!closed.get()) {
                        onError(error)
                    }
                }
                .addOnCompleteListener {
                    release(bitmaps, proxy)
                }
        } catch (e: Exception) {
            try {
                if (!closed.get()) onError(e)
            } finally {
                release(bitmaps, proxy)
            }
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
        }

        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
    }

    private fun cropCamera(bitmap: Bitmap, rect: Rect): Bitmap {
        val bounds = Rect(0, 0, bitmap.width, bitmap.height)
        val safe = Rect(rect)
        require(safe.intersect(bounds)) {
            "Invalid camera cropRect"
        }
        if (
            safe.left == 0 &&
            safe.top == 0 &&
            safe.right == bitmap.width &&
            safe.bottom == bitmap.height
        ) {
            return bitmap
        }
        return Bitmap.createBitmap(
            bitmap,
            safe.left,
            safe.top,
            safe.width(),
            safe.height()
        )
    }

    private fun cropForOCR(
        bitmap: Bitmap,
        scanArea: ScanArea,
        viewWidth: Int,
        viewHeight: Int
    ): Bitmap {
        require(viewWidth > 0 && viewHeight > 0)

        val imageWidth = bitmap.width
        val imageHeight = bitmap.height

        val scaleX = viewWidth.toFloat() / imageWidth
        val scaleY = viewHeight.toFloat() / imageHeight
        val scale = maxOf(scaleX, scaleY)

        val scaleWidth = imageWidth * scale
        val scaleHeight = imageHeight * scale

        val offsetX = (scaleWidth - viewWidth) / 2f
        val offsetY = (scaleHeight - viewHeight) / 2f

        val box = scanArea.rect(viewWidth, viewHeight)

        val left = ((box.left + offsetX) / scale)
            .toInt().coerceIn(0, imageWidth - 1)

        val top = ((box.top + offsetY) / scale)
            .toInt().coerceIn(0, imageHeight - 1)

        val right = ((box.right + offsetX) / scale)
            .toInt().coerceIn(left + 1, imageWidth)

        val bottom = ((box.bottom + offsetY) / scale)
            .toInt().coerceIn(top + 1, imageHeight)


        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top
        )

    }

    private fun resize(bitmap: Bitmap, scale: Float): Bitmap {
        if (scale <= 1f) return bitmap
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /**
     * Release temporary bitmaps and the CameraX frame.
     */
    private fun release(bitmaps: List<Bitmap>, proxy: ImageProxy) {
        try {
            bitmaps.asReversed().forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        } finally {
            proxy.close()
            busy.set(false)
        }
    }

    /**
     * Release ML Kit resources.
     * Call only after the analyzer has stopped.
     */
    fun close() {
        if (closed.compareAndSet(false, true)) {
            enabled = false
            recognizer.close()
        }
    }
}