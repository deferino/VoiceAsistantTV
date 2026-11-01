package com.voiceasistanttv.stt

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.TextView

/**
 * Shows which key code each remote key arrives with.
 * If nothing shows up here when you press the assistant/microphone key, the system is not delivering the key to apps at all
 * (in that case we take the key over by being the default assistant). Press Back twice to exit.
 */
class KeyTestActivity : Activity() {
    private lateinit var tv: TextView
    private val lines = ArrayList<String>()
    private var lastBack = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tv = TextView(this)
        tv.textSize = 22f
        tv.typeface = Typeface.MONOSPACE
        tv.setTextColor(0xFFFFFFFF.toInt())
        tv.setBackgroundColor(0xFF121216.toInt())
        tv.setPadding(dp(48), dp(32), dp(48), dp(32))
        setContentView(tv)
        render()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                val now = SystemClock.uptimeMillis()
                if (now - lastBack < 800L) {
                    finish()
                    return true
                }
                lastBack = now
            }
            lines.add(
                0,
                Lang.s(R.string.keytest_line, KeyEvent.keyCodeToString(event.keyCode), event.keyCode, event.scanCode, event.deviceId)
            )
            while (lines.size > 12) lines.removeAt(lines.size - 1)
            render()
        }
        return true
    }

    private fun render() {
        val sb = StringBuilder()
        sb.append(Lang.s(R.string.keytest_title))
        sb.append(Lang.s(R.string.keytest_line1))
        sb.append(Lang.s(R.string.keytest_line2))
        for (l in lines) sb.append(l).append('\n')
        tv.text = sb.toString()
    }
}
