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
        try {
            startAssistantActivity(Intent(context, AssistActivity::class.java).setAction(Intent.ACTION_ASSIST))
        } catch (e: Exception) {
            Log.w(TAG, "could not open the listening screen: ${e.message}")
        }
    }

    override fun onHide() {
        super.onHide()
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
