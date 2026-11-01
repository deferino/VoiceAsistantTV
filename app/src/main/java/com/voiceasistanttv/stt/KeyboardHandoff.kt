package com.voiceasistanttv.stt

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When the remote's assistant key is pressed and a keyboard (Simple Keyboard TV) is on screen, instead of opening
 * the listening screen it starts the keyboard's own "Voice typing" feature. That way the text field's window
 * does not lose focus and the speech is written directly into the field.
 *
 * Communication uses only public Android APIs (ordered broadcast); no code is shared between the two apps.
 * If the keyboard is not on screen / does not answer, the old behavior (AssistActivity) continues.
 */
object KeyboardHandoff {
    private const val TAG = "VoiceTV"
    const val KEYBOARD_PACKAGE = "rkr.simplekeyboard.inputmethod.tv"
    const val ACTION_VOICE_KEY = "rkr.simplekeyboard.inputmethod.tv.action.VOICE_KEY"
    private const val TIMEOUT_MS = 700L

    /** Is the keyboard the default input method? (cheap pre-check: if not, no broadcast is sent) */
    private fun keyboardIsCurrentIme(context: Context): Boolean {
        val cur = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return cur != null && cur.startsWith("$KEYBOARD_PACKAGE/")
    }

    /**
     * Tells the keyboard "you start/end the dictation". [onDone] is called exactly once, on the main thread:
     * true = the keyboard took over, false = it did not (the assistant screen must be opened).
     */
    fun offer(context: Context, onDone: (Boolean) -> Unit) {
        if (!keyboardIsCurrentIme(context)) {
            onDone(false)
            return
        }
        val main = Handler(Looper.getMainLooper())
        val finished = AtomicBoolean(false)
        lateinit var timeout: Runnable
        fun finish(handled: Boolean) {
            if (finished.compareAndSet(false, true)) {
                main.removeCallbacks(timeout)
                Log.i(TAG, "keyboard handoff: ${if (handled) "taken over" else "not taken over"}")
                onDone(handled)
            }
        }
        timeout = Runnable { finish(false) }
        main.postDelayed(timeout, TIMEOUT_MS)
        try {
            val intent = Intent(ACTION_VOICE_KEY)
                .setPackage(KEYBOARD_PACKAGE)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendOrderedBroadcast(
                intent, null,
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, i: Intent?) {
                        finish(resultCode == Activity.RESULT_OK)
                    }
                },
                main, Activity.RESULT_CANCELED, null, null
            )
        } catch (e: Exception) {
            Log.w(TAG, "could not send the broadcast to the keyboard: ${e.message}")
            finish(false)
        }
    }
}
