package com.voiceasistanttv.stt

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Arayüz dili / UI language. Ayar `uiLanguage`: "auto" (sistem dili; Türkçe değilse İngilizce), "tr" ya da "en".
 * Metinler `res/values` (İngilizce, varsayılan) ve `res/values-tr` (Türkçe) içindedir; [s] seçili dile göre okur.
 * Context gerektirmez (uygulama bağlamı [init] ile bir kez verilir), bu yüzden ağ/servis kodundan da kullanılabilir.
 */
object Lang {
    const val AUTO = "auto"

    @Volatile
    private var app: Context? = null
    private val cache = HashMap<String, Context>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    /** Ayarda seçili değer: "auto", "tr" ya da "en". */
    fun pref(): String {
        val a = app ?: return AUTO
        return a.getSharedPreferences("stt_config", Context.MODE_PRIVATE).getString("uiLanguage", AUTO) ?: AUTO
    }

    /** Etkin dil kodu: "tr" ya da "en". */
    fun code(): String = when (pref()) {
        "tr" -> "tr"
        "en" -> "en"
        else -> if (Locale.getDefault().language == "tr") "tr" else "en"
    }

    fun locale(): Locale = if (code() == "tr") Locale("tr", "TR") else Locale.ENGLISH

    @Synchronized
    private fun res(): Context {
        val a = app ?: throw IllegalStateException("Lang.init çağrılmadı")
        return cache.getOrPut(code()) {
            val cfg = Configuration(a.resources.configuration)
            cfg.setLocale(locale())
            a.createConfigurationContext(cfg)
        }
    }

    fun s(id: Int): String = res().getString(id)

    fun s(id: Int, vararg args: Any): String = res().getString(id, *args)

    /** TMDB `language=` değeri. */
    fun tmdbLang(): String = if (code() == "tr") "tr-TR" else "en-US"

    /** YouTube `gl` / `regionCode`: Türkçe arayüzde TR, aksi halde cihazın bölgesi (yoksa US). */
    fun region(): String = if (code() == "tr") "TR" else Locale.getDefault().country.ifBlank { "US" }

    /** "%40" (Türkçe) / "40%" (İngilizce). */
    fun pct(n: Int): String = if (code() == "tr") "%$n" else "$n%"

    /** ABD yerel ayarında İngilizce arayüz: °F ve mph. */
    fun imperial(): Boolean = code() == "en" && Locale.getDefault().country == "US"

    fun unitsQuery(): String = if (imperial()) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else ""

    fun windUnit(): String = if (imperial()) "mph" else if (code() == "tr") "km/s" else "km/h"

    fun acceptLanguage(): String = if (code() == "tr") "tr-TR,tr;q=0.9" else "en-US,en;q=0.9"
}
