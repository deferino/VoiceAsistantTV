package com.voiceasistanttv.stt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.SystemClock
import android.view.View
import android.widget.ScrollView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

/** Button/row background that becomes prominent when focused with the remote. */
fun Context.focusDrawable(): StateListDrawable {
    val focused = GradientDrawable()
    focused.setColor(0xFF1F6F5C.toInt())
    focused.cornerRadius = dp(10).toFloat()
    focused.setStroke(dp(2), 0xFFFFFFFF.toInt())
    val normal = GradientDrawable()
    normal.setColor(0xFF2E2E36.toInt())
    normal.cornerRadius = dp(10).toFloat()
    val s = StateListDrawable()
    s.addState(intArrayOf(android.R.attr.state_focused), focused)
    s.addState(intArrayOf(), normal)
    return s
}

/** The microphone indicator on the listening screen: it pulses with the sound level. */
class MicView(context: Context) : View(context) {
    enum class Mode { IDLE, LISTENING, PROCESSING, OK, ERROR }

    var mode: Mode = Mode.IDLE
        set(value) {
            field = value
            invalidate()
        }

    private var level = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun setLevel(rms: Int) {
        val target = (Math.log(rms + 1.0) / Math.log(8000.0)).toFloat().coerceIn(0f, 1f)
        level = level * 0.6f + target * 0.4f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * 0.30f
        val color = when (mode) {
            Mode.LISTENING -> 0xFF1F9E7A
            Mode.PROCESSING -> 0xFF3D7CC9
            Mode.OK -> 0xFF2E9E4F
            Mode.ERROR -> 0xFFC94A3D
            Mode.IDLE -> 0xFF555B66
        }.toInt()

        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = 70
        canvas.drawCircle(cx, cy, base * (1f + 0.55f * level), paint)
        paint.color = color
        paint.alpha = 255
        canvas.drawCircle(cx, cy, base, paint)

        // Microphone drawing
        paint.color = 0xFFFFFFFF.toInt()
        val bw = base * 0.55f
        val bh = base * 0.95f
        rect.set(cx - bw / 2, cy - bh * 0.62f, cx + bw / 2, cy + bh * 0.18f)
        canvas.drawRoundRect(rect, bw / 2, bw / 2, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = base * 0.11f
        paint.strokeCap = Paint.Cap.ROUND
        rect.set(cx - bw * 0.85f, cy - bh * 0.42f, cx + bw * 0.85f, cy + bh * 0.40f)
        canvas.drawArc(rect, 20f, 140f, false, paint)
        canvas.drawLine(cx, cy + bh * 0.40f, cx, cy + bh * 0.68f, paint)
        canvas.drawLine(cx - bw * 0.45f, cy + bh * 0.68f, cx + bw * 0.45f, cy + bh * 0.68f, paint)

        if (mode == Mode.PROCESSING) {
            val r = base * 1.35f
            rect.set(cx - r, cy - r, cx + r, cy + r)
            paint.color = 0xFFFFFFFF.toInt()
            paint.strokeWidth = base * 0.09f
            val start = (SystemClock.uptimeMillis() % 1000L) / 1000f * 360f
            canvas.drawArc(rect, start, 100f, false, paint)
            postInvalidateOnAnimation()
        }
    }
}

/** A scroll area that scrolls if its content is large but never exceeds the given height. */
class MaxHeightScrollView(context: Context, private val maxHeightPx: Int) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST))
    }
}
