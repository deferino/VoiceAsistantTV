package com.voiceasistanttv.stt

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * All settings are kept here (SharedPreferences).
 *
 * On first launch, a `filesDir/config.json` left behind by adb is imported and then deleted.
 * That way the long API key does not have to be typed with the remote (see install.bat).
 */
object Config {
    private const val TAG = "VoiceTV"

    const val SOURCE_VOICE_RECOGNITION = "voice_recognition"
    const val SOURCE_MIC = "mic"
    const val SOURCE_VOICE_COMMUNICATION = "voice_communication"
    const val SOURCE_UNPROCESSED = "unprocessed"

    const val TARGET_BOTH = "both"
    const val TARGET_SMARTTUBE = "smarttube"
    const val TARGET_NUVIO = "nuvio"
    const val TARGET_WRITE = "write"

    const val INSERT_APPEND = "append"
    const val INSERT_REPLACE = "replace"

    class Preset(val id: String, private val labelOf: () -> String, val baseUrl: String, val models: List<String>) {
        val label: String get() = labelOf()
    }

    val presets: List<Preset> = listOf(
        Preset(
            "groq", { Lang.s(R.string.preset_groq) }, "https://api.groq.com/openai/v1",
            listOf("whisper-large-v3-turbo", "whisper-large-v3")
        ),
        Preset(
            "openai", { "OpenAI" }, "https://api.openai.com/v1",
            listOf("gpt-4o-mini-transcribe", "gpt-4o-transcribe", "whisper-1")
        ),
        Preset(
            "custom", { Lang.s(R.string.preset_custom) }, "http://192.168.1.10:8000/v1",
            listOf("whisper-1")
        )
    )

    fun preset(id: String): Preset = presets.firstOrNull { it.id == id } ?: presets[0]

    private val stringKeys = setOf(
        "provider", "baseUrl", "apiKey", "model", "language", "prompt", "audioSource", "kbAudioSource", "insertMode",
        "searchTarget", "tmdbKey", "youtubeKey", "weatherCity", "smartTubePkg", "nuvioPkg", "musicApp", "uiLanguage",
        "spotifyId", "spotifySecret", "musicLinkKey", "appsOff"
    )
    private val intKeys = setOf("silenceMs", "maxSeconds", "noSpeechSeconds", "vadThreshold", "inputDeviceId")
    private val boolKeys = setOf("autoSubmit", "spotifyBackground", "bitchordBackground")

    private lateinit var prefs: SharedPreferences

