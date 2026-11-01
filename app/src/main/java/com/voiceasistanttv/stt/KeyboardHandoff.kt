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
 * Kumandadaki asistan tusuna basildiginda klavye (Simple Keyboard TV) ekrandaysa dinleme ekranini
 * acmak yerine klavyenin kendi "Sesle yaz" ozelligini baslatir. Boylece metin alaninin penceresi
 * odagini kaybetmez ve konusma dogrudan alana yazilir.
 *
 * Iletisim yalnizca genel Android API'leriyle yapilir (siralı yayin); iki uygulama arasinda kod
 * paylasimi yoktur. Klavye ekranda degilse / yaniti gelmezse eski davranis (AssistActivity) surer.
 */
object KeyboardHandoff {
    private const val TAG = "VoiceTV"
    const val KEYBOARD_PACKAGE = "rkr.simplekeyboard.inputmethod.tv"
    const val ACTION_VOICE_KEY = "rkr.simplekeyboard.inputmethod.tv.action.VOICE_KEY"
    private const val TIMEOUT_MS = 700L

    /** Klavye varsayilan giris yontemi mi? (ucuz on kontrol: degilse yayin gonderilmez) */
    private fun keyboardIsCurrentIme(context: Context): Boolean {
        val cur = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return cur != null && cur.startsWith("$KEYBOARD_PACKAGE/")
    }

    /**
     * Klavyeye "dikteyi sen baslat/bitir" der. [onDone] tam bir kez, ana iplikte cagrilir:
     * true = klavye ustlendi, false = ustlenmedi (asistan ekrani acilmali).
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
                Log.i(TAG, "klavye devri: ${if (handled) "ustlendi" else "ustlenmedi"}")
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
            Log.w(TAG, "klavyeye yayin gonderilemedi: ${e.message}")
            finish(false)
        }
    }
}
