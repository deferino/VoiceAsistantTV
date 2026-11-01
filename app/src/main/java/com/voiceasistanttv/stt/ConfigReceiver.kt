package com.voiceasistanttv.stt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * For the install script (adb) to send settings. The manifest requires `android.permission.DUMP`: only adb shell
 * can send. Values (API key etc.) are never written to the log.
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Config.init(context)
        val n = Config.applyExtras(intent.extras)
        Log.i("VoiceTV", "settings received: $n values")
        if (isOrderedBroadcast) {
            resultCode = 1
            resultData = "ok:$n"
        }
    }
}
