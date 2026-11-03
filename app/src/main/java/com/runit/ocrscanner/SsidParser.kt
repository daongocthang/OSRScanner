package com.runit.ocrscanner

import java.util.Locale

object SsidParser {
    private val regex = Regex(
        """(?i)\bVIETTEL[_\s]+[A-Z0-9._\s]+"""
    )

    fun parse(text: String): String? {
        val ssid = regex.find(text)?.value ?: return null
        return ssid
            .replace(Regex("""[\s\t]{2,}.*"""), "")
            .replace(Regex("""[_\s]"""), "_")
            .replace(Regex("""(?i)(?:_(?:5G|2.4G))?$"""), "")
            .ifEmpty { null }
    }
}