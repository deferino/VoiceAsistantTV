package com.voiceasistanttv.stt

import android.net.Uri
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Konuşulan cümle bir hava durumu sorusu mu? ("İstanbul hava durumu", "İstanbul'da bugün hava nasıl",
 * "yarın Ankara kaç derece", "weather in London", "will it rain tomorrow in Paris" …). Her iki dil de
 * (arayüz dilinden bağımsız) tanınır. Evetse şehri ve günü çıkarır; hayırsa null döner ve normal arama sürer.
 */
object WeatherQuery {
    /** city: konuşmada geçen yer adı (yoksa null → ayarlardaki varsayılan şehir); dayOffset: 0 bugün, 1 yarın, 2 öbür gün. */
    class Query(val city: String?, val dayOffset: Int)

    private const val FROM = "ıçğöşüâîû"
    private const val TO = "icgosuaiu"

    /** Küçük harf + Türkçe karakterleri sadeleştirme (uzunluk değişmez). */
    fun norm(s: String): String {
        val l = s.lowercase(Locale("tr", "TR"))
        val sb = StringBuilder(l.length)
        for (ch in l) {
            val i = FROM.indexOf(ch)
            sb.append(if (i >= 0) TO[i] else ch)
        }
        return sb.toString()
    }

    private fun clean(n: String): String =
        n.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private val triggers = listOf(
        Regex("\\bhava\\s+durum"),
        Regex("\\bhava\\s+(nasil|kac|sicak|soguk|yagisli|tahmin|rapor|ne\\s+kadar|ne\\s+durumda|ne\\s+olacak)"),
        Regex("\\bhava\\s+(bugun|yarin)"),
        Regex("\\bkac\\s+derece"),
        Regex("\\b(yagmur|kar|dolu)\\s+(yagacak|yagar|yagiyor)"),
        Regex("\\byagmur\\s+(var|olacak)\\s+m[iu]"),
        Regex("\\b(bugun|yarin|obur\\s+gun|ertesi\\s+gun|simdi|su\\s+an|suan|haftalik|bu\\s+hafta|hafta\\s+sonu)\\s+(icin\\s+)?hava\\b"),
        Regex("\\bsicaklik(lar)?\\b|\\bsicakligi\\b"),
        // English
        Regex("\\bweather\\b"),
        Regex("\\bforecast\\b"),
        Regex("\\btemperature\\b"),
        Regex("\\bhow\\s+(hot|cold|warm|chilly)\\s+is\\s+it\\b"),
        Regex("\\bhow\\s+many\\s+degrees\\b"),
        Regex("\\b(is|will|does)\\s+it\\s+(going\\s+to\\s+)?(rain|snow|be\\s+(sunny|rainy|cold|hot|warm|cloudy|snowy|windy))\\b"),
        Regex("\\bis\\s+it\\s+(raining|snowing|cold|hot|warm|sunny|cloudy|windy)\\b"),
        Regex("\\bdo\\s+i\\s+need\\s+(an?\\s+)?(umbrella|jacket|coat)\\b")
    )

