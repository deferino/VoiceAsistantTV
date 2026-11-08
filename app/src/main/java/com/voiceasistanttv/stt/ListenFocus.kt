package com.voiceasistanttv.stt

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Keeps other apps quiet while the microphone is open: asks for transient, exclusive audio focus. Players that honor audio
 * focus (BitChord, most video apps) pause by themselves and resume by themselves when the focus is given back. Without this
 * the TV's own music would be recorded together with the voice and the speech would not be understood.
 *
 * Process-wide (not tied to an activity) so that, when another app is being opened, the focus can be released a few seconds
 * later: the new app takes the focus itself, and the old music does not flash back on in between.
 */
object ListenFocus {
    private const val TAG = "VoiceTV"
    private val handler = Handler(Looper.getMainLooper())
    private var audio: AudioManager? = null
    private var request: AudioFocusRequest? = null
    private var held = false
    private var pendingRelease: Runnable? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        // Another app (for example BitChord starting a new song) took the focus: it is no longer ours, so the next
        // acquire() must ask again instead of assuming we still hold it.
        Log.i(TAG, "focus: change=$change")
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
        ) {
            held = false
        }
    }

    /** Asks for the focus (listening starts). Safe to call again. */
    fun acquire(ctx: Context) {
        pendingRelease?.let { handler.removeCallbacks(it) }
        pendingRelease = null
        if (held) return
        val am = audio ?: (ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also { audio = it }
        val result = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(listener, handler)
                    .build()
                request = r
                am.requestAudioFocus(r)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "focus: request failed: ${e.message}")
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i(TAG, "focus: requested, granted=$held")
    }

    /** Is something playing on the music stream right now? */
    fun musicActive(ctx: Context): Boolean = try {
        val am = audio ?: (ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also { audio = it }
        am.isMusicActive
    } catch (e: Exception) {
        false
    }

    /** Gives the focus back (the paused music resumes), now or after [delayMs]. */
    fun release(delayMs: Long = 0L) {
        pendingRelease?.let { handler.removeCallbacks(it) }
        pendingRelease = null
        if (!held) return
        if (delayMs <= 0L) {
            doRelease()
        } else {
            val r = Runnable { pendingRelease = null; doRelease() }
            pendingRelease = r
            handler.postDelayed(r, delayMs)
        }
    }

    private fun doRelease() {
        val am = audio ?: return
        held = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                request?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(listener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "focus: release failed: ${e.message}")
        }
        request = null
        Log.i(TAG, "focus: released")
    }
}
