package com.runit.ocrscanner.model

data class ScanResult(
    val code: String,
    val timeMillis: Long = System.currentTimeMillis()
)
