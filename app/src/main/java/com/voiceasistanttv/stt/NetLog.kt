package com.voiceasistanttv.stt

import android.os.SystemClock
import android.util.Log

/** Writes the duration of network requests to logcat (server name + ms only; the search text / URL query is not written). */
object NetLog {
    private const val TAG = "VoiceTV"

    fun host(url: String): String = try {
        java.net.URL(url).host
    } catch (_: Exception) {
        "?"
    }

    inline fun <T> timed(url: String, block: () -> T): T {
        val t0 = SystemClock.elapsedRealtime()
        var ok = false
        try {
            val r = block()
            ok = true
            return r
        } finally {
            Log.i("VoiceTV", "http ${host(url)} ${SystemClock.elapsedRealtime() - t0} ms" + if (ok) "" else " (error)")
        }
    }
}
