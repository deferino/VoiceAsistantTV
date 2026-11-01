package com.voiceasistanttv.stt

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Tüm ayarlar burada tutulur (SharedPreferences).
 *
 * İlk açılışta, adb ile bırakılan `filesDir/config.json` dosyası varsa içe aktarılır ve silinir.
 * Böylece TV'de uzun API anahtarını kumandayla yazmak gerekmez (bkz. kurulum.bat).
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
        "searchTarget", "tmdbKey", "youtubeKey", "weatherCity", "smartTubePkg", "nuvioPkg", "uiLanguage"
    )
    private val intKeys = setOf("silenceMs", "maxSeconds", "noSpeechSeconds", "vadThreshold", "inputDeviceId")
    private val boolKeys = setOf("autoSubmit")

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

    /** ISO-639-1 kodu ("tr"); boş = otomatik algıla. */
    var language: String
        get() = str("language", Lang.code())
        set(v) = prefs.edit().putString("language", v).apply()

    /** İsteğe bağlı bağlam/ipucu metni (Whisper `prompt` alanı). */
    var prompt: String
        get() = str("prompt", "")
        set(v) = prefs.edit().putString("prompt", v).apply()

    /** Konuşma bittikten kaç ms sessizlik sonra kayıt kapanır. */
    var silenceMs: Int
        get() = int("silenceMs", 1200)
        set(v) = prefs.edit().putInt("silenceMs", v).apply()

    var maxSeconds: Int
        get() = int("maxSeconds", 15)
        set(v) = prefs.edit().putInt("maxSeconds", v).apply()

    /** Hiç konuşma algılanmazsa kaç saniye sonra vazgeçilir. */
    var noSpeechSeconds: Int
        get() = int("noSpeechSeconds", 6)
        set(v) = prefs.edit().putInt("noSpeechSeconds", v).apply()

    /** Konuşma algılama için minimum RMS eşiği (16-bit örnekler). Düşük = daha hassas. */
    var vadThreshold: Int
        get() = int("vadThreshold", 500)
        set(v) = prefs.edit().putInt("vadThreshold", v).apply()

    var audioSource: String
        get() = str("audioSource", SOURCE_VOICE_RECOGNITION)
        set(v) = prefs.edit().putString("audioSource", v).apply()

    /** Klavye (SpeechRecognizer) yolunda denenecek ses kaynağı; boş = audioSource kullanılır. Asistan tuşu yolu bunu kullanmaz. */
    var kbAudioSource: String
        get() = str("kbAudioSource", "")
        set(v) = prefs.edit().putString("kbAudioSource", v).apply()

    /** 0 = sistem varsayılanı; aksi halde AudioDeviceInfo.id. */
    var inputDeviceId: Int
        get() = int("inputDeviceId", 0)
        set(v) = prefs.edit().putInt("inputDeviceId", v).apply()

    /** Konuşma bitince ne yapılsın: both | smarttube | nuvio | write. */
    var searchTarget: String
        get() = str("searchTarget", TARGET_BOTH).let { if (it == "ask") TARGET_BOTH else it }
        set(v) = prefs.edit().putString("searchTarget", v).apply()

    /** "Her seferinde sor" ekranında önce odaklanacak seçenek (son kullanılan). */
    var lastTarget: String
        get() = str("lastTarget", TARGET_SMARTTUBE)
        set(v) = prefs.edit().putString("lastTarget", v).apply()

    /** İsteğe bağlı TMDB anahtarı (v3 anahtarı ya da v4 okuma jetonu): Türkçe film adlarını da bulur. */
    var tmdbKey: String
        get() = str("tmdbKey", "")
        set(v) = prefs.edit().putString("tmdbKey", v).apply()

    /** İsteğe bağlı YouTube Data API v3 anahtarı (yoksa anahtarsız web arama uç noktası denenir). */
    var youtubeKey: String
        get() = str("youtubeKey", "")
        set(v) = prefs.edit().putString("youtubeKey", v).apply()

    /** Konuşmada şehir söylenmezse ("hava durumu") hava durumu bu şehir için gösterilir. */
    var weatherCity: String
        get() = str("weatherCity", "").ifBlank { Lang.s(R.string.default_city) }
        set(v) = prefs.edit().putString("weatherCity", v).apply()

    /** Kullanılacak SmartTube paket adı; boş = kurulu olanı kendisi bulur (otomatik). */
    var smartTubePkg: String
        get() = str("smartTubePkg", "").trim()
        set(v) = prefs.edit().putString("smartTubePkg", v.trim()).apply()

    /** Kullanılacak Nuvio paket adı; boş = kurulu olanı kendisi bulur (otomatik). */
    var nuvioPkg: String
        get() = str("nuvioPkg", "").trim()
        set(v) = prefs.edit().putString("nuvioPkg", v.trim()).apply()

    /** Arayüz dili: auto | tr | en (bkz. Lang). */
    var uiLanguage: String
        get() = str("uiLanguage", Lang.AUTO)
        set(v) = prefs.edit().putString("uiLanguage", v).apply()

    var insertMode: String
        get() = str("insertMode", INSERT_APPEND)
        set(v) = prefs.edit().putString("insertMode", v).apply()

    /** Yazdıktan sonra otomatik "Enter/Ara" gönder (Android 11+ erişilebilirlik ile). */
    var autoSubmit: Boolean
        get() = bool("autoSubmit", false)
        set(v) = prefs.edit().putBoolean("autoSubmit", v).apply()

    /**
     * `adb shell am broadcast -n …/.ConfigReceiver -a com.voiceasistanttv.stt.CONFIG --es apiKey … ` ile gelen ayarları
     * uygular (yalnız bilinen anahtarlar). Kaç ayar uygulandığını döndürür.
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

    /** adb ile bırakılan config.json varsa içe aktarır ve siler (açılışta ve ekran açılırken çağrılır). */
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
            Log.i(TAG, "config.json içe aktarıldı")
        } catch (ex: Exception) {
            Log.e(TAG, "config.json okunamadı: ${ex.message}")
        } finally {
            f.delete()
        }
    }
}
