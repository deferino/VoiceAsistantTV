package com.voiceasistanttv.stt

import android.content.Context
import android.content.ContextParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Sistem konuşma tanıyıcısı (SpeechRecognizer API'si). Arama kutularındaki mikrofon düğmeleri
 * gibi RecognizerIntent/SpeechRecognizer kullanan uygulamalar bunu çağırır.
 * Sayboard'daki SayboardRecognitionService'in bulut STT'ye uyarlanmış hâlidir.
 */
class CloudRecognitionService : RecognitionService() {
    private var transcriber: Transcriber? = null
    private var callback: Callback? = null

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener ?: return
        cancelCurrent()
        callback = listener
        Config.init(this)

        val attribution: Context? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            createContext(
                ContextParams.Builder().setNextAttributionSource(listener.callingAttributionSource).build()
            )
        } else null

        val requested = recognizerIntent?.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
            ?.let { Locale.forLanguageTag(it).language }
            ?.takeIf { it.isNotBlank() }

        // Klavye yolu: TV'nin yerlesik (uzak alan) mikrofonu cok daha sessiz gelir ve sabit 500 esigi
        // konusmanin cogunu kesiyordu. Esik gurultuye gore (3x), taban 40; klip payi daha genis.
        // Asistan tusu yolu (AssistActivity) bu ayarlari KULLANMAZ, degismedi.
        val t = Transcriber(
            this, attribution, requested, tag = "klavye",
            vadFloor = minOf(Config.vadThreshold, KEYBOARD_VAD_FLOOR),
            farField = true, sourceOverride = Config.kbAudioSource
        )
        transcriber = t
        t.start(object : Transcriber.Listener {
            override fun onReady() = safe { listener.readyForSpeech(Bundle()) }
            override fun onSpeechStart() = safe { listener.beginningOfSpeech() }
            override fun onLevel(rms: Int, speaking: Boolean) {
                // 0..10 aralığına yaklaşık dB eşlemesi
                val v = (Math.log(rms.toDouble() + 1.0) / Math.log(32768.0) * 10.0).toFloat()
                safe { listener.rmsChanged(v) }
            }

            override fun onProcessing() = safe { listener.endOfSpeech() }
            override fun onResult(text: String) {
                val b = Bundle()
                b.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
                b.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1f))
                safe { listener.results(b) }
                finishCurrent()
            }

            override fun onError(code: Transcriber.Code, message: String) {
                val err = when (code) {
                    Transcriber.Code.NO_PERMISSION -> SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                    Transcriber.Code.MIC_UNAVAILABLE -> SpeechRecognizer.ERROR_AUDIO
                    Transcriber.Code.NO_SPEECH -> SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    Transcriber.Code.EMPTY -> SpeechRecognizer.ERROR_NO_MATCH
                    Transcriber.Code.NETWORK -> SpeechRecognizer.ERROR_NETWORK
                    Transcriber.Code.API -> SpeechRecognizer.ERROR_SERVER
                    Transcriber.Code.CONFIG -> SpeechRecognizer.ERROR_CLIENT
                }
                safe { listener.error(err) }
                finishCurrent()
            }
        })
    }

    override fun onStopListening(listener: Callback?) {
        transcriber?.stopNow()
    }

    override fun onCancel(listener: Callback?) {
        cancelCurrent()
    }

    override fun onDestroy() {
        cancelCurrent()
        super.onDestroy()
    }

    private fun finishCurrent() {
        transcriber = null
        callback = null
    }

    private fun cancelCurrent() {
        transcriber?.cancel()
        finishCurrent()
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: RemoteException) {
            Log.e(TAG, "Çağıran uygulamaya ulaşılamadı", e)
        }
    }

    companion object {
        private const val TAG = "VoiceTV"
        private const val KEYBOARD_VAD_FLOOR = 40
    }
}
