package com.voiceasistanttv.stt

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Çevrilen metni odaktaki düzenlenebilir alana yazar (ACTION_SET_TEXT).
 * Hiçbir olay dinlemez/okumaz; sadece bir konuşma bittiğinde odaktaki alanı bulur.
 * Açık değilse uygulama metni panoya kopyalar.
 */
class InjectorService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        Config.init(this)
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * Ekranda bir klavye (giris yontemi) penceresi var mi? Yalnizca pencere TURUNE bakar, icerik
     * okumaz. Servis hazir degilse veya sorgu basarisizsa null. Asistan tusuna basildiginda
     * "klavye acik mi" kararini ANINDA (es zamanli) verebilmek icin kullanilir.
     */
    fun isKeyboardWindowShown(): Boolean? = try {
        windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    } catch (_: Exception) {
        null
    }

    /** Dinleme ekranı kapanırken çağrılır; alt uygulamanın odağı geri alması için kısa bekler. */
    fun insertLater(text: String) = attempt(text, 0)

    private fun attempt(text: String, n: Int) {
        handler.postDelayed({
            if (tryInsert(text)) return@postDelayed
            if (n < MAX_TRIES) {
                attempt(text, n + 1)
            } else {
                TextDelivery.copyToClipboard(this, text)
                TextDelivery.toast(this, Lang.s(R.string.toast_no_field_copied, text))
            }
        }, if (n == 0) 400L else 250L)
    }

    private fun tryInsert(text: String): Boolean {
        val node = findInputNode() ?: return false
        val showingHint = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && node.isShowingHintText
        val existing = if (showingHint) "" else node.text?.toString().orEmpty()
        val newText =
            if (Config.insertMode == Config.INSERT_REPLACE || existing.isBlank()) text
            else existing.trimEnd() + " " + text

        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false

        val sel = Bundle()
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newText.length)
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newText.length)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)

        if (Config.autoSubmit && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            handler.postDelayed({
                node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }, 200L)
        }
        return true
    }

    private fun findInputNode(): AccessibilityNodeInfo? {
        val roots = ArrayList<AccessibilityNodeInfo>()
        try {
            for (w in windows.sortedByDescending { it.layer }) {
                w.root?.let { roots.add(it) }
            }
        } catch (_: Exception) {
        }
        rootInActiveWindow?.let { roots.add(it) }

        for (root in roots) {
            if (root.packageName?.toString() == packageName) continue // kendi dinleme ekranımızı atla
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) return focused
            firstFocusedEditable(root)?.let { return it }
        }
        return null
    }

    /** Ağacı sınırlı sayıda düğümle gezip odaklı, düzenlenebilir ilk düğümü bulur. */
    private fun firstFocusedEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val n = stack.removeLast()
            visited++
            if (n.isEditable && (n.isFocused || n.isAccessibilityFocused)) return n
            for (i in n.childCount - 1 downTo 0) {
                n.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return null
    }

    companion object {
        private const val MAX_TRIES = 6
        private const val MAX_NODES = 2000

        @Volatile
        var instance: InjectorService? = null
    }
}
