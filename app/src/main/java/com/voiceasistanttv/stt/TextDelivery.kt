package com.voiceasistanttv.stt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Delivers the transcribed text to the focused field:
 *  1) writes into the focused text field if the accessibility service is on,
 *  2) otherwise copies it to the clipboard and shows a short notification.
 */
object TextDelivery {
    fun deliver(ctx: Context, text: String) {
        val app = ctx.applicationContext
        val svc = InjectorService.instance
        if (svc != null) {
            svc.insertLater(text)
        } else {
            copyToClipboard(app, text)
            toast(app, Lang.s(R.string.toast_a11y_off_copied, text))
        }
    }

    fun copyToClipboard(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("stt", text))
    }

    fun toast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }
}
