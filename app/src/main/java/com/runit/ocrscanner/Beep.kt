package com.runit.ocrscanner

import android.media.AudioManager
import android.media.ToneGenerator

class Beep {
    private val tone = ToneGenerator(
        AudioManager.STREAM_MUSIC,
        80
    )

    fun play() {
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    fun close() {
        tone.release()
    }
}