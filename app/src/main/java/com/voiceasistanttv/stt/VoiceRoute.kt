package com.voiceasistanttv.stt

/**
 * App-prefixed voice commands (both languages, independent of the UI language):
 *  - "bitchord <song>" / "spotify <song>" play the song in that music app ("spotify duman bal ac"),
 *  - "music / song ... <song>" or "<song> cal / dinle" play it in the music app chosen in the settings ([App.MUSIC]),
 *  - "nuvio <title>" and "smarttube <query>" only search in that app,
 *  - "spotify ac" (an app name and nothing else, with an "open" word) just opens the app (empty query).
 * The app name may also come last ("duman bal bitchord'da cal", "play duman bal on spotify").
 *
 * Speech recognition often writes foreign app names the way they sound in Turkish ("bitçörd", "spotifay", "smart tüp"),
 * so names are not matched letter by letter: words are reduced to a sound key (see [soundKey]) and compared to the key
 * of each app name, with at most one letter of difference for the longer names.
 * Matching is done on lower-case ASCII-folded words; the query keeps the original spelling.
 */
object VoiceRoute {
    enum class App { BITCHORD, SPOTIFY, NUVIO, SMARTTUBE, MUSIC }

    class Route(val app: App, val query: String)

    // Spoken verbs around the query ("ac" = open/play, "cal" = play, "ara" = search ...); dropped from the query.
    private val VERBS = setOf(
        "ac", "cal", "calsana", "calsin", "oynat", "baslat", "ara", "arat", "bul",
        "play", "open", "start", "launch", "calistir", "search", "find", "please", "lutfen",
        "dinle", "dinlet", "listen"
    )

    // Verbs that mean "music" by themselves ("duman bal cal", "tarkan simarik dinle", "listen to ...").
    private val MUSIC_VERBS = setOf("cal", "calsana", "calsin", "dinle", "dinlet", "listen")

    // Verbs that, after only an app name, mean "open the app".
    private val OPEN_VERBS = setOf("ac", "baslat", "open", "start", "launch", "calistir")

    // Words that name music in general ("muzik ac duman bal", "play music duman bal").
    private val MUSIC_WORDS = setOf("muzik", "sarki", "music", "song")

    // Linking words next to the app name ("bitchord'da", "on spotify", "listen to ...").
    private val JOIN = setOf("da", "de", "ta", "te", "ile", "on", "in", "with", "to")

    // Case endings glued to an app name ("spotifyda", "bitchorddan").
    private val SUFFIXES = listOf("dan", "den", "da", "de", "ta", "te")

    // Letters that sound alike for these names (applied in order, on ASCII-folded text).
    private val PHONETIC = listOf(
        "ch" to "c", "sh" to "s", "ph" to "f", "q" to "c", "k" to "c", "w" to "v",
        "x" to "cs", "y" to "i", "d" to "t", "p" to "b"
    )

    /**
     * Sound key: letters that sound alike are merged ("ch/k/q" -> c, "d" -> t, "p" -> b, "y" -> i ...), doubled consonants
     * and runs of vowels collapse to one "V", and a final vowel is dropped. "bitchord", "bitcord", "bitkort", "bit cord"
     * (joined) all become "bVtcVrt"; "spotify", "spotifay", "spodifi" become "sbVtVf".
     */
    internal fun soundKey(raw: String): String {
        var s = WeatherQuery.norm(raw).filter { it.isLetterOrDigit() }
        for ((a, b) in PHONETIC) s = s.replace(a, b)
        val sb = StringBuilder()
        var last = ' '
        for (c in s) {
            if (c in "aeiou") {
                if (last !in "aeiou") sb.append('V')
            } else if (c != last) {
                sb.append(c)
            }
            last = c
        }
        var r = sb.toString()
        if (r.length > 3 && r.endsWith("V")) r = r.dropLast(1)
        return r
    }

    internal fun distance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private val KEYS: List<Pair<App, String>> by lazy {
        listOf(
            App.BITCHORD to soundKey("bitchord"),
            App.SPOTIFY to soundKey("spotify"),
            App.SMARTTUBE to soundKey("smarttube"),
            App.NUVIO to soundKey("nuvio")
        )
    }

