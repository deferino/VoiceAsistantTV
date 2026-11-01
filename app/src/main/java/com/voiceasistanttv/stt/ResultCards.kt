package com.voiceasistanttv.stt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Küçük resimleri/afişleri arka planda indirip küçük bir önbellekte tutar (az RAM: RGB_565 + örnekleme). */
object ImageLoader {
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(3) { r -> Thread(r).apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    fun load(url: String?, view: ImageView, reqWidth: Int) {
        view.tag = url
        view.setImageDrawable(null)
        if (url.isNullOrBlank()) return
        cache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }
        pool.execute {
            val bmp = try {
                fetch(url, reqWidth)
            } catch (_: Exception) {
                null
            }
            if (bmp != null) {
                cache.put(url, bmp)
                main.post { if (view.tag == url) view.setImageBitmap(bmp) }
            }
        }
    }

    private fun fetch(url: String, reqWidth: Int): Bitmap? = NetLog.timed(url) { fetchImpl(url, reqWidth) }

    private fun fetchImpl(url: String, reqWidth: Int): Bitmap? {
        val c = URL(url).openConnection() as HttpURLConnection
        val bytes: ByteArray
        try {
            c.connectTimeout = 6000
            c.readTimeout = 6000
            if (c.responseCode !in 200..299) return null
            bytes = c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
        if (bytes.isEmpty() || bytes.size > 3_000_000) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqWidth) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}

/** Sığmayan (yarım kalacak) satırları hiç çizmeyen dikey sütun: kart metni yarım kesilmiş görünmesin. */
class FitColumn(ctx: Context) : LinearLayout(ctx) {
    override fun drawChild(canvas: android.graphics.Canvas, child: View, drawingTime: Long): Boolean {
        // İlk satır (başlık) hep çizilir; diğer satırlar tam sığmıyorsa (LinearLayout metni kalan yüksekliğe
        // kırptığı için yarım satır görünür) hiç çizilmez.
        if (indexOfChild(child) > 0) {
            val lay = (child as? TextView)?.layout
            val needed = if (lay != null) lay.height + child.totalPaddingTop + child.totalPaddingBottom else child.height
            if (child.bottom > height - paddingBottom || needed > child.height) return false
        }
        return super.drawChild(canvas, child, drawingTime)
    }
}

/** Sonuç kartları: YouTube videosu (yatay küçük resim) ve film/dizi (afiş + ayrıntılar). Tasarım dili ayar ekranıyla aynı. */
object ResultCards {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GRAY = 0xFFB8BCC6.toInt()
    private const val DIM = 0xFF8A8F99.toInt()

    private fun rounded(ctx: Context, v: ImageView, radiusDp: Int) {
        val bg = GradientDrawable()
        bg.setColor(0xFF1B1B21.toInt())
        bg.cornerRadius = ctx.dp(radiusDp).toFloat()
        v.background = bg
        v.clipToOutline = true
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, ctx.dp(radiusDp).toFloat())
            }
        }
        v.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private fun text(ctx: Context, t: String, sizeSp: Float, color: Int, lines: Int, bold: Boolean = false): TextView {
        val tv = TextView(ctx)
        tv.text = t
        tv.textSize = sizeSp
        tv.setTextColor(color)
        tv.maxLines = lines
        tv.ellipsize = TextUtils.TruncateAt.END
        if (bold) tv.setTypeface(null, Typeface.BOLD)
        return tv
    }

    private fun cardShell(ctx: Context, horizontal: Boolean, onPick: () -> Unit): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        c.setPadding(ctx.dp(6), ctx.dp(5), ctx.dp(6), ctx.dp(5))
        c.isFocusable = true
        c.isClickable = true
        c.background = ctx.focusDrawable()
        c.setOnClickListener { onPick() }
        return c
    }

    /** k: ekran yüksekliğine göre ölçek (küçük ekranlarda kartlar küçülür). */
    fun videoCard(ctx: Context, v: YouTubeSearch.Video, k: Float, onPick: () -> Unit): View {
        val w = ctx.dp((192 * k).toInt())
        val h = w * 9 / 16
        val card = cardShell(ctx, false, onPick)

        val frame = FrameLayout(ctx)
        val img = ImageView(ctx)
        rounded(ctx, img, 8)
        frame.addView(img, FrameLayout.LayoutParams(w, h))
        ImageLoader.load("https://i.ytimg.com/vi/${v.id}/mqdefault.jpg", img, w)
        if (v.duration.isNotBlank()) {
            val badge = text(ctx, v.duration, 12f, WHITE, 1, true)
            badge.setPadding(ctx.dp(6), ctx.dp(1), ctx.dp(6), ctx.dp(1))
            val bb = GradientDrawable()
            bb.setColor(0xCC000000.toInt())
            bb.cornerRadius = ctx.dp(4).toFloat()
            badge.background = bb
            val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.gravity = Gravity.BOTTOM or Gravity.END
            lp.setMargins(0, 0, ctx.dp(6), ctx.dp(6))
            frame.addView(badge, lp)
        }
        card.addView(frame, LinearLayout.LayoutParams(w, h))

        val t = text(ctx, v.title, 14.5f, WHITE, 2, true)
        t.setPadding(ctx.dp(2), ctx.dp(6), ctx.dp(2), 0)
        card.addView(t, LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (v.channel.isNotBlank()) {
            val c = text(ctx, v.channel, 12.5f, GRAY, 1)
            c.setPadding(ctx.dp(2), ctx.dp(2), ctx.dp(2), 0)
            card.addView(c, LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val meta = listOf(v.views, v.published).filter { it.isNotBlank() }.joinToString("  ·  ")
        if (meta.isNotBlank()) {
            val m = text(ctx, meta, 12f, DIM, 1)
            m.setPadding(ctx.dp(2), ctx.dp(1), ctx.dp(2), 0)
            card.addView(m, LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return card
    }

    fun titleCard(ctx: Context, item: MediaSearch.Item, k: Float, onPick: () -> Unit): View {
        val pw = ctx.dp((82 * k).toInt())
        val ph = pw * 3 / 2
        val tw = ctx.dp((190 * k).toInt())
        val card = cardShell(ctx, true, onPick)

        val img = ImageView(ctx)
        rounded(ctx, img, 6)
        card.addView(img, LinearLayout.LayoutParams(pw, ph))
        ImageLoader.load(item.poster, img, pw)

        val col = FitColumn(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ctx.dp(10), ctx.dp(2), ctx.dp(4), 0)
        col.addView(text(ctx, item.title, 15.5f, WHITE, 2, true))
        val line = listOf(item.year, item.typeLabel).filter { it.isNotBlank() }.joinToString("  ·  ")
        if (line.isNotBlank()) col.addView(text(ctx, line, 12.5f, GRAY, 1).apply { setPadding(0, ctx.dp(3), 0, 0) })
        if (item.rating.isNotBlank()) {
            col.addView(text(ctx, item.rating, 12.5f, 0xFF4FC3A1.toInt(), 1).apply { setPadding(0, ctx.dp(1), 0, 0) })
        }
        if (item.genres.isNotBlank()) {
            col.addView(text(ctx, item.genres, 12f, DIM, 1).apply { setPadding(0, ctx.dp(1), 0, 0) })
        }
        if (item.overview.isNotBlank()) {
            col.addView(text(ctx, item.overview, 12f, GRAY, 2).apply { setPadding(0, ctx.dp(4), 0, 0) })
        }
        card.addView(col, LinearLayout.LayoutParams(tw, ph))
        return card
    }
}
