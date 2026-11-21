package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator

object ChatSoundHelper {
    private var lastSoundTimeMs: Long = 0L

    fun playNewMessageSound(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastSoundTimeMs < 700L) return
        lastSoundTimeMs = now

        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    tg.release()
                } catch (_: Exception) {
                }
            }, 300L)
        } catch (_: Throwable) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(context.applicationContext, uri)?.play()
            } catch (_: Throwable) {
                // Safe no-op in headless/Robolectric environments
            }
        }
    }
}
