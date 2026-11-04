package com.runit.ocrscanner.model

import android.graphics.RectF

data class ScanArea(
    val width: Float = 0.9f,
    val height: Float = 0.9f,
) {
    fun rect(viewWidth: Int, viewHeight: Int): RectF {
        val w = viewWidth * width.coerceIn(0f, 1f)
        val h = viewHeight * height.coerceIn(0f, 1f)

        return RectF(
            (viewWidth - w) / 2f,
            (viewHeight - h) / 2f,
            (viewWidth + w) / 2f,
            (viewHeight + h) / 2f
        )
    }
}
