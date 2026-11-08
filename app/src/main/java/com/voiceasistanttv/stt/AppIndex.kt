package com.voiceasistanttv.stt

import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * The apps that can be opened by voice ("Netflix ac", "open Prime Video"): every app with a launcher entry (TV or normal),
 * except this one, minus the ones switched off in the settings ("Apps opened by voice"). The spoken name is compared with the
 * app's label the same forgiving way as the music app names ([VoiceRoute.soundKey]).
 */
object AppIndex {
    class Entry(val pkg: String, val label: String) {
        /** Label folded to plain letters and digits: "Prime Video" -> "primevideo". */
        val key: String = WeatherQuery.norm(label).filter { it.isLetterOrDigit() }
        val words: List<String> = WeatherQuery.norm(label).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        val sound: String by lazy { VoiceRoute.soundKey(key) }
    }

    private var cache: List<Entry> = emptyList()
    private var cacheAt = 0L

    /** All launchable apps (sorted by name); remembered for 30 s unless [force]. */
    @Synchronized
    fun list(ctx: Context, force: Boolean = false): List<Entry> {
        val now = SystemClock.elapsedRealtime()
        if (!force && cache.isNotEmpty() && now - cacheAt < 30_000L) return cache
        val pm = ctx.packageManager
        val seen = LinkedHashMap<String, Entry>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val infos = try {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
            } catch (_: Exception) {
                emptyList()
            }
            for (ri in infos) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == ctx.packageName || seen.containsKey(pkg)) continue
                val label = try {
                    ri.loadLabel(pm).toString().trim()
                } catch (_: Exception) {
                    ""
                }
                if (label.isNotEmpty()) seen[pkg] = Entry(pkg, label)
            }
        }
        cache = seen.values.sortedBy { it.label.lowercase() }
        cacheAt = now
        return cache
    }

    // ------------------------------------------------------------------ spoken name

    // "<name> ac / baslat / calistir", "open / launch / start / run <name>", "go to <name>", "<name>'e git".
    private val OPEN_VERBS = setOf(
        "ac", "acar", "acsana", "acin", "acsin", "calistir", "calistirir", "baslat", "baslatir", "open", "launch", "start", "run",
        "git"
    )
    private val FILL = setOf(
        "lutfen", "please", "uygulamasini", "uygulamasi", "uygulama", "uygulamayi", "app", "application", "the", "mi", "misin",
        "musun", "mu", "can", "you", "could", "would", "bana", "simdi", "now", "hemen", "a", "tv", "for", "me", "go", "to"
    )

    /** The app name spoken in an "open" sentence ("netflix'i ac" -> ["netflix"]), or null if it is not one. */
    fun spokenName(text: String): List<String>? {
        val toks = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (toks.isEmpty() || toks.size > 8) return null
        val n = toks.map { VoiceRoute.base(it) }.filter { it.isNotEmpty() }
        val hasVerb = n.any { it in OPEN_VERBS } || (n.size >= 3 && n[0] == "go" && n[1] == "to")
        if (!hasVerb) return null
        val name = n.filter { it !in OPEN_VERBS && it !in FILL }
        if (name.isEmpty() || name.size > 4) return null
        return name
    }

    private fun score(q: String, qs: String, e: Entry): Int {
        val k = e.key
        if (k.isEmpty()) return 0
        if (q == k) return 100
        if (k.length >= 3 && q.startsWith(k) && q.length - k.length <= 3) return 95 // "netflixi" -> "netflix"
        if (qs == e.sound) return 90
        if (q.length >= 4 && k.startsWith(q)) return 85 // "prime" -> "Prime Video"
        val first = e.words.firstOrNull()
        if (first != null && first.length >= 4 && VoiceRoute.soundKey(first) == qs) return 75 // "aurora" -> "Aurora Store TV"
        if (qs.length >= 7 && e.sound.length >= 7 && VoiceRoute.distance(qs, e.sound) <= 1) return 65
        return 0
    }

    /** Spoken names that stand for a package family when no app carries that name ("youtube" -> SmartTube, "settings" -> the TV's Settings). */
    private val SMARTTUBE: List<(Entry) -> Boolean> = listOf(
        { e: Entry -> e.pkg == "org.smarttube.stable" },
        { e: Entry -> e.pkg.startsWith("org.smarttube") },
        { e: Entry -> e.key.startsWith("smarttube") }
    )
    private val SETTINGS: List<(Entry) -> Boolean> = listOf(
        { e: Entry -> e.key == "ayarlar" || e.key == "settings" },
        { e: Entry -> e.pkg.endsWith(".settings") }
    )
    private val ALIASES: Map<String, List<(Entry) -> Boolean>> = mapOf(
        "youtube" to SMARTTUBE,
        "settings" to SETTINGS,
        "ayarlar" to SETTINGS,
        "ayar" to SETTINGS
    )

    /** The enabled app that best matches the spoken [name], or null. */
    fun find(ctx: Context, name: List<String>): Entry? {
        val q = name.joinToString("")
        if (q.length < 3) return null
        val on = list(ctx).filter { it.pkg !in Config.appsOff }
        // A direct label match wins; the alias is the fallback ("youtube" has no app of its own on this TV).
        val direct = findDirect(ctx, name)
        if (direct != null) return direct
        ALIASES[q]?.let { rules -> for (r in rules) on.firstOrNull(r)?.let { return it } }
        return null
    }

    private fun findDirect(ctx: Context, name: List<String>): Entry? {
        val q = name.joinToString("")
        if (q.length < 3) return null
        val qs = VoiceRoute.soundKey(q)
        val off = Config.appsOff
        var best: Entry? = null
        var bestScore = 0
        for (e in list(ctx)) {
            if (e.pkg in off) continue
            val s = score(q, qs, e)
            if (s > bestScore || (s == bestScore && s > 0 && best != null && e.label.length < best.label.length)) {
                best = e
                bestScore = s
            }
        }
        return if (bestScore >= 65) best else null
    }
}
