package com.voiceasistanttv.stt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

/** Weather icons: drawn with Canvas so they do not depend on a font (no emoji, low memory). */
class WeatherIconView(ctx: Context, private val kind: WeatherClient.Kind) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        c.save()
        c.translate((width - s) / 2f, (height - s) / 2f)
        c.scale(s, s)
        when (kind) {
            WeatherClient.Kind.SUN -> sun(c, 0.5f, 0.5f, 0.2f)
            WeatherClient.Kind.MOON -> moon(c, 0.5f, 0.5f, 0.3f)
            WeatherClient.Kind.PARTLY_DAY -> {
                sun(c, 0.36f, 0.36f, 0.15f)
                cloud(c, 0.2f, 0.36f, 0.72f, CLOUD_LIGHT)
            }
            WeatherClient.Kind.PARTLY_NIGHT -> {
                moon(c, 0.36f, 0.36f, 0.2f)
                cloud(c, 0.2f, 0.36f, 0.72f, CLOUD_LIGHT)
            }
            WeatherClient.Kind.CLOUD -> cloud(c, 0.08f, 0.24f, 0.84f, CLOUD_LIGHT)
            WeatherClient.Kind.FOG -> {
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = 0.07f
                p.color = FOG
                c.drawLine(0.18f, 0.28f, 0.82f, 0.28f, p)
                c.drawLine(0.10f, 0.44f, 0.74f, 0.44f, p)
                c.drawLine(0.22f, 0.60f, 0.90f, 0.60f, p)
                c.drawLine(0.14f, 0.76f, 0.70f, 0.76f, p)
            }
            WeatherClient.Kind.RAIN -> {
                cloud(c, 0.1f, 0.08f, 0.8f, CLOUD_GRAY)
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = 0.065f
                p.color = RAIN
                for (x in floatArrayOf(0.30f, 0.50f, 0.70f)) c.drawLine(x + 0.04f, 0.68f, x - 0.04f, 0.86f, p)
            }
            WeatherClient.Kind.SNOW -> {
                cloud(c, 0.1f, 0.08f, 0.8f, CLOUD_GRAY)
                p.style = Paint.Style.FILL
                p.color = SNOW
                c.drawCircle(0.30f, 0.76f, 0.045f, p)
                c.drawCircle(0.50f, 0.86f, 0.045f, p)
                c.drawCircle(0.70f, 0.76f, 0.045f, p)
            }
            WeatherClient.Kind.STORM -> {
                cloud(c, 0.1f, 0.06f, 0.8f, CLOUD_DARK)
                p.style = Paint.Style.FILL
                p.color = BOLT
                path.reset()
                path.moveTo(0.54f, 0.54f)
                path.lineTo(0.36f, 0.78f)
                path.lineTo(0.49f, 0.78f)
                path.lineTo(0.42f, 0.97f)
                path.lineTo(0.66f, 0.68f)
                path.lineTo(0.52f, 0.68f)
                path.lineTo(0.60f, 0.54f)
                path.close()
                c.drawPath(path, p)
            }
        }
        c.restore()
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = SUN
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.3f
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val ca = Math.cos(a).toFloat()
            val sa = Math.sin(a).toFloat()
            c.drawLine(cx + ca * r * 1.45f, cy + sa * r * 1.45f, cx + ca * r * 1.85f, cy + sa * r * 1.85f, p)
        }
    }

    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
        val a = Path()
        a.addCircle(cx, cy, r, Path.Direction.CW)
        val b = Path()
        b.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.82f, Path.Direction.CW)
        path.reset()
        path.op(a, b, Path.Op.DIFFERENCE)
        p.style = Paint.Style.FILL
        p.color = MOON
        c.drawPath(path, p)
    }

    /** x,y: top-left corner of the cloud; w: width (height ≈ 0.62·w). */
    private fun cloud(c: Canvas, x: Float, y: Float, w: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        c.drawCircle(x + 0.30f * w, y + 0.44f * w, 0.18f * w, p)
        c.drawCircle(x + 0.50f * w, y + 0.30f * w, 0.26f * w, p)
        c.drawCircle(x + 0.76f * w, y + 0.46f * w, 0.16f * w, p)
        rect.set(x + 0.10f * w, y + 0.40f * w, x + 0.92f * w, y + 0.62f * w)
        c.drawRoundRect(rect, 0.11f * w, 0.11f * w, p)
    }

    private companion object {
        const val SUN = 0xFFFFC857.toInt()
        const val MOON = 0xFFF2E6B8.toInt()
        const val CLOUD_LIGHT = 0xFFE3E7EF.toInt()
        const val CLOUD_GRAY = 0xFFB4BBCB.toInt()
        const val CLOUD_DARK = 0xFF8E97AB.toInt()
        const val RAIN = 0xFF5BB0F0.toInt()
        const val SNOW = 0xFFFFFFFF.toInt()
        const val BOLT = 0xFFFFD84D.toInt()
        const val FOG = 0xFF9AA3B5.toInt()
    }
}

/** Weather panel cards. kk: scale based on screen height (1.0 at 540 dp height). */
object WeatherUi {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GRAY = 0xFFB8BCC6.toInt()
    private const val DIM = 0xFF8A8F99.toInt()
    private const val RAIN_BLUE = 0xFF6DB8F2.toInt()

    private fun tv(ctx: Context, t: String, sp: Float, color: Int, bold: Boolean = false, lines: Int = 1): TextView {
        val v = TextView(ctx)
        v.text = t
        v.textSize = sp
        v.setTextColor(color)
        v.maxLines = lines
        v.ellipsize = TextUtils.TruncateAt.END
        if (bold) v.setTypeface(null, Typeface.BOLD)
        return v
    }

