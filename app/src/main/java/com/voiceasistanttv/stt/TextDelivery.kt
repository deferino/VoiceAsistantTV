package com.voiceasistanttv.stt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Çevrilen metni odaktaki alana ulaştırır:
 *  1) Erişilebilirlik servisi açıksa odaktaki metin alanına yazar,
 *  2) değilse panoya kopyalar ve kısa bir bildirim gösterir.
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