    /** App named by this folded word (or two joined words), tolerating how the speech recognizer wrote it. */
    private fun appOf(w: String): App? {
        if (w.length < 3) return null
        val forms = ArrayList<String>(4)
        forms.add(w)
        for (s in SUFFIXES) if (w.endsWith(s) && w.length - s.length >= 3) forms.add(w.dropLast(s.length))
        for (f in forms) {
            val k = soundKey(f)
            for ((app, key) in KEYS) {
                // "nuvio" is short ("nova", "neva" sound the same): it must also start with "nu".
                if (app == App.NUVIO) {
                    if (k == key && f.startsWith("nu")) return app
                    continue
                }
                if (k == key || (key.length >= 7 && distance(k, key) <= 1)) return app
            }
        }
        return null
    }

    /** Lower-case, folded, punctuation trimmed, suffix after an apostrophe dropped. */
    internal fun base(tok: String): String {
        var s = WeatherQuery.norm(tok)
        val a = s.indexOfFirst { it == '\'' || it == '’' || it == 'ʼ' }
        if (a >= 0) s = s.substring(0, a)
        return s.trim { !it.isLetterOrDigit() }
    }

    /** App named by the words at [i] (one or two words), with how many words it spans. */
    private fun trigAt(n: List<String>, i: Int, end: Int): Pair<App, Int>? {
        appOf(n[i])?.let { return it to 1 }
        if (i + 1 < end) appOf(n[i] + n[i + 1])?.let { return it to 2 }
        return null
    }

    /** Words that are only app names (what a recognizer sometimes makes up from a vocabulary hint): not a real query. */
    private fun onlyNames(n: List<String>, from: Int, to: Int): Boolean {
        if (to <= from) return false
        for (i in from until to) if (appOf(n[i]) == null) return false
        return true
    }

    private fun build(app: App, toks: List<String>, n: List<String>, from0: Int, to0: Int, openIntent: Boolean): Route? {
        var from = from0
        var to = to0
        while (from < to && (n[from] in JOIN || n[from] in VERBS)) from++
        while (to > from && (n[to - 1] in JOIN || n[to - 1] in VERBS)) to--
        val q = toks.subList(from, to).joinToString(" ").trim { !it.isLetterOrDigit() }
        if (q.isEmpty()) return if (openIntent && app != App.MUSIC) Route(app, "") else null
        if (onlyNames(n, from, to)) return null
        return Route(app, q)
    }

    fun parse(text: String): Route? {
        val toks = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (toks.isEmpty() || toks.size > 16) return null
        val n = toks.map { base(it) }
        var s = 0
        while (s < n.size && n[s] in VERBS) s++
        var e = n.size
        while (e > s && n[e - 1] in VERBS) e--
        if (s >= e) return null
        val verbs = HashSet<String>().apply {
            addAll(n.subList(0, s))
            addAll(n.subList(e, n.size))
        }
        val open = verbs.any { it in OPEN_VERBS }

        // App name first: "bitchord duman bal ac"
        trigAt(n, s, e)?.let { (app, len) -> return build(app, toks, n, s + len, e, open) }

        // App name last: "duman bal spotify'da cal"
        var e2 = e
        while (e2 > s && n[e2 - 1] in JOIN) e2--
        for (len in 2 downTo 1) {
            val st = e2 - len
            if (st < s) continue
            val t = trigAt(n, st, e2) ?: continue
            if (t.second == len) return build(t.first, toks, n, s, st, open)
        }

        // No app named: a music request for the app chosen in the settings.
        if (toks.size < 2) return null
        if (verbs.any { it in MUSIC_VERBS }) return build(App.MUSIC, toks, n, s, e, false)
        if (n[s] in MUSIC_WORDS && e - s >= 2 && (verbs.isNotEmpty() || n[s + 1] in VERBS)) {
            return build(App.MUSIC, toks, n, s + 1, e, false)
        }
        return null
    }

    /** Names for the speech recognizer's hint (Whisper `prompt`): written the way they should come out. */
    const val NAME_HINT = "BitChord, Spotify, Nuvio, SmartTube."
}