    private val stop = setOf(
        "hava", "havasi", "havanin", "durumu", "durum", "durumunu", "durumuna", "nasil", "nasildir", "olacak", "olur",
        "bugun", "bugunku", "yarin", "yarinki", "obur", "ertesi", "gun", "gunu", "gunluk", "bu", "hafta", "haftalik",
        "sonu", "su", "an", "simdi", "suan", "simdiki", "kac", "derece", "dereceye", "sicaklik", "sicakligi", "sicakliklar",
        "tahmini", "tahmin", "raporu", "rapor", "ne", "kadar", "nedir", "mi", "mu", "var", "yagmur", "yagacak", "yagar",
        "yagiyor", "kar", "dolu", "acaba", "bana", "soyle", "soyler", "misin", "misiniz", "goster", "bak", "bakar",
        "icin", "ile", "ve", "peki", "de", "da", "te", "ta", "aksam", "sabah", "oglen", "gece", "hakkinda", "bilgi",
        "sicak", "soguk", "yagisli", "saatlik", "durumda", "lutfen", "ogren", "ogrenmek", "istiyorum", "tamam",
        // English
        "weather", "forecast", "forecasts", "temperature", "temperatures", "degrees", "degree", "what", "whats", "how",
        "hows", "is", "are", "it", "its", "the", "a", "an", "in", "at", "on", "for", "of", "to", "me", "tell", "show",
        "check", "get", "give", "please", "today", "tomorrow", "tonight", "now", "currently", "current", "right",
        "like", "outside", "going", "gonna", "be", "will", "do", "does", "i", "need", "umbrella", "jacket", "coat",
        "rain", "raining", "rainy", "snow", "snowing", "snowy", "sunny", "cloudy", "windy", "cold", "hot", "warm",
        "chilly", "much", "many", "day", "after", "next", "this", "week", "weekend", "weekly", "hourly", "daily",
        "report", "conditions", "condition", "there", "here", "can", "you", "could", "would", "hey", "ok", "okay",
        "let", "know", "want", "see", "about", "and", "or", "with", "say", "look", "up", "by", "s", "overmorrow", "two",
        "days", "later", "morning", "afternoon", "evening", "night", "um", "uh", "so", "far"
    )

    fun parse(text: String): Query? {
        val cleaned = clean(norm(text))
        if (cleaned.isEmpty() || cleaned.split(' ').size > 12) return null
        if (triggers.none { it.containsMatchIn(cleaned) }) return null

        val day = when {
            Regex("\\bobur gun|\\bertesi gun|\\bday after tomorrow|\\bovermorrow|\\bin two days").containsMatchIn(cleaned) -> 2
            Regex("\\byarin|\\btomorrow").containsMatchIn(cleaned) -> 1
            else -> 0
        }

        val city = ArrayList<String>()
        for (raw in text.split(Regex("[\\s,.?!;:\"“”()]+"))) {
            if (raw.isBlank()) continue
            // "İstanbul'da", "Ankara’daki" → ek kalkar
            val base = raw.substringBefore('\'').substringBefore('’').substringBefore('ʼ')
            if (base.isBlank()) continue
            if (norm(base) in stop) continue
            city.add(base)
        }
        val name = city.joinToString(" ").trim()
        return Query(if (name.isBlank()) null else name, day)
    }
}

/** Open-Meteo (anahtarsız, ücretsiz): yer arama + tahmin. Ağ çağrıları yapar; ana iş parçacığından çağırma. */
object WeatherClient {
    class Place(val name: String, val admin: String, val country: String, val lat: Double, val lon: Double) {
        fun label(): String {
            val parts = ArrayList<String>()
            parts.add(name)
            if (admin.isNotBlank() && WeatherQuery.norm(admin) != WeatherQuery.norm(name)) parts.add(admin)
            if (country.isNotBlank()) parts.add(country.removeSuffix(" Cumhuriyeti"))
            return parts.joinToString(" · ")
        }
    }

    class Day(
        val date: String,
        val code: Int,
        val max: Int,
        val min: Int,
        val rain: Int,
        val sunrise: String,
        val sunset: String
    )

    class Hour(val label: String, val code: Int, val temp: Int, val rain: Int, val isDay: Boolean)

    class Report(
        val place: Place,
        val temp: Int,
        val feels: Int,
        val humidity: Int,
        val wind: Int,
        val code: Int,
        val isDay: Boolean,
        val time: String,
        val days: List<Day>,
        val hours: List<Hour>
    )

    /** report doluysa başarılı; notFound: yer bulunamadı (hava sorusu değilmiş olabilir); aksi halde error. */
    class Outcome(val report: Report?, val notFound: Boolean, val error: String?)

    fun fetch(cityText: String, dayOffset: Int): Outcome {
        return try {
            val place = findPlace(cityText) ?: return Outcome(null, true, null)
            Outcome(forecast(place, dayOffset), false, null)
        } catch (e: Exception) {
            Outcome(null, false, MediaSearch.describe(e))
        }
    }

    // ---------------------------------------------------------------- yer arama

