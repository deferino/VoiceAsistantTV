package com.voiceasistanttv.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Bu uygulama varsayılan "asistan" olarak atandığında kumandadaki asistan/mikrofon tuşu
 * Google Asistan yerine buraya gelir ve dinleme ekranını (AssistActivity) açar.
 * Atama komutları: kurulum.bat (veya uygulamadaki "Kurulum komutları").
 */
class AssistService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        Log.i(TAG, "AssistService hazır: varsayılan asistan olarak bağlandık")
    }

    override fun onShutdown() {
        Log.i(TAG, "AssistService kapandı")
        super.onShutdown()
    }
}

private const val TAG = "VoiceTV"

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.i(TAG, "yeni asistan oturumu")
        return AssistSession(this)
    }
}

class AssistSession(context: Context) : VoiceInteractionSession(context) {
    override fun onCreate() {
        super.onCreate()
        // Kendi penceresini çizme; arayüzü AssistActivity gösterecek.
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i(TAG, "asistan tuşu geldi: onShow showFlags=$showFlags")
        current = this
        // Klavye ekrandaysa dikteyi o baslatir/bitirir. Karar ANINDA verilir: klavye gorunmuyorsa
        // dinleme ekrani eskisi gibi hemen (bu cagri icinde) acilir. Asenkron bir yanit beklemek,
        // oturum arada sistem tarafindan gizlenirse "hidden session" hatasina yol aciyordu.
        val kb = InjectorService.instance?.isKeyboardWindowShown()
        Log.i(TAG, "klavye penceresi gorunur=$kb")
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
            Log.w(TAG, "dinleme ekrani acilamadi (oturum): ${e.message}")
            try {
                context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                Log.w(TAG, "dinleme ekrani acilamadi (dogrudan): ${e2.message}")
            }
        }
    }

    override fun onHide() {
        super.onHide()
        Log.i(TAG, "oturum gizlendi (onHide)")
        if (current === this) current = null
    }

    companion object {
        @Volatile
        var current: AssistSession? = null

        /** Dinleme ekranı kapanınca oturumu da kapat. */
        fun close() {
            try {
                current?.hide()
            } catch (_: Exception) {
            }
        }
    }
}