    private fun panelBg(ctx: Context, color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = ctx.dp(radiusDp).toFloat()
        return g
    }

    private fun deg(v: Int) = "$v°"

    fun dayName(date: String, index: Int): String {
        if (index == 0) return Lang.s(R.string.wx_today)
        if (index == 1) return Lang.s(R.string.wx_tomorrow)
        val p = date.split("-")
        if (p.size < 3) return date
        val cal = Calendar.getInstance()
        cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        return java.text.SimpleDateFormat("EEE", Lang.locale()).format(cal.time)
    }

    /** The big summary on top: the current state for today, the day's summary for tomorrow / the day after. */
    fun summary(ctx: Context, r: WeatherClient.Report, dayOffset: Int, kk: Float): View {
        val d = r.days.getOrNull(dayOffset) ?: r.days.firstOrNull()
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ctx.dp(18), ctx.dp(5), ctx.dp(22), ctx.dp(5))
        row.background = panelBg(ctx, 0xFF24242B.toInt(), 14)

        val today = dayOffset <= 0 || d == null
        val code = if (today) r.code else d!!.code
        val isDay = if (today) r.isDay else true
        val size = ctx.dp((74 * kk).toInt())
        row.addView(WeatherIconView(ctx, WeatherClient.kindOf(code, isDay)), LinearLayout.LayoutParams(size, size))

        val big = tv(ctx, deg(if (today) r.temp else d!!.max), 46f * kk.coerceAtLeast(0.9f), WHITE, true)
        big.setPadding(ctx.dp(14), 0, ctx.dp(22), 0)
        row.addView(big)

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val label = if (today) "" else (Lang.s(if (dayOffset == 1) R.string.wx_tomorrow else R.string.wx_day_after) + " · ")
        col.addView(tv(ctx, label + WeatherClient.describe(code), 21f, WHITE, true))
        if (today) {
            col.addView(tv(ctx, Lang.s(R.string.wx_now_details, deg(r.feels), Lang.pct(r.humidity), r.wind, Lang.windUnit()), 14.5f, GRAY).apply {
                setPadding(0, ctx.dp(2), 0, 0)
            })
            if (d != null) {
                col.addView(tv(ctx, Lang.s(R.string.wx_hi_lo_rain, deg(d.max), deg(d.min), Lang.pct(d.rain)), 14.5f, GRAY).apply {
                    setPadding(0, ctx.dp(1), 0, 0)
                })
            }
        } else if (d != null) {
            col.addView(tv(ctx, Lang.s(R.string.wx_hi_lo, deg(d.max), deg(d.min)), 14.5f, GRAY).apply {
                setPadding(0, ctx.dp(2), 0, 0)
            })
            col.addView(tv(ctx, Lang.s(R.string.wx_rain_chance, Lang.pct(d.rain)), 14.5f, GRAY).apply { setPadding(0, ctx.dp(1), 0, 0) })
        }
        if (d != null && d.sunrise.isNotBlank() && d.sunset.isNotBlank()) {
            col.addView(tv(ctx, Lang.s(R.string.wx_sun, d.sunrise, d.sunset), 13.5f, DIM).apply {
                setPadding(0, ctx.dp(1), 0, 0)
            })
        }
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    fun hourCard(ctx: Context, h: WeatherClient.Hour, first: Boolean, kk: Float): View {
        val w = ctx.dp((66 * kk).toInt())
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.setPadding(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4))
        c.background = panelBg(ctx, if (first) 0xFF1F4D43.toInt() else 0xFF24242B.toInt(), 10)
        c.addView(tv(ctx, h.label, 12.5f, if (first) WHITE else GRAY, first).apply { gravity = Gravity.CENTER })
        val s = ctx.dp((26 * kk).toInt())
        c.addView(
            WeatherIconView(ctx, WeatherClient.kindOf(h.code, h.isDay)),
            LinearLayout.LayoutParams(s, s).apply { setMargins(0, ctx.dp(2), 0, ctx.dp(2)) }
        )
        c.addView(tv(ctx, deg(h.temp), 15f, WHITE, true).apply { gravity = Gravity.CENTER })
        c.addView(
            tv(ctx, if (h.rain >= 10) Lang.pct(h.rain) else " ", 11.5f, RAIN_BLUE).apply { gravity = Gravity.CENTER }
        )
        c.layoutParams = LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        return c
    }

    fun dayCard(ctx: Context, d: WeatherClient.Day, index: Int, selected: Boolean, kk: Float): View {
        val w = ctx.dp((104 * kk).toInt())
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.setPadding(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4))
        c.background = panelBg(ctx, if (selected) 0xFF1F4D43.toInt() else 0xFF24242B.toInt(), 12)
        c.addView(tv(ctx, dayName(d.date, index), 14f, if (selected) WHITE else GRAY, true).apply { gravity = Gravity.CENTER })
        val s = ctx.dp((32 * kk).toInt())
        c.addView(
            WeatherIconView(ctx, WeatherClient.kindOf(d.code, true)),
            LinearLayout.LayoutParams(s, s).apply { setMargins(0, ctx.dp(2), 0, ctx.dp(2)) }
        )
        c.addView(tv(ctx, "${deg(d.max)}  ${deg(d.min)}", 15f, WHITE, true).apply { gravity = Gravity.CENTER })
        c.addView(
            tv(ctx, if (d.rain >= 10) Lang.pct(d.rain) else " ", 12f, RAIN_BLUE).apply { gravity = Gravity.CENTER }
        )
        c.layoutParams = LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        return c
    }
}
