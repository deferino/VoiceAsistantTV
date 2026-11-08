package com.voiceasistanttv.stt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Settings screen. Designed to be used only with the remote's D-pad + OK:
 * every row is focusable and the focused row is highlighted with a clear frame.
 */
class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private val rows = ArrayList<View>()
    private var focusIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.init(this)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(0xFF121216.toInt())
        scroll.isFillViewport = true
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(56), dp(28), dp(56), dp(40))
        scroll.addView(
            list,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        Config.importFile(this)
        render()
    }

    // ------------------------------------------------------------------ UI

    private fun render() {
        list.removeAllViews()
        rows.clear()

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
        title(Lang.s(R.string.app_title) + "  $version")
        note(Lang.s(R.string.main_note))

        header(Lang.s(R.string.hdr_general))
        row(Lang.s(R.string.row_ui_language), uiLanguageLabel()) { pickUiLanguage() }

        header(Lang.s(R.string.hdr_status))
        row(Lang.s(R.string.row_mic_perm), if (micGranted()) Lang.s(R.string.perm_granted) else Lang.s(R.string.perm_not_granted)) { requestMic() }
        row(Lang.s(R.string.row_assist_key), assistantStatus()) { showAdbHelp() }
        row(Lang.s(R.string.row_recognizer), recognizerStatus()) { showAdbHelp() }
        row(Lang.s(R.string.row_a11y), a11yStatus()) { openAccessibility() }

        header("API")
        row(Lang.s(R.string.row_provider), Config.preset(Config.provider).label) { pickProvider() }
        row(Lang.s(R.string.row_api_url), Config.baseUrl) {
            askText(Lang.s(R.string.dlg_api_url), Config.baseUrl, false) { Config.baseUrl = it }
        }
        row(Lang.s(R.string.row_api_key), maskKey(Config.apiKey)) {
            askText(Lang.s(R.string.row_api_key), Config.apiKey, true) { Config.apiKey = it }
        }
        row("Model", Config.model) { pickModel() }
        row(Lang.s(R.string.row_stt_language), languageLabel()) { pickLanguage() }
        row(Lang.s(R.string.row_prompt), Config.prompt.ifBlank { Lang.s(R.string.none_lower) }) {
            askText(Lang.s(R.string.dlg_prompt), Config.prompt, false) { Config.prompt = it }
        }
        row(Lang.s(R.string.row_test_conn), Lang.s(R.string.row_test_conn_sub)) { testConnection() }

        header(Lang.s(R.string.hdr_listening))
        row(Lang.s(R.string.row_silence), Lang.s(R.string.fmt_seconds, "${Config.silenceMs / 1000.0}")) { cycleSilence() }
        row(Lang.s(R.string.row_max_rec), Lang.s(R.string.fmt_seconds, "${Config.maxSeconds}")) { cycleMax() }
        row(Lang.s(R.string.row_sensitivity), sensitivityLabel()) { pickSensitivity() }
        row(Lang.s(R.string.row_source), sourceLabel(Config.audioSource)) { pickSource() }
        row(Lang.s(R.string.row_mic_device), deviceLabel()) { pickDevice() }

        header(Lang.s(R.string.hdr_music))
        row(Lang.s(R.string.row_music_app), musicStatus()) { pickMusicApp() }
        row(
            Lang.s(R.string.row_music_screen),
            Lang.s(if (Config.spotifyBackground) R.string.music_screen_bg else R.string.music_screen_show)
        ) {
            choose(
                Lang.s(R.string.dlg_music_screen),
                listOf(Lang.s(R.string.music_screen_bg), Lang.s(R.string.music_screen_show)),
                if (Config.spotifyBackground) 0 else 1
            ) { Config.spotifyBackground = it == 0 }
        }
        row(
            Lang.s(R.string.row_bitchord_screen),
            Lang.s(if (Config.bitchordBackground) R.string.bitchord_screen_bg else R.string.bitchord_screen_show)
        ) {
            choose(
                Lang.s(R.string.dlg_bitchord_screen),
                listOf(Lang.s(R.string.bitchord_screen_bg), Lang.s(R.string.bitchord_screen_show)),
                if (Config.bitchordBackground) 0 else 1
            ) { Config.bitchordBackground = it == 0 }
        }
        row(
            Lang.s(R.string.row_spotify_id),
            if (Config.spotifyId.isBlank()) Lang.s(R.string.spotify_id_none) else maskKey(Config.spotifyId)
        ) {
            askText(Lang.s(R.string.dlg_spotify_id), Config.spotifyId, true) { Config.spotifyId = it }
        }
        row(Lang.s(R.string.row_spotify_secret), maskKey(Config.spotifySecret)) {
            askText(Lang.s(R.string.dlg_spotify_secret), Config.spotifySecret, true) { Config.spotifySecret = it }
        }
        row(
            Lang.s(R.string.row_musiclink),
            if (Config.musicLinkKey.isBlank()) Lang.s(R.string.musiclink_none) else maskKey(Config.musicLinkKey)
        ) {
            askText(Lang.s(R.string.dlg_musiclink), Config.musicLinkKey, true) { Config.musicLinkKey = it }
        }

        header(Lang.s(R.string.hdr_apps))
        row(Lang.s(R.string.row_apps), appsStatus()) { pickApps() }

        header(Lang.s(R.string.hdr_target))
        row(Lang.s(R.string.row_target), targetLabel(Config.searchTarget)) { pickTarget() }
        row(Lang.s(R.string.row_smarttube_pkg), smartTubeStatus()) { pickSmartTube() }
        row(Lang.s(R.string.row_smarttube_test), Lang.s(R.string.row_smarttube_test_sub)) { testSmartTube() }
        row(Lang.s(R.string.row_nuvio_pkg), nuvioStatus()) { pickNuvio() }
        row(Lang.s(R.string.row_nuvio_open), Lang.s(R.string.row_nuvio_open_sub)) { openNuvio() }
        row(
            Lang.s(R.string.row_tmdb),
            if (Config.tmdbKey.isBlank()) Lang.s(R.string.tmdb_none) else maskKey(Config.tmdbKey)
        ) {
            askText(Lang.s(R.string.dlg_tmdb), Config.tmdbKey, true) { Config.tmdbKey = it }
        }
        row(Lang.s(R.string.row_media_test), Lang.s(R.string.row_media_test_sub)) { testMediaSearch() }
        row(
            Lang.s(R.string.row_yt_key),
            if (Config.youtubeKey.isBlank()) Lang.s(R.string.yt_key_none) else maskKey(Config.youtubeKey)
        ) {
            askText(Lang.s(R.string.dlg_yt_key), Config.youtubeKey, true) { Config.youtubeKey = it }
        }
        row(Lang.s(R.string.row_yt_test), Lang.s(R.string.row_yt_test_sub)) { testYouTubeSearch() }
        row(
            Lang.s(R.string.row_weather_city),
            Lang.s(R.string.row_weather_city_sub, Config.weatherCity)
        ) {
            askText(Lang.s(R.string.dlg_weather_city), Config.weatherCity, false) { Config.weatherCity = it }
        }
        row(Lang.s(R.string.row_weather_test), Lang.s(R.string.row_weather_test_sub, Config.weatherCity)) { testWeather() }

        header(Lang.s(R.string.hdr_text))
        row(
            Lang.s(R.string.row_insert_mode),
            if (Config.insertMode == Config.INSERT_REPLACE) Lang.s(R.string.insert_replace) else Lang.s(R.string.insert_append)
        ) {
            Config.insertMode =
                if (Config.insertMode == Config.INSERT_REPLACE) Config.INSERT_APPEND else Config.INSERT_REPLACE
            render()
        }
        row(
            Lang.s(R.string.row_autosubmit),
            if (Config.autoSubmit) Lang.s(R.string.autosubmit_on) else Lang.s(R.string.off)
        ) {
            Config.autoSubmit = !Config.autoSubmit
            render()
        }

        header(Lang.s(R.string.hdr_diag))
        row(Lang.s(R.string.row_mic_test), Lang.s(R.string.row_mic_test_sub)) {
            startActivity(Intent(this, AssistActivity::class.java).putExtra(AssistActivity.EXTRA_TEST, true))
        }
        row(Lang.s(R.string.row_key_test), Lang.s(R.string.row_key_test_sub)) {
            startActivity(Intent(this, KeyTestActivity::class.java))
        }
        row(Lang.s(R.string.row_list_devices), Lang.s(R.string.row_list_devices_sub)) { showDevices() }
        row(Lang.s(R.string.row_adb), Lang.s(R.string.row_adb_sub)) { showAdbHelp() }

        val idx = focusIndex.coerceIn(0, maxOf(0, rows.size - 1))
        rows.getOrNull(idx)?.requestFocus()
    }

    private fun title(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = 30f
        tv.setTextColor(0xFFFFFFFF.toInt())
        list.addView(tv)
    }

    private fun note(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = 16f
        tv.setTextColor(0xFF9AA0AA.toInt())
        tv.setPadding(0, dp(4), 0, dp(8))
        list.addView(tv)
    }

    private fun header(t: String) {
        val tv = TextView(this)
        tv.text = t.uppercase(Lang.locale())
        tv.textSize = 15f
        tv.setTextColor(0xFF4FC3A1.toInt())
        tv.setPadding(dp(4), dp(18), 0, dp(4))
        list.addView(tv)
    }

    private fun focusBackground(): StateListDrawable {
        val focused = GradientDrawable()
        focused.setColor(0xFF1F6F5C.toInt())
        focused.cornerRadius = dp(10).toFloat()
        focused.setStroke(dp(2), 0xFFFFFFFF.toInt())
        val normal = GradientDrawable()
        normal.setColor(0xFF24242B.toInt())
        normal.cornerRadius = dp(10).toFloat()
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_focused), focused)
        s.addState(intArrayOf(), normal)
        return s
    }

    private fun row(titleText: String, value: String, onClick: () -> Unit) {
        val index = rows.size
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.isFocusable = true
        r.isClickable = true
        r.setPadding(dp(20), dp(12), dp(20), dp(12))
        r.background = focusBackground()
        r.setOnClickListener { onClick() }
        r.setOnFocusChangeListener { _, has -> if (has) focusIndex = index }

        val t = TextView(this)
        t.text = titleText
        t.textSize = 20f
        t.setTextColor(0xFFFFFFFF.toInt())
        r.addView(t)

        val v = TextView(this)
        v.text = value
        v.textSize = 15f
        v.setTextColor(0xFFB8BCC6.toInt())
        r.addView(v)

        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, dp(4), 0, dp(4))
        list.addView(r, lp)
        rows.add(r)
    }

    // ------------------------------------------------------------------ dialogs

    private fun builder(): AlertDialog.Builder =
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)

    private fun askText(title: String, initial: String, secret: Boolean, onOk: (String) -> Unit) {
        val et = EditText(this)
        et.setText(initial)
        et.setSingleLine()
        et.inputType = InputType.TYPE_CLASS_TEXT or
                (if (secret) InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                else InputType.TYPE_TEXT_VARIATION_URI)
        et.setSelection(et.text.length)
        val box = FrameLayout(this)
        box.setPadding(dp(24), dp(8), dp(24), dp(8))
        box.addView(et, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val dlg = builder()
            .setTitle(title)
            .setView(box)
            .setPositiveButton(Lang.s(R.string.btn_save)) { _, _ ->
                onOk(et.text.toString().trim())
                render()
            }
            .setNegativeButton(Lang.s(R.string.btn_cancel), null)
            .create()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }

    private fun choose(title: String, items: List<String>, selected: Int, onPick: (Int) -> Unit) {
        builder()
            .setTitle(title)
            .setSingleChoiceItems(items.toTypedArray(), selected) { d, which ->
                d.dismiss()
                onPick(which)
                render()
            }
            .setNegativeButton(Lang.s(R.string.btn_cancel), null)
            .show()
    }

    private fun message(title: String, text: String) {
        builder().setTitle(title).setMessage(text).setPositiveButton(Lang.s(R.string.btn_ok), null).show()
    }

    // ------------------------------------------------------------------ state

    private val targets get() = listOf(
        Config.TARGET_BOTH to Lang.s(R.string.target_both),
        Config.TARGET_SMARTTUBE to Lang.s(R.string.target_smarttube),
        Config.TARGET_NUVIO to Lang.s(R.string.target_nuvio),
        Config.TARGET_WRITE to Lang.s(R.string.target_write)
    )

    private fun targetLabel(id: String): String = targets.firstOrNull { it.first == id }?.second ?: id

    private fun pickTarget() {
        val sel = targets.indexOfFirst { it.first == Config.searchTarget }.coerceAtLeast(0)
        choose(Lang.s(R.string.dlg_target), targets.map { it.second }, sel) { Config.searchTarget = targets[it].first }
    }

    /** Subtitle of the package-selection row: automatic or which package, installed or not. */
    private fun pkgStatus(chosen: String, choices: List<AppLauncher.AppChoice>, resolved: String?): String =
        if (chosen.isEmpty()) {
            if (resolved != null) Lang.s(R.string.pkg_auto_found, resolved) else Lang.s(R.string.pkg_auto_none)
        } else {
            val name = choices.firstOrNull { it.pkg == chosen }?.label ?: Lang.s(R.string.pkg_custom)
            Lang.s(if (resolved != null) R.string.pkg_chosen_installed else R.string.pkg_chosen_missing, name, chosen)
        }

    private fun smartTubeStatus(): String =
        pkgStatus(Config.smartTubePkg, AppLauncher.SMARTTUBE_CHOICES, AppLauncher.smartTubePackage(this))

    private fun nuvioStatus(): String =
        pkgStatus(Config.nuvioPkg, AppLauncher.NUVIO_CHOICES, AppLauncher.nuvioPackage(this))

    /** Pick from the ready-made list, "Automatic", or type a package name by hand. */
    private fun pickPackage(
        title: String, choices: List<AppLauncher.AppChoice>, current: String, onSet: (String) -> Unit
    ) {
        val items = ArrayList<String>()
        items.add(Lang.s(R.string.pkg_item_auto))
        for (c in choices) {
            items.add(Lang.s(if (AppLauncher.isInstalled(this, c.pkg)) R.string.pkg_item_installed else R.string.pkg_item_missing, c.label, c.pkg))
        }
        items.add(Lang.s(R.string.pkg_item_other))
        val sel = when {
            current.isEmpty() -> 0
            else -> choices.indexOfFirst { it.pkg == current }.let { if (it >= 0) it + 1 else items.size - 1 }
        }
        choose(title, items, sel) { i ->
            when {
                i == 0 -> onSet("")
                i <= choices.size -> onSet(choices[i - 1].pkg)
                else -> askText(Lang.s(R.string.pkg_enter_hint, title), current, false) { onSet(it.replace(" ", "")) }
            }
        }
    }

    /** Subtitle of the music app row: automatic (and which app that is now), or the chosen app and whether it is installed. */
    private fun musicStatus(): String {
        val app = MusicApps.forId(Config.musicApp)
        if (app == null) {
            val found = MusicApps.chosen(this)
            return if (found != null) Lang.s(R.string.pkg_auto_found, found.label) else Lang.s(R.string.pkg_auto_none)
        }
        val pkg = MusicApps.packageOf(this, app) ?: app.packages.first()
        val name = if (MusicApps.KNOWN.any { it.id == app.id }) app.label else Lang.s(R.string.pkg_custom)
        return Lang.s(
            if (MusicApps.isInstalled(this, app)) R.string.pkg_chosen_installed else R.string.pkg_chosen_missing,
            name, pkg
        )
    }

    /** Subtitle of the "Apps opened by voice" row: how many of the installed apps are switched on. */
    private fun appsStatus(): String {
        val all = AppIndex.list(this)
        if (all.isEmpty()) return Lang.s(R.string.apps_none_found)
        val off = Config.appsOff
        val on = all.count { it.pkg !in off }
        return if (on == all.size) Lang.s(R.string.apps_all, all.size) else Lang.s(R.string.apps_some, on, all.size)
    }

    /** Check list of the installed apps: the checked ones can be opened by voice ("Netflix ac", "open Prime Video"). */
    private fun pickApps() {
        val all = AppIndex.list(this, true)
        if (all.isEmpty()) {
            message(Lang.s(R.string.row_apps), Lang.s(R.string.apps_none_found))
            return
        }
        val off = Config.appsOff
        val checked = BooleanArray(all.size) { all[it].pkg !in off }
        val dup = all.groupingBy { it.label.lowercase() }.eachCount()
        val labels = Array(all.size) {
            val e = all[it]
            if ((dup[e.label.lowercase()] ?: 0) > 1) e.label + "  (" + e.pkg + ")" else e.label
        }
        val dlg = builder()
            .setTitle(Lang.s(R.string.dlg_apps))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(Lang.s(R.string.btn_ok)) { _, _ ->
                // Keep the choice for apps that are not installed right now (they may come back).
                val installed = all.map { it.pkg }.toSet()
                val newOff = HashSet<String>(Config.appsOff.filter { it !in installed })
                for (i in all.indices) if (!checked[i]) newOff.add(all[i].pkg)
                Config.appsOff = newOff
                render()
            }
            .setNeutralButton(Lang.s(R.string.apps_clear_all), null)
            .setNegativeButton(Lang.s(R.string.btn_cancel), null)
            .create()
        dlg.show()
        // The all / none button must not close the dialog.
        val toggle = dlg.getButton(AlertDialog.BUTTON_NEUTRAL)
        val refreshLabel = { toggle.text = Lang.s(if (checked.all { it }) R.string.apps_clear_all else R.string.apps_select_all) }
        refreshLabel()
        toggle.setOnClickListener {
            val state = !checked.all { it }
            for (i in checked.indices) {
                checked[i] = state
                dlg.listView.setItemChecked(i, state)
            }
            refreshLabel()
        }
        dlg.listView.setOnItemClickListener { _, _, which, _ ->
            checked[which] = dlg.listView.isItemChecked(which)
            refreshLabel()
        }
    }

    /** Music app for general music commands ("duman bal cal"): Automatic, one of the known apps, or a package name. */
    private fun pickMusicApp() {
        val known = MusicApps.KNOWN
        val items = ArrayList<String>()
        items.add(Lang.s(R.string.pkg_item_auto))
        for (a in known) {
            val pkg = MusicApps.packageOf(this, a) ?: a.packages.first()
            items.add(Lang.s(if (MusicApps.isInstalled(this, a)) R.string.pkg_item_installed else R.string.pkg_item_missing, a.label, pkg))
        }
        items.add(Lang.s(R.string.pkg_item_other))
        val cur = Config.musicApp
        val sel = when {
            cur.isEmpty() -> 0
            else -> known.indexOfFirst { it.id == cur }.let { if (it >= 0) it + 1 else items.size - 1 }
        }
        val title = Lang.s(R.string.dlg_music_app)
        val set = { v: String -> Config.musicApp = v }
        choose(title, items, sel) { i ->
            when {
                i == 0 -> set("")
                i <= known.size -> set(known[i - 1].id)
                else -> askText(Lang.s(R.string.pkg_enter_hint, title), if (cur.contains('.')) cur else "", false) {
                    set(it.replace(" ", ""))
                }
            }
        }
    }

    private fun pickSmartTube() =
        pickPackage(Lang.s(R.string.dlg_smarttube_pkg), AppLauncher.SMARTTUBE_CHOICES, Config.smartTubePkg) { Config.smartTubePkg = it }

    private fun pickNuvio() =
        pickPackage(Lang.s(R.string.dlg_nuvio_pkg), AppLauncher.NUVIO_CHOICES, Config.nuvioPkg) { Config.nuvioPkg = it }

    private fun testSmartTube() {
        if (!AppLauncher.searchSmartTube(this, Lang.s(R.string.test_query))) message("SmartTube", Lang.s(R.string.msg_smarttube_not_installed))
    }

    private fun openNuvio() {
        if (!AppLauncher.launchNuvio(this)) message("Nuvio", Lang.s(R.string.msg_nuvio_not_installed))
    }

    private fun testMediaSearch() {
        android.widget.Toast.makeText(this, Lang.s(R.string.toast_trying), android.widget.Toast.LENGTH_SHORT).show()
        val key = Config.tmdbKey
        Thread {
            val out = MediaSearch.search("interstellar", key)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val text = when {
                    out.error != null -> Lang.s(R.string.test_failed, out.error ?: "")
                    out.items.isEmpty() -> Lang.s(R.string.test_no_results)
                    else -> Lang.s(R.string.test_media_ok, out.items.size, out.items[0].title, out.items[0].year) +
                            "\n" + Lang.s(R.string.test_source, if (key.isBlank()) "Cinemeta" else "TMDB")
                }
                message(Lang.s(R.string.row_media_test), text)
            }
        }.start()
    }

    private fun testYouTubeSearch() {
        android.widget.Toast.makeText(this, Lang.s(R.string.toast_trying), android.widget.Toast.LENGTH_SHORT).show()
        val key = Config.youtubeKey
        Thread {
            val out = YouTubeSearch.search(Lang.s(R.string.test_yt_query), key)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val text = when {
                    out.error != null -> Lang.s(R.string.test_failed, out.error ?: "")
                    out.videos.isEmpty() -> Lang.s(R.string.test_no_videos)
                    else -> Lang.s(R.string.test_yt_ok, out.videos.size, out.videos[0].title) +
                            "\n" + Lang.s(R.string.test_source, if (key.isBlank()) Lang.s(R.string.test_src_keyless) else "YouTube Data API")
                }
                message(Lang.s(R.string.row_yt_test), text)
            }
        }.start()
    }

    private fun testWeather() {
        android.widget.Toast.makeText(this, Lang.s(R.string.toast_trying), android.widget.Toast.LENGTH_SHORT).show()
        val city = Config.weatherCity
        Thread {
            val out = WeatherClient.fetch(city, 0)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val r = out.report
                val text = when {
                    r != null -> Lang.s(R.string.test_weather_ok, r.place.label(), r.temp, WeatherClient.describe(r.code))
                    out.notFound -> Lang.s(R.string.test_weather_nf, city)
                    else -> Lang.s(R.string.test_failed, out.error ?: "")
                }
                message(Lang.s(R.string.row_weather_test), text)
            }
        }.start()
    }

    private fun micGranted() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMic() {
        if (micGranted()) message(Lang.s(R.string.row_mic_perm), Lang.s(R.string.perm_already))
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun secure(name: String): String = try {
        Settings.Secure.getString(contentResolver, name) ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun assistantStatus(): String {
        val v = secure("assistant")
        return when {
            v.startsWith("$packageName/") -> Lang.s(R.string.assist_this)
            v.isBlank() -> Lang.s(R.string.assist_unassigned)
            else -> Lang.s(R.string.assist_other, v.substringBefore('/'))
        }
    }

    private fun recognizerStatus(): String {
        val v = secure("voice_recognition_service")
        return when {
            v.startsWith("$packageName/") -> Lang.s(R.string.this_app)
            v.isBlank() -> Lang.s(R.string.unassigned)
            else -> Lang.s(R.string.recog_other, v.substringBefore('/'))
        }
    }

    private fun a11yStatus(): String {
        val enabled = secure(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).contains("$packageName/")
        return when {
            enabled && InjectorService.instance != null -> Lang.s(R.string.a11y_on)
            enabled -> Lang.s(R.string.a11y_on_unbound)
            else -> Lang.s(R.string.a11y_off)
        }
    }

    private fun openAccessibility() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            showAdbHelp()
        }
    }

    // ------------------------------------------------------------------ API settings

    private fun maskKey(k: String): String = when {
        k.isBlank() -> Lang.s(R.string.not_set)
        k.length <= 8 -> "••••••••"
        else -> k.take(4) + "••••••••" + k.takeLast(4)
    }

    private fun pickProvider() {
        val labels = Config.presets.map { it.label }
        val cur = Config.presets.indexOfFirst { it.id == Config.provider }.coerceAtLeast(0)
        choose(Lang.s(R.string.row_provider), labels, cur) { i ->
            val p = Config.presets[i]
            Config.provider = p.id
            if (p.id != "custom") {
                Config.baseUrl = p.baseUrl
                if (Config.model !in p.models) Config.model = p.models[0]
            } else if (Config.presets.any { it.id != "custom" && it.baseUrl == Config.baseUrl }) {
                Config.baseUrl = p.baseUrl
                Config.model = p.models[0]
            }
        }
    }

    private fun pickModel() {
        val models = Config.preset(Config.provider).models
        val items = models + Lang.s(R.string.model_other)
        choose("Model", items, models.indexOf(Config.model)) { i ->
            if (i < models.size) Config.model = models[i]
            else askText(Lang.s(R.string.dlg_model_name), Config.model, false) { Config.model = it }
        }
    }

    private val languages get() = listOf(
        Lang.s(R.string.lang_tr) to "tr", Lang.s(R.string.lang_en) to "en", Lang.s(R.string.lang_de) to "de",
        Lang.s(R.string.lang_fr) to "fr", Lang.s(R.string.lang_es) to "es", Lang.s(R.string.lang_ar) to "ar",
        Lang.s(R.string.lang_ru) to "ru", Lang.s(R.string.lang_auto) to ""
    )

    private fun languageLabel(): String =
        languages.firstOrNull { it.second == Config.language }?.first ?: Config.language

    private fun pickLanguage() {
        val cur = languages.indexOfFirst { it.second == Config.language }
        choose(Lang.s(R.string.row_stt_language), languages.map { it.first }, cur) { Config.language = languages[it].second }
    }

    private fun uiLanguageLabel(): String = when (Config.uiLanguage) {
        "tr" -> "Türkçe"
        "en" -> "English"
        else -> Lang.s(R.string.ui_lang_auto)
    }

    private fun pickUiLanguage() {
        val ids = listOf(Lang.AUTO, "tr", "en")
        val items = listOf(Lang.s(R.string.ui_lang_auto), "Türkçe", "English")
        choose(Lang.s(R.string.row_ui_language), items, ids.indexOf(Config.uiLanguage).coerceAtLeast(0)) { Config.uiLanguage = ids[it] }
    }

    private fun testConnection() {
        val wait = builder()
            .setTitle(Lang.s(R.string.dlg_testing))
            .setMessage(Lang.s(R.string.dlg_testing_msg))
            .setCancelable(false)
            .show()
        Thread {
            val r = SttClient.ping(Config.baseUrl, Config.apiKey, Config.model, Config.language)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                wait.dismiss()
                when (r) {
                    is SttClient.Result.Ok -> message(Lang.s(R.string.dlg_conn_ok), Lang.s(R.string.dlg_conn_ok_msg))
                    is SttClient.Result.Err -> message(Lang.s(R.string.dlg_conn_fail), r.message)
                }
            }
        }.start()
    }

    // ------------------------------------------------------------------ listening settings

    private fun cycleSilence() {
        val v = listOf(800, 1200, 1800, 2500)
        Config.silenceMs = v[(v.indexOf(Config.silenceMs) + 1) % v.size]
        render()
    }

    private fun cycleMax() {
        val v = listOf(10, 15, 30)
        Config.maxSeconds = v[(v.indexOf(Config.maxSeconds) + 1) % v.size]
        render()
    }

    private val sensitivities get() = listOf(
        Lang.s(R.string.sens_very) to 120, Lang.s(R.string.sens_high) to 250, Lang.s(R.string.sens_normal) to 500, Lang.s(R.string.sens_low) to 900
    )

    private fun sensitivityLabel(): String =
        sensitivities.firstOrNull { it.second == Config.vadThreshold }?.first ?: Lang.s(R.string.sens_custom, Config.vadThreshold)

    private fun pickSensitivity() {
        val cur = sensitivities.indexOfFirst { it.second == Config.vadThreshold }
        choose(Lang.s(R.string.row_sensitivity), sensitivities.map { it.first }, cur) {
            Config.vadThreshold = sensitivities[it].second
        }
    }

    private val sources get() = listOf(
        Config.SOURCE_VOICE_RECOGNITION to Lang.s(R.string.src_voice_rec),
        Config.SOURCE_MIC to Lang.s(R.string.src_mic),
        Config.SOURCE_VOICE_COMMUNICATION to Lang.s(R.string.src_comm),
        Config.SOURCE_UNPROCESSED to Lang.s(R.string.src_raw)
    )

    private fun sourceLabel(id: String): String = sources.firstOrNull { it.first == id }?.second ?: id

    private fun pickSource() {
        val cur = sources.indexOfFirst { it.first == Config.audioSource }
        choose(Lang.s(R.string.row_source), sources.map { it.second }, cur) { Config.audioSource = sources[it].first }
    }

    private fun inputDevices(): List<AudioDeviceInfo> {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
    }

    private fun typeName(t: Int): String = when (t) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> Lang.s(R.string.dev_builtin)
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (SCO)"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (A2DP)"
        AudioDeviceInfo.TYPE_USB_DEVICE -> Lang.s(R.string.dev_usb)
        AudioDeviceInfo.TYPE_USB_HEADSET -> Lang.s(R.string.dev_usb_headset)
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> Lang.s(R.string.dev_line)
        AudioDeviceInfo.TYPE_TELEPHONY -> Lang.s(R.string.dev_phone)
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> Lang.s(R.string.dev_submix)
        AudioDeviceInfo.TYPE_BUS -> "Bus"
        else -> Lang.s(R.string.dev_type, t)
    }

    private fun describe(d: AudioDeviceInfo): String =
        "${typeName(d.type)} • ${d.productName} (#${d.id})"

    private fun deviceLabel(): String {
        if (Config.inputDeviceId == 0) return Lang.s(R.string.dev_default)
        val d = inputDevices().firstOrNull { it.id == Config.inputDeviceId }
        return if (d != null) describe(d) else Lang.s(R.string.dev_missing, Config.inputDeviceId)
    }

    private fun pickDevice() {
        val devs = inputDevices()
        val items = listOf(Lang.s(R.string.dev_default)) + devs.map { describe(it) }
        val cur = if (Config.inputDeviceId == 0) 0 else (devs.indexOfFirst { it.id == Config.inputDeviceId } + 1)
        choose(Lang.s(R.string.row_mic_device), items, cur) { i ->
            Config.inputDeviceId = if (i == 0) 0 else devs[i - 1].id
        }
    }

    private fun showDevices() {
        val devs = inputDevices()
        val text = if (devs.isEmpty()) Lang.s(R.string.dev_none) else devs.joinToString("\n") { describe(it) }
        message(Lang.s(R.string.dlg_devices), text + "\n\n" + Lang.s(R.string.dev_note))
    }

    // ------------------------------------------------------------------ adb help

    private fun showAdbHelp() {
        val pkg = packageName
        val text = Lang.s(R.string.adb_easy) + "\n\n" +
                Lang.s(R.string.adb_manual) + "\n" +
                "pm grant $pkg android.permission.RECORD_AUDIO\n" +
                "settings put secure assistant $pkg/$pkg.AssistService\n" +
                "settings put secure voice_interaction_service $pkg/$pkg.AssistService\n" +
                "settings put secure voice_recognition_service $pkg/$pkg.CloudRecognitionService\n" +
                "settings put secure enabled_accessibility_services $pkg/$pkg.InjectorService\n" +
                "settings put secure accessibility_enabled 1"
        message(Lang.s(R.string.dlg_setup_cmds), text)
    }
}
