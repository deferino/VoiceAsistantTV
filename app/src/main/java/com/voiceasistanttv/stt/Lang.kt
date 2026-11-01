package com.voiceasistanttv.stt

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * UI language. The `uiLanguage` setting: "auto" (system language; English if it is not Turkish), "tr" or "en".
 * Strings live in `res/values` (English, the default) and `res/values-tr` (Turkish); [s] reads them for the selected language.
 * Needs no Context (the application context is given once via [init]), so it can also be used from network/service code.
 */
object Lang {
    const val AUTO = "auto"

    @Volatile
    private var app: Context? = null
    private val cache = HashMap<String, Context>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    /** Selected value in the settings: "auto", "tr" or "en". */
    fun pref(): String {
        val a = app ?: return AUTO
        return a.getSharedPreferences("stt_config", Context.MODE_PRIVATE).getString("uiLanguage", AUTO) ?: AUTO
    }

    /** Effective language code: "tr" or "en". */
    fun code(): String = when (pref()) {
        "tr" -> "tr"
        "en" -> "en"
        else -> if (Locale.getDefault().language == "tr") "tr" else "en"
    }

    fun locale(): Locale = if (code() == "tr") Locale("tr", "TR") else Locale.ENGLISH

    @Synchronized
    private fun res(): Context {
        val a = app ?: throw IllegalStateException("Lang.init was not called")
        return cache.getOrPut(code()) {
            val cfg = Configuration(a.resources.configuration)
            cfg.setLocale(locale())
            a.createConfigurationContext(cfg)
        }
    }

    fun s(id: Int): String = res().getString(id)

    fun s(id: Int, vararg args: Any): String = res().getString(id, *args)

    /** The TMDB `language=` value. */
    fun tmdbLang(): String = if (code() == "tr") "tr-TR" else "en-US"

    /** YouTube `gl` / `regionCode`: TR in the Turkish UI, otherwise the device region (US if none). */
    fun region(): String = if (code() == "tr") "TR" else Locale.getDefault().country.ifBlank { "US" }

    /** "%40" (Turkish) / "40%" (English). */
    fun pct(n: Int): String = if (code() == "tr") "%$n" else "$n%"

    /** English UI with a US locale: °F and mph. */
    fun imperial(): Boolean = code() == "en" && Locale.getDefault().country == "US"

    fun unitsQuery(): String = if (imperial()) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else ""

    fun windUnit(): String = if (imperial()) "mph" else if (code() == "tr") "km/s" else "km/h"

    fun acceptLanguage(): String = if (code() == "tr") "tr-TR,tr;q=0.9" else "en-US,en;q=0.9"
}
