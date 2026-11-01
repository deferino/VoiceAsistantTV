package com.voiceasistanttv.stt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Kurulum betiğinin (adb) ayar göndermesi için. Manifest'te `android.permission.DUMP` istenir: yalnızca adb shell
 * gönderebilir. Değerler (API anahtarı vb.) loga yazılmaz.
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Config.init(context)
        val n = Config.applyExtras(intent.extras)
        Log.i("VoiceTV", "ayar alındı: $n değer")
        if (isOrderedBroadcast) {
            resultCode = 1
            resultData = "ok:$n"
        }
    }
}