    private val suffixes = listOf(
        "daki", "deki", "taki", "teki", "dan", "den", "tan", "ten", "nin", "nun", "nın", "nün",
        "da", "de", "ta", "te", "in", "un", "ın", "ün"
    )

    /** "Ankarada" → ["Ankarada", "Ankara"]: Whisper bazen kesme işareti koymaz. */
    private fun candidates(raw: String): List<String> {
        val out = ArrayList<String>()
        out.add(raw)
        val last = raw.substringAfterLast(' ')
        val head = raw.dropLast(last.length)
        val nl = WeatherQuery.norm(last)
        for (s in suffixes) {
            if (out.size >= 3) break
            if (nl.endsWith(WeatherQuery.norm(s)) && last.length - s.length >= 3) out.add(head + last.dropLast(s.length))
        }
        return out
    }

    private fun lev(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    private fun similar(a: String, b: String): Boolean =
        a == b || (minOf(a.length, b.length) >= 6 && lev(a, b) <= 1)

    private fun findPlace(raw: String): Place? {
        val cands = candidates(raw.trim())
        val normed = cands.map { WeatherQuery.norm(it) }
        val primary = Lang.code()
        for (c in cands) {
            val res = geocode(c, primary)
            val hit = res.firstOrNull { p -> normed.any { similar(WeatherQuery.norm(p.name), it) } }
            if (hit != null) return hit
        }
        // Yer adı öbür dilde söylenmiş olabilir ("Münih" / "Munich"): yalnız ilk aday için öbür dili de dene.
        val other = if (primary == "tr") "en" else "tr"
        return geocode(cands[0], other).firstOrNull { p -> normed.any { similar(WeatherQuery.norm(p.name), it) } }
    }

    private fun geocode(name: String, lang: String): List<Place> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=5&language=" + lang + "&format=json&name=" + Uri.encode(name)
        val arr = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        val list = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!o.has("latitude") || !o.has("longitude")) continue
            list.add(
                Place(
                    o.optString("name"),
                    o.optString("admin1"),
                    o.optString("country"),
                    o.optDouble("latitude"),
                    o.optDouble("longitude")
                )
            )
        }
        return list
    }

    // ---------------------------------------------------------------- tahmin

    private fun forecast(p: Place, dayOffset: Int): Report {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
                "&timezone=auto&forecast_days=7" + Lang.unitsQuery()
        val j = JSONObject(get(url))
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        val hourly = j.getJSONObject("hourly")

        val dTime = daily.getJSONArray("time")
        val days = ArrayList<Day>()
        for (i in 0 until dTime.length()) {
            days.add(
                Day(
                    dTime.getString(i),
                    daily.getJSONArray("weather_code").optInt(i, 0),
                    round(daily.getJSONArray("temperature_2m_max").optDouble(i, 0.0)),
                    round(daily.getJSONArray("temperature_2m_min").optDouble(i, 0.0)),
                    round(daily.optJSONArray("precipitation_probability_max")?.optDouble(i, 0.0) ?: 0.0),
                    hm(daily.optJSONArray("sunrise")?.optString(i).orEmpty()),
                    hm(daily.optJSONArray("sunset")?.optString(i).orEmpty())
                )
            )
        }

        val now = cur.optString("time")
        val hours = buildHours(hourly, now, dayOffset, days)

        return Report(
            p,
            round(cur.optDouble("temperature_2m", 0.0)),
            round(cur.optDouble("apparent_temperature", 0.0)),
            round(cur.optDouble("relative_humidity_2m", 0.0)),
            round(cur.optDouble("wind_speed_10m", 0.0)),
            cur.optInt("weather_code", 0),
            cur.optInt("is_day", 1) == 1,
            hm(now),
            days,
            hours
        )
    }

    /** Bugün: şimdiden itibaren 2 saatlik adımlarla 10 kart; yarın/öbür gün: o günün 06:00–22:00 arası 2 saatlik adımlar. */
    private fun buildHours(h: JSONObject, now: String, dayOffset: Int, days: List<Day>): List<Hour> {
        val times = h.getJSONArray("time")
        val temps = h.getJSONArray("temperature_2m")
        val codes = h.getJSONArray("weather_code")
        val rain = h.optJSONArray("precipitation_probability")
        val isDay = h.optJSONArray("is_day")
        val out = ArrayList<Hour>()

        fun add(i: Int, label: String) {
            out.add(
                Hour(
                    label,
                    codes.optInt(i, 0),
                    round(temps.optDouble(i, 0.0)),
                    round(rain?.optDouble(i, 0.0) ?: 0.0),
                    (isDay?.optInt(i, 1) ?: 1) == 1
                )
            )
        }

        if (dayOffset <= 0) {
            val key = if (now.length >= 13) now.substring(0, 13) else ""
            var start = 0
            for (i in 0 until times.length()) {
                if (times.getString(i).startsWith(key)) {
                    start = i
                    break
                }
            }
            var i = start
            while (i < times.length() && out.size < 10) {
                add(i, if (out.isEmpty()) Lang.s(R.string.now) else hm(times.getString(i)))
                i += 2
            }
        } else {
            val date = days.getOrNull(dayOffset)?.date ?: return out
            for (i in 0 until times.length()) {
                val t = times.getString(i)
                if (!t.startsWith(date)) continue
                val hour = t.substring(11, 13).toIntOrNull() ?: continue
                if (hour in 6..22 && hour % 2 == 0) add(i, hm(t))
            }
        }
        return out
    }

    private fun round(d: Double): Int = Math.round(d).toInt()

    /** "2026-10-23T07:05" → "07:05" */
    private fun hm(iso: String): String = if (iso.length >= 16) iso.substring(11, 16) else ""

    private fun get(url: String): String = NetLog.timed(url) { getImpl(url) }

    private fun getImpl(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // ---------------------------------------------------------------- WMO hava kodları

    enum class Kind { SUN, MOON, PARTLY_DAY, PARTLY_NIGHT, CLOUD, FOG, RAIN, SNOW, STORM }

    fun kindOf(code: Int, isDay: Boolean): Kind = when (code) {
        0, 1 -> if (isDay) Kind.SUN else Kind.MOON
        2 -> if (isDay) Kind.PARTLY_DAY else Kind.PARTLY_NIGHT
        3 -> Kind.CLOUD
        45, 48 -> Kind.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Kind.RAIN
        71, 73, 75, 77, 85, 86 -> Kind.SNOW
        95, 96, 99 -> Kind.STORM
        else -> Kind.CLOUD
    }

    fun describe(code: Int): String = when (code) {
        0 -> Lang.s(R.string.wmo_clear)
        1 -> Lang.s(R.string.wmo_mostly_clear)
        2 -> Lang.s(R.string.wmo_partly_cloudy)
        3 -> Lang.s(R.string.wmo_overcast)
        45, 48 -> Lang.s(R.string.wmo_fog)
        51, 53, 55 -> Lang.s(R.string.wmo_drizzle)
        56, 57 -> Lang.s(R.string.wmo_freezing_drizzle)
        61 -> Lang.s(R.string.wmo_light_rain)
        63 -> Lang.s(R.string.wmo_rain)
        65 -> Lang.s(R.string.wmo_heavy_rain)
        66, 67 -> Lang.s(R.string.wmo_freezing_rain)
        71 -> Lang.s(R.string.wmo_light_snow)
        73 -> Lang.s(R.string.wmo_snow)
        75 -> Lang.s(R.string.wmo_heavy_snow)
        77 -> Lang.s(R.string.wmo_snow_grains)
        80 -> Lang.s(R.string.wmo_light_showers)
        81 -> Lang.s(R.string.wmo_showers)
        82 -> Lang.s(R.string.wmo_violent_showers)
        85, 86 -> Lang.s(R.string.wmo_snow_showers)
        95 -> Lang.s(R.string.wmo_thunderstorm)
        96, 99 -> Lang.s(R.string.wmo_thunder_hail)
        else -> Lang.s(R.string.wmo_cloudy)
    }
}
