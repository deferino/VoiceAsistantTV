package com.voiceasistanttv.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * When this app is set as the default "assistant", the remote's assistant/microphone key
 * comes here instead of Google Assistant and opens the listening screen (AssistActivity).
 * Assignment commands: install.bat (or "Setup commands" in the app).
 */
class AssistService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        Log.i(TAG, "AssistService ready: bound as the default assistant")
    }

    override fun onShutdown() {
        Log.i(TAG, "AssistService stopped")
        super.onShutdown()
    }
}

private const val TAG = "VoiceTV"

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.i(TAG, "new assistant session")
        return AssistSession(this)
    }
}

class AssistSession(context: Context) : VoiceInteractionSession(context) {
    override fun onCreate() {
        super.onCreate()
        // Do not draw our own window; AssistActivity will show the UI.
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i(TAG, "assistant key received: onShow showFlags=$showFlags")
        current = this
        // If a keyboard is on screen, it starts/ends dictation. The decision is made IMMEDIATELY: if no keyboard is visible,
        // the listening screen opens right away as before (inside this call). Waiting for an asynchronous answer
        // could cause a "hidden session" error if the system hid the session in between.
        val kb = InjectorService.instance?.isKeyboardWindowShown()
        Log.i(TAG, "keyboard window visible=$kb")
        if (kb == true) {
            KeyboardHandoff.offer(context) { handled ->
                if (handled) {
                    if (current === this) current = null
                    try {
                        hide()
                    } catch (_: Exception) {
                    }
                } else openAssistant()
            }
        } else {
            openAssistant()
        }
    }

    private fun openAssistant() {
        val intent = Intent(context, AssistActivity::class.java).setAction(Intent.ACTION_ASSIST)
        try {
            startAssistantActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "could not open the listening screen (session): ${e.message}")
            try {
                context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                Log.w(TAG, "could not open the listening screen (direct): ${e2.message}")
            }
        }
    }

    override fun onHide() {
        super.onHide()
        Log.i(TAG, "session hidden (onHide)")
        if (current === this) current = null
    }

    companion object {
        @Volatile
        var current: AssistSession? = null

        /** Also close the session when the listening screen closes. */
        fun close() {
            try {
                current?.hide()
            } catch (_: Exception) {
            }
        }
    }
}