    @Volatile
    private var ready = false

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences("stt_config", Context.MODE_PRIVATE)
        ready = true
        importFile(app)
    }

    private fun str(key: String, def: String): String = prefs.getString(key, def) ?: def
    private fun int(key: String, def: Int): Int = prefs.getInt(key, def)
    private fun bool(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)

    var provider: String
        get() = str("provider", "groq")
        set(v) = prefs.edit().putString("provider", v).apply()

    var baseUrl: String
        get() = str("baseUrl", presets[0].baseUrl)
        set(v) = prefs.edit().putString("baseUrl", v).apply()

    var apiKey: String
        get() = str("apiKey", "")
        set(v) = prefs.edit().putString("apiKey", v).apply()

    var model: String
        get() = str("model", presets[0].models[0])
        set(v) = prefs.edit().putString("model", v).apply()

    /** ISO-639-1 code ("tr"); empty = auto-detect. */
    var language: String
        get() = str("language", Lang.code())
        set(v) = prefs.edit().putString("language", v).apply()

    /** Optional context/hint text (Whisper `prompt` field). */
    var prompt: String
        get() = str("prompt", "")
        set(v) = prefs.edit().putString("prompt", v).apply()

    /** How many ms of silence after speech ends before recording stops. */
    var silenceMs: Int
        get() = int("silenceMs", 1200)
        set(v) = prefs.edit().putInt("silenceMs", v).apply()

    var maxSeconds: Int
        get() = int("maxSeconds", 15)
        set(v) = prefs.edit().putInt("maxSeconds", v).apply()

    /** How many seconds without any detected speech before giving up. */
    var noSpeechSeconds: Int
        get() = int("noSpeechSeconds", 6)
        set(v) = prefs.edit().putInt("noSpeechSeconds", v).apply()

    /** Minimum RMS threshold for speech detection (16-bit samples). Lower = more sensitive. */
    var vadThreshold: Int
        get() = int("vadThreshold", 500)
        set(v) = prefs.edit().putInt("vadThreshold", v).apply()

    var audioSource: String
        get() = str("audioSource", SOURCE_VOICE_RECOGNITION)
        set(v) = prefs.edit().putString("audioSource", v).apply()

    /** Audio source to try on the keyboard (SpeechRecognizer) path; empty = use audioSource. The assistant-key path does not use this. */
    var kbAudioSource: String
        get() = str("kbAudioSource", "")
        set(v) = prefs.edit().putString("kbAudioSource", v).apply()

    /** 0 = system default; otherwise an AudioDeviceInfo.id. */
    var inputDeviceId: Int
        get() = int("inputDeviceId", 0)
        set(v) = prefs.edit().putInt("inputDeviceId", v).apply()

    /** What to do when speech ends: both | smarttube | nuvio | write. */
    var searchTarget: String
        get() = str("searchTarget", TARGET_BOTH).let { if (it == "ask") TARGET_BOTH else it }
        set(v) = prefs.edit().putString("searchTarget", v).apply()

    /** On the "ask every time" screen, the option focused first (the last one used). */
    var lastTarget: String
        get() = str("lastTarget", TARGET_SMARTTUBE)
        set(v) = prefs.edit().putString("lastTarget", v).apply()

    /** Music app for general music commands ("duman bal cal"): "" = automatic, a MusicApps id ("spotify", "bitchord") or a package name. */
    var musicApp: String
        get() = str("musicApp", "").trim()
        set(v) = prefs.edit().putString("musicApp", v.trim()).apply()

    /** Music app a song last started in, and when (ms since epoch, 0 = never): that app is connected ahead for people who use it. */
    var lastMusicApp: String
        get() = str("lastMusicApp", "")
        set(v) = prefs.edit().putString("lastMusicApp", v).apply()

    var lastMusicUse: Long
        get() = prefs.getLong("lastMusicUse", 0L)
        set(v) = prefs.edit().putLong("lastMusicUse", v).apply()

    /**
     * Hint text for the assistant (mic key) path: the user's own prompt plus the app names, so the recognizer writes
     * "BitChord", "Spotify" ... instead of how they sound in Turkish ("bitçörd", "spotifay").
     */
    fun assistPrompt(): String {
        val own = prompt.trim()
        return if (own.isEmpty()) VoiceRoute.NAME_HINT else own + " " + VoiceRoute.NAME_HINT
    }

    /**
     * Where the music app's own screen is after a song starts by voice. True (default): out of sight, the music plays "in the
     * background" (Spotify TV brings its screen up by itself, so the app goes back to the home screen right away; BitChord shows
     * nothing). False: Spotify's screen stays / BitChord's screen is opened.
     */
    var spotifyBackground: Boolean
        get() = bool("spotifyBackground", true)
        set(v) = prefs.edit().putBoolean("spotifyBackground", v).apply()

    var bitchordBackground: Boolean
        get() = bool("bitchordBackground", true)
        set(v) = prefs.edit().putBoolean("bitchordBackground", v).apply()

    /** The setting above for [app] (any other music app: background). */
    fun musicBackground(app: MusicApps.App): Boolean = when (app.id) {
        MusicApps.SPOTIFY.id -> spotifyBackground
        MusicApps.BITCHORD.id -> bitchordBackground
        else -> true
    }

    /** Optional Spotify developer app (Client ID + Secret): "spotify <song>" looks the song up with Spotify's search and plays it. */
    var spotifyId: String
        get() = str("spotifyId", "")
        set(v) = prefs.edit().putString("spotifyId", v.trim()).apply()

    var spotifySecret: String
        get() = str("spotifySecret", "")
        set(v) = prefs.edit().putString("spotifySecret", v.trim()).apply()

    /** Optional MusicLink key (free, musiclink.one): the other way to find a song's Spotify track (see [SpotifyLookup]). */
    var musicLinkKey: String
        get() = str("musicLinkKey", "")
        set(v) = prefs.edit().putString("musicLinkKey", v.trim()).apply()

    /** Optional TMDB key (v3 key or v4 read token): also finds localized (e.g. Turkish) movie titles. */
    var tmdbKey: String
        get() = str("tmdbKey", "")
        set(v) = prefs.edit().putString("tmdbKey", v).apply()

    /** Optional YouTube Data API v3 key (without it, the keyless web search endpoint is tried). */
    var youtubeKey: String
        get() = str("youtubeKey", "")
        set(v) = prefs.edit().putString("youtubeKey", v).apply()

    /** City shown in the weather panel when none is spoken ("weather"). */
    var weatherCity: String
        get() = str("weatherCity", "").ifBlank { Lang.s(R.string.default_city) }
        set(v) = prefs.edit().putString("weatherCity", v).apply()

    /** SmartTube package name to use; empty = auto-detect the installed one. */
    var smartTubePkg: String
        get() = str("smartTubePkg", "").trim()
        set(v) = prefs.edit().putString("smartTubePkg", v.trim()).apply()

    /** Nuvio package name to use; empty = auto-detect the installed one. */
    var nuvioPkg: String
        get() = str("nuvioPkg", "").trim()
        set(v) = prefs.edit().putString("nuvioPkg", v.trim()).apply()

    /** Packages switched off in "Apps opened by voice" (apps are on by default, so new ones work without a visit to the settings). */
    var appsOff: Set<String>
        get() = str("appsOff", "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        set(v) = prefs.edit().putString("appsOff", v.sorted().joinToString(",")).apply()

    /** UI language: auto | tr | en (see Lang). */
    var uiLanguage: String
        get() = str("uiLanguage", Lang.AUTO)
        set(v) = prefs.edit().putString("uiLanguage", v).apply()

    var insertMode: String
        get() = str("insertMode", INSERT_APPEND)
        set(v) = prefs.edit().putString("insertMode", v).apply()

    /** After typing, automatically send "Enter/Search" (via accessibility, Android 11+). */
    var autoSubmit: Boolean
        get() = bool("autoSubmit", false)
        set(v) = prefs.edit().putBoolean("autoSubmit", v).apply()

    /**
     * Applies settings received via `adb shell am broadcast -n …/.ConfigReceiver -a com.voiceasistanttv.stt.CONFIG --es apiKey … `
     * (known keys only). Returns how many settings were applied.
     */
    @Synchronized
    fun applyExtras(extras: android.os.Bundle?): Int {
        if (!ready || extras == null) return 0
        val e = prefs.edit()
        var n = 0
        for (k in stringKeys) extras.getString(k)?.let { e.putString(k, it.trim()); n++ }
        for (k in intKeys) if (extras.containsKey(k)) { e.putInt(k, extras.getInt(k)); n++ }
        for (k in boolKeys) if (extras.containsKey(k)) { e.putBoolean(k, extras.getBoolean(k)); n++ }
        e.apply()
        return n
    }

    /** Imports and deletes a config.json left behind by adb, if present (called at startup and when the screen opens). */
    @Synchronized
    fun importFile(ctx: Context) {
        if (!ready) return
        val f = File(ctx.applicationContext.filesDir, "config.json")
        if (!f.exists()) return
        try {
            val o = JSONObject(f.readText(Charsets.UTF_8))
            val e = prefs.edit()
            for (k in stringKeys) if (o.has(k)) e.putString(k, o.getString(k))
            for (k in intKeys) if (o.has(k)) e.putInt(k, o.getInt(k))
            for (k in boolKeys) if (o.has(k)) e.putBoolean(k, o.getBoolean(k))
            e.apply()
            Log.i(TAG, "config.json imported")
        } catch (ex: Exception) {
            Log.e(TAG, "config.json could not be read: ${ex.message}")
        } finally {
            f.delete()
        }
    }
}
