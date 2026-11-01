package com.voiceasistanttv.stt

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Listening screen. It is opened in three ways:
 *  - Assistant key / ASSIST request: listens, then writes the text into the focused field (or copies it to the clipboard),
 *  - RecognizerIntent.ACTION_RECOGNIZE_SPEECH: returns the result to the calling app,
 *  - Test from the settings screen: only shows the result.
 * Remote: OK = stop speaking and transcribe, Back = cancel.
 */
class AssistActivity : Activity(), Transcriber.Listener {
    private lateinit var mic: MicView
    private lateinit var title: TextView
    private lateinit var sub: TextView
    private lateinit var choices: LinearLayout
    private lateinit var card: LinearLayout
    private lateinit var cardLp: FrameLayout.LayoutParams
    private var autoFocused = false
    private var choosing = false
    private var transcriber: Transcriber? = null
    private var returnMode = false
    private var testMode = false
    private var done = false
    private var waitingPermission = false
    private var peak = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.init(this)
        Config.importFile(this)
        Log.i("VoiceTV", "AssistActivity opened: ${intent?.action}")
        returnMode = intent?.action == RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        testMode = intent?.getBooleanExtra(EXTRA_TEST, false) == true
        setContentView(buildUi())
        // Debug builds only: test without a microphone (adb: am start -n .../.AssistActivity --es debug_text "search")
        val debugText = if (isDebuggable()) intent?.getStringExtra(EXTRA_DEBUG_TEXT) else null
        // While the user speaks, warm up connections for the next requests (on the assistant path, the search servers too).
        Prewarm.forAssist(withSearch = !returnMode && !testMode)
        if (!debugText.isNullOrBlank()) {
            // debug_delay (ms): simulates the speaking time (used to measure the connection warm-up)
            val delay = intent?.getIntExtra("debug_delay", 0)?.toLong() ?: 0L
            handler.postDelayed({ onResult(debugText) }, delay)
            return
        }
        beginWhenAllowed()
    }

    private fun isDebuggable(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // The key was pressed again: stop speaking
        if (done) finishAssist() else transcriber?.stopNow()
    }

    private fun buildUi(): View {
        val root = FrameLayout(this)
        root.setBackgroundColor(0xB3000000.toInt())

        card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.setPadding(dp(32), dp(24), dp(32), dp(28))
        val bg = GradientDrawable()
        bg.setColor(0xF2202026.toInt())
        bg.cornerRadius = dp(28).toFloat()
        card.background = bg

        mic = MicView(this)
        card.addView(mic, LinearLayout.LayoutParams(dp(140), dp(140)))

        title = TextView(this)
        title.textSize = 28f
        title.setTextColor(0xFFFFFFFF.toInt())
        title.gravity = Gravity.CENTER
        title.maxLines = 5
        card.addView(
            title,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        )

        sub = TextView(this)
        sub.textSize = 18f
        sub.setTextColor(0xFFB8BCC6.toInt())
        sub.gravity = Gravity.CENTER
        card.addView(
            sub,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        )

        choices = LinearLayout(this)
        choices.orientation = LinearLayout.HORIZONTAL
        choices.visibility = View.GONE
        card.addView(
            choices,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(14) }
        )

        val lp = FrameLayout.LayoutParams(
            dp(720), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        lp.bottomMargin = dp(56)
        cardLp = lp
        root.addView(card, lp)
        setStatus(MicView.Mode.IDLE, Lang.s(R.string.st_preparing), "")
        return root
    }

    private fun setStatus(mode: MicView.Mode, t: String, s: String) {
        mic.mode = mode
        title.text = t
        sub.text = s
    }

    private fun beginWhenAllowed() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            begin()
        } else {
            waitingPermission = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        waitingPermission = false
        if (requestCode == REQ_MIC && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) begin() else onError(Transcriber.Code.NO_PERMISSION, Lang.s(R.string.err_perm_denied))
    }

    private fun begin() {
        val t = Transcriber(this)
        transcriber = t
        t.start(this)
    }

    // ---- Transcriber.Listener ----

    override fun onReady() {
        setStatus(MicView.Mode.LISTENING, Lang.s(R.string.st_listening), Lang.s(R.string.st_listening_hint))
    }

    override fun onLevel(rms: Int, speaking: Boolean) {
        mic.setLevel(rms)
        if (rms > peak) peak = rms
        if (testMode) sub.text = Lang.s(R.string.test_level, rms, peak)
    }

    override fun onSpeechStart() {
        title.text = Lang.s(R.string.st_hearing)
    }

    override fun onProcessing() {
        setStatus(MicView.Mode.PROCESSING, Lang.s(R.string.st_transcribing), "")
    }

    override fun onResult(text: String) {
        done = true
        setStatus(MicView.Mode.OK, text, "")
        when {
            returnMode -> {
                val data = Intent()
                data.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(text))
                data.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, floatArrayOf(1f))
                setResult(RESULT_OK, data)
                handler.postDelayed({ finishAssist() }, 400L)
            }
            testMode -> sub.text = Lang.s(R.string.test_done)
            else -> routeResult(text)
        }
    }

    override fun onError(code: Transcriber.Code, message: String) {
        done = true
        setStatus(MicView.Mode.ERROR, Lang.s(R.string.st_failed), message)
        if (returnMode) setResult(RESULT_CANCELED)
        if (!testMode) handler.postDelayed({ finishAssist() }, 3500L)
    }

    // ---- Routing the result: SmartTube / Nuvio / write into the focused field ----

    private fun routeResult(text: String) {
        // Weather question ("weather in Istanbul", "how is the weather tomorrow in Ankara") -> weather panel only.
        // Left alone in "write to field only" mode; if it is not a weather question, the normal search continues.
        if (Config.searchTarget != Config.TARGET_WRITE) {
            val wq = WeatherQuery.parse(text)
            if (wq != null) {
                showWeather(text, wq)
                return
            }
        }
        routeSearch(text)
    }

    private fun routeSearch(text: String) {
        when (Config.searchTarget) {
            Config.TARGET_SMARTTUBE -> openSmartTube(text)
            Config.TARGET_NUVIO -> showResults(text, youtube = false, nuvio = true)
            Config.TARGET_WRITE -> writeText(text)
            else -> showResults(text, youtube = true, nuvio = true)
        }
    }

    private fun writeText(text: String) {
        Config.lastTarget = Config.TARGET_WRITE
        TextDelivery.deliver(this, text)
        handler.postDelayed({ finishAssist() }, 700L)
    }

    /** Status card: small, bottom center. */
    private fun showStatusAgain() {
        choosing = false
        choices.visibility = View.GONE
        mic.visibility = View.VISIBLE
        sub.visibility = View.VISIBLE
        title.textSize = 28f
        title.maxLines = 5
        title.ellipsize = null
        sub.textSize = 18f
        card.setPadding(dp(32), dp(24), dp(32), dp(28))
        cardLp.width = dp(720)
        cardLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        cardLp.bottomMargin = dp(56)
        card.layoutParams = cardLp
    }

    /** Results panel: wide, at the bottom of the screen (like Google Assistant results). */
    private fun showPanel() {
        mic.visibility = View.GONE
        sub.visibility = View.VISIBLE
        title.textSize = 20f
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        sub.textSize = 14f
        card.setPadding(dp(32), dp(14), dp(32), dp(16))
        cardLp.width = minOf(dp(1240), resources.displayMetrics.widthPixels - dp(64))
        cardLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        cardLp.bottomMargin = dp(28)
        card.layoutParams = cardLp
    }

    /** Card scale based on screen height (cards shrink on small/dense screens). */
    private fun cardScale(): Float {
        val dm = resources.displayMetrics
        return ((dm.heightPixels / dm.density) / 720f).coerceIn(0.72f, 1.15f)
    }

    private class Section(val header: TextView, val row: LinearLayout)

    private var ytSection: Section? = null
    private var nuvSection: Section? = null
    private var pendingSections = 0

    private fun addHeader(parent: LinearLayout, headerText: String): TextView {
        val h = TextView(this)
        h.text = headerText.uppercase()
        h.textSize = 14f
        h.setTextColor(0xFF4FC3A1.toInt())
        h.setPadding(dp(8), dp(3), 0, dp(1))
        parent.addView(h)
        return h
    }

    private fun makeSection(parent: LinearLayout, headerText: String): Section {
        val h = addHeader(parent, headerText)
        val scroller = HorizontalScrollView(this)
        scroller.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(2), dp(2), dp(2), dp(2))
        scroller.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        parent.addView(
            scroller,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        return Section(h, row)
    }

    private fun setNote(sec: Section, text: String) {
        sec.row.removeAllViews()
        val t = TextView(this)
        t.text = text
        t.textSize = 16f
        t.setTextColor(0xFFB8BCC6.toInt())
        t.setPadding(dp(10), dp(14), dp(10), dp(14))
        sec.row.addView(t)
    }

    private var resultsScroll: MaxHeightScrollView? = null

    private fun addCard(sec: Section, v: View) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(4), dp(3), dp(4), dp(3))
        // When focus moves to this row, show the row header too (vertical scrolling must not clip the header)
        v.setOnFocusChangeListener { _, has ->
            if (has) {
                val s = resultsScroll
                if (s != null) s.post { s.smoothScrollTo(0, (sec.header.top - dp(2)).coerceAtLeast(0)) }
            }
        }
        sec.row.addView(v, lp)
    }

    private fun button(label: String, onPick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.textSize = 15f
        b.setTextColor(0xFFFFFFFF.toInt())
        b.gravity = Gravity.CENTER
        b.maxLines = 1
        b.setPadding(dp(18), dp(9), dp(18), dp(9))
        b.isFocusable = true
        b.isClickable = true
        b.background = focusDrawable()
        b.setOnClickListener { onPick() }
        return b
    }

    private fun showResults(text: String, youtube: Boolean, nuvio: Boolean) {
        if (nuvio && !youtube && AppLauncher.nuvioPackage(this) == null) {
            showStatusAgain()
            setStatus(MicView.Mode.ERROR, Lang.s(R.string.st_nuvio_missing), Lang.s(R.string.st_nuvio_missing_hint))
            handler.postDelayed({ finishAssist() }, 3500L)
            return
        }
        showPanel()
        choosing = true
        autoFocused = false
        title.text = "“$text”"
        sub.text = Lang.s(R.string.results_hint)
        choices.removeAllViews()
        choices.orientation = LinearLayout.VERTICAL
        choices.visibility = View.VISIBLE

        val dm = resources.displayMetrics
        val sections = LinearLayout(this)
        sections.orientation = LinearLayout.VERTICAL
        val scroll = MaxHeightScrollView(this, dm.heightPixels - dp(200))
        scroll.isVerticalScrollBarEnabled = false
        resultsScroll = scroll
        scroll.addView(sections, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        choices.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        ytSection = null
        nuvSection = null
        pendingSections = 0
        if (youtube) {
            val sec = makeSection(sections, Lang.s(R.string.sec_smarttube))
            setNote(sec, Lang.s(R.string.searching))
            ytSection = sec
            pendingSections++
        }
        if (nuvio) {
            val sec = makeSection(sections, Lang.s(R.string.sec_nuvio))
            setNote(sec, Lang.s(R.string.searching))
            nuvSection = sec
            pendingSections++
        }

        val footer = LinearLayout(this)
        footer.orientation = LinearLayout.HORIZONTAL
        footer.gravity = Gravity.CENTER
        val flp = { LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, dp(8), 0) } }
        if (youtube) footer.addView(button(Lang.s(R.string.btn_all_in_smarttube)) { openSmartTube(text) }, flp())
        footer.addView(button(Lang.s(R.string.btn_write_text)) { writeText(text) }, flp())
        choices.addView(
            footer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        )
        footerView = footer

        if (youtube) {
            val key = Config.youtubeKey
            Thread {
                val out = YouTubeSearch.search(text, key)
                runOnUiThread { if (!isFinishing && !isDestroyed) fillYouTube(out) }
            }.start()
        }
        if (nuvio) {
            val key = Config.tmdbKey
            Thread {
                val out = MediaSearch.search(text, key)
                runOnUiThread { if (!isFinishing && !isDestroyed) fillNuvio(out) }
            }.start()
        }
        // If one list is very slow, still focus somewhere
        handler.postDelayed({ if (!autoFocused) focusPreferred() }, 4000L)
    }

    // ---- Weather panel (only when a weather question was asked) ----

    private fun footerLp() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(dp(8), 0, dp(8), 0) }

    private fun addPlain(sec: Section, v: View) {
        val lp = (v.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(4), dp(3), dp(4), dp(3))
        sec.row.addView(v, lp)
    }

    private fun showWeather(text: String, q: WeatherQuery.Query) {
        setStatus(MicView.Mode.PROCESSING, text, Lang.s(R.string.st_weather_loading))
        val city = q.city ?: Config.weatherCity
        Thread {
            val out = WeatherClient.fetch(city, q.dayOffset)
            runOnUiThread { if (!isFinishing && !isDestroyed) renderWeather(text, q, out) }
        }.start()
    }

    private fun renderWeather(text: String, q: WeatherQuery.Query, out: WeatherClient.Outcome) {
        val rep = out.report
        if (rep == null) {
            if (out.notFound && q.city != null) {
                // Not a place name (or misheard): behave like a normal search
                routeSearch(text)
                return
            }
            val msg = if (out.notFound) Lang.s(R.string.weather_city_not_found, Config.weatherCity)
            else (out.error ?: Lang.s(R.string.err_unknown))
            setStatus(MicView.Mode.ERROR, Lang.s(R.string.st_weather_failed), msg)
            handler.postDelayed({ finishAssist() }, 4000L)
            return
        }

        showPanel()
        sub.visibility = View.GONE
        // The weather panel is taller: trim the margins a little so it fits the screen
        card.setPadding(dp(32), dp(10), dp(32), dp(12))
        cardLp.bottomMargin = dp(18)
        card.layoutParams = cardLp
        choosing = true
        autoFocused = true
        title.text = "“$text”"
        choices.removeAllViews()
        choices.orientation = LinearLayout.VERTICAL
        choices.visibility = View.VISIBLE

        val kk = cardScale() / 0.75f
        val sections = LinearLayout(this)
        sections.orientation = LinearLayout.VERTICAL
        val scroll = MaxHeightScrollView(this, resources.displayMetrics.heightPixels - dp(150))
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(sections, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        choices.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addHeader(sections, rep.place.label().uppercase(Lang.locale()))
        sections.addView(
            WeatherUi.summary(this, rep, q.dayOffset, kk),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(2), dp(2), dp(2), dp(4)) }
        )
        if (rep.hours.isNotEmpty()) {
            val sec = makeSection(sections, if (q.dayOffset <= 0) Lang.s(R.string.sec_hourly) else Lang.s(R.string.sec_through_day))
            rep.hours.forEachIndexed { i, h -> addPlain(sec, WeatherUi.hourCard(this, h, i == 0 && q.dayOffset <= 0, kk)) }
        }
        val dsec = makeSection(sections, Lang.s(R.string.sec_7day))
        rep.days.forEachIndexed { i, d -> addPlain(dsec, WeatherUi.dayCard(this, d, i, i == q.dayOffset, kk)) }

        val footer = LinearLayout(this)
        footer.orientation = LinearLayout.HORIZONTAL
        footer.gravity = Gravity.CENTER
        footer.addView(button(Lang.s(R.string.btn_close)) { finishAssist() }, footerLp())
        footer.addView(button(Lang.s(R.string.btn_search_yt_nuvio)) { routeSearch(text) }, footerLp())
        // Open-Meteo terms of use: the source must be credited
        footer.addView(
            TextView(this).apply {
                this.text = Lang.s(R.string.weather_credit)
                textSize = 12f
                setTextColor(0xFF8A8F99.toInt())
                setPadding(dp(24), 0, 0, 0)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        choices.addView(
            footer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        )
        footerView = footer
        footer.getChildAt(0)?.requestFocus()
    }

    private var footerView: LinearLayout? = null

    private fun fillYouTube(out: YouTubeSearch.Outcome) {
        val sec = ytSection ?: return
        val k = cardScale()
        sec.row.removeAllViews()
        if (out.error != null) {
            setNote(sec, out.error + Lang.s(R.string.note_try_smarttube))
        } else if (out.videos.isEmpty()) {
            setNote(sec, Lang.s(R.string.note_no_video))
        } else {
            for (v in out.videos) addCard(sec, ResultCards.videoCard(this, v, k) { openVideo(v) })
        }
        sectionDone()
    }

    private fun fillNuvio(out: MediaSearch.Outcome) {
        val sec = nuvSection ?: return
        val k = cardScale()
        sec.row.removeAllViews()
        if (AppLauncher.nuvioPackage(this) == null) {
            setNote(sec, Lang.s(R.string.note_nuvio_not_installed))
        } else if (out.error != null) {
            setNote(sec, out.error)
        } else if (out.items.isEmpty()) {
            setNote(sec, Lang.s(R.string.note_no_media))
        } else {
            for (item in out.items) addCard(sec, ResultCards.titleCard(this, item, k) { openNuvioItem(item) })
        }
        sectionDone()
    }

    private fun sectionDone() {
        pendingSections--
        if (pendingSections <= 0 && !autoFocused) focusPreferred()
    }

    private fun firstCard(sec: Section?): View? {
        if (sec == null) return null
        for (i in 0 until sec.row.childCount) {
            val c = sec.row.getChildAt(i)
            if (c.isFocusable) return c
        }
        return null
    }

    /** Focus the first result of the last used list, else the other list, else the bottom buttons. */
    private fun focusPreferred() {
        autoFocused = true
        val order = if (Config.lastTarget == Config.TARGET_NUVIO) listOf(nuvSection, ytSection) else listOf(ytSection, nuvSection)
        for (sec in order) {
            firstCard(sec)?.let {
                it.requestFocus()
                return
            }
        }
        footerView?.getChildAt(0)?.requestFocus()
    }

    private fun openSmartTube(text: String) {
        Config.lastTarget = Config.TARGET_SMARTTUBE
        if (AppLauncher.searchSmartTube(this, text)) {
            finishAssist()
        } else {
            showStatusAgain()
            setStatus(MicView.Mode.ERROR, Lang.s(R.string.st_smarttube_missing), Lang.s(R.string.st_smarttube_hint))
            handler.postDelayed({ finishAssist() }, 3500L)
        }
    }

    private fun openVideo(v: YouTubeSearch.Video) {
        Config.lastTarget = Config.TARGET_SMARTTUBE
        if (AppLauncher.openSmartTubeVideo(this, v.id)) {
            finishAssist()
        } else {
            showStatusAgain()
            setStatus(MicView.Mode.ERROR, Lang.s(R.string.st_smarttube_open_failed), Lang.s(R.string.st_smarttube_hint))
            handler.postDelayed({ finishAssist() }, 3500L)
        }
    }

    private fun openNuvioItem(item: MediaSearch.Item) {
        Config.lastTarget = Config.TARGET_NUVIO
        showStatusAgain()
        setStatus(MicView.Mode.PROCESSING, item.title, Lang.s(R.string.st_nuvio_opening))
        val key = Config.tmdbKey
        Thread {
            var imdb = item.imdbId
            if (imdb.isBlank() && item.tmdbId > 0L) imdb = MediaSearch.resolveImdb(item, key) ?: ""
            val ok = if (imdb.isNotBlank()) AppLauncher.openNuvioMeta(this, item.type, imdb)
            else AppLauncher.openNuvioTmdb(this, if (item.type == "movie") "movie" else "series", item.tmdbId)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ok) {
                    finishAssist()
                } else {
                    setStatus(MicView.Mode.ERROR, Lang.s(R.string.st_nuvio_open_failed), Lang.s(R.string.st_nuvio_open_hint))
                    handler.postDelayed({ finishAssist() }, 3500L)
                }
            }
        }.start()
    }

    // ---- Remote control ----

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (choosing) {
            when (keyCode) {
                KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_SEARCH,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (event == null || event.repeatCount == 0) {
                        val f = currentFocus
                        if (f != null) f.performClick() else choices.getChildAt(0)?.performClick()
                    }
                    return true
                }
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_SEARCH -> {
                if (event == null || event.repeatCount == 0) {
                    if (done) finishAssist() else transcriber?.stopNow()
                }
                return true
            }
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                cancelAndFinish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun cancelAndFinish() {
        transcriber?.cancel()
        if (returnMode && !done) setResult(RESULT_CANCELED)
        finishAssist()
    }

    private fun finishAssist() {
        if (isFinishing) return
        AssistSession.close()
        finishAndRemoveTask()
    }

    override fun onStop() {
        super.onStop()
        // If the user moved elsewhere, stop listening (except while the permission dialog is open)
        if (!isFinishing && !waitingPermission) {
            transcriber?.cancel()
            finishAssist()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        transcriber?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TEST = "test"
        const val EXTRA_DEBUG_TEXT = "debug_text"
        private const val REQ_MIC = 1
    }
}
