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
 * Writes the transcribed text into the focused editable field (ACTION_SET_TEXT).
 * It listens to / reads no events; it only finds the focused field when an utterance has ended.
 * If it is not enabled, the app copies the text to the clipboard.
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

    /** Called when the listening screen closes; waits briefly so that the app underneath can take focus back. */
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
            if (root.packageName?.toString() == packageName) continue // skip our own listening screen
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) return focused
            firstFocusedEditable(root)?.let { return it }
        }
        return null
    }

    /** Walks the tree with a bounded number of nodes and finds the first focused, editable node. */
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

        /** Presses "Home" through the accessibility service, if it is on. True if the press was sent. */
        fun goHome(): Boolean = try {
            instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
        } catch (_: Exception) {
            false
        }
    }
}
