package com.runit.ocrscanner

import java.util.Locale

object SsidParser {
    private val regex = Regex(
        """(?i)\bVIETTEL[_ ]+[A-Z0-9._ ]+"""
    )

    fun parse(text: String): String? {
        val ssid = regex.find(text)?.value ?: return null
        return ssid
            .replace(Regex("""[ _]+"""), "_")
            .replace(Regex("""(?i)_(?:WLAN|Wi[-_]?Fi).*$"""), "")
            .replace(Regex("""(?i)(?:_(?:5G|2.4G))?$"""), "")
            .trim('_')
            .split('_')
            .take(3)
            .joinToString("_")
            .substringAfterLast('_')
            .takeLast(6)
            .takeIf {
                it.length > 3 &&
                        !"VIETTEL".contains(it, ignoreCase = true)
            }
            ?.uppercase(Locale.US)
    }
}