package com.voiceasistanttv.stt

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent

/**
 * Short utility commands. Turkish and English are always both recognized (independent of the UI language):
 *  - music control: "durdur", "devam et", "sonraki sarki", "onceki sarki" / "pause", "resume", "next song", "previous song",
 *  - volume: "sesi kis", "sesi ac", "sessize al", "sesi yuzde 30 yap" / "volume up", "mute", "set volume to 30",
 *  - sleep timer: "30 dakika sonra muzigi durdur", "yarim saat sonra kapat" / "stop the music in 30 minutes",
 *    and "zamanlayiciyi iptal et" / "cancel the timer".
 *
 * Matching is done on lower-case, ASCII-folded words ([WeatherQuery.norm]). A command matches only if EVERY word of the
 * sentence belongs to it (trigger words plus harmless filler words), so a longer sentence ("pause menu of the game
 * interstellar") is still an ordinary search.
 */
object VoiceCommands {
    private const val TAG = "VoiceTV"
    private const val SETTLE_MS = 350L

    enum class Media { PAUSE, PLAY, NEXT, PREVIOUS }

    sealed class Cmd {
        class MediaKey(val key: Media) : Cmd()
        class VolumeStep(val up: Boolean) : Cmd()
        class VolumeSet(val percent: Int) : Cmd()
        class Mute(val mute: Boolean) : Cmd()
        class Timer(val minutes: Int) : Cmd()
        object CancelTimer : Cmd()
    }

    fun parse(text: String): Cmd? {
        val w = words(text)
        if (w.isEmpty() || w.size > 9) return null
        return timer(w) ?: volume(w) ?: media(w)
    }

    /** For the debug log: a short name of the command. */
    fun describe(c: Cmd?): String = when (c) {
        null -> "none"
        is Cmd.MediaKey -> "media:" + c.key.name.lowercase()
        is Cmd.VolumeStep -> if (c.up) "volume:up" else "volume:down"
        is Cmd.VolumeSet -> "volume:set:" + c.percent
        is Cmd.Mute -> if (c.mute) "volume:mute" else "volume:unmute"
        is Cmd.Timer -> "timer:" + c.minutes + "min"
        Cmd.CancelTimer -> "timer:cancel"
    }

    // ------------------------------------------------------------------ words

    /** Folded words; digits and letters glued together are split ("45dk" -> "45", "dk"). */
    private fun words(text: String): List<String> {
        val n = WeatherQuery.norm(text)
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("(\\d)([a-z])"), "\$1 \$2")
            .replace(Regex("([a-z])(\\d)"), "\$1 \$2")
        return n.split(' ').filter { it.isNotEmpty() }
    }

    private fun Set<String>.has(s: String?): Boolean = s != null && s in this

    private val ONES = mapOf(
        "sifir" to 0, "bir" to 1, "iki" to 2, "uc" to 3, "dort" to 4, "bes" to 5, "alti" to 6, "yedi" to 7, "sekiz" to 8,
        "dokuz" to 9,
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
    )
    private val TENS = mapOf(
        "on" to 10, "yirmi" to 20, "otuz" to 30, "kirk" to 40, "elli" to 50, "altmis" to 60, "yetmis" to 70, "seksen" to 80,
        "doksan" to 90,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80,
        "ninety" to 90
    )

    /** Number at [i] and how many words it spans: digits, "otuz", "yirmi bes", "twenty five", "yuz", "hundred". */
    private fun numberAt(w: List<String>, i: Int): Pair<Int, Int>? {
        val t = w[i]
        if (t.length <= 3 && t.all { it.isDigit() }) return t.toInt() to 1
        if (t == "yuz" || t == "hundred") return 100 to 1
        val tens = TENS[t]
        if (tens != null) {
            val next = w.getOrNull(i + 1)?.let { ONES[it] }
            if (next != null && next in 1..9) return (tens + next) to 2
            return tens to 1
        }
        val ones = ONES[t]
        if (ones != null) return ones to 1
        return null
    }

    // ------------------------------------------------------------------ sleep timer

    private val MIN_UNITS = setOf("dakika", "dakikalik", "dk", "dak", "min", "mins", "minute", "minutes", "mn")
    private val HOUR_UNITS = setOf("saat", "saatlik", "sa", "hour", "hours", "hr", "hrs", "h")
    private val STOP_WORDS = setOf(
        "durdur", "duraklat", "kapat", "kapatir", "kapatsin", "sonlandir", "dursun", "dur", "stop", "pause", "off", "end",
        "shut", "turn"
    )
    private val TIMER_WORDS = setOf("zamanlayici", "zamanlayicisi", "zamanlayiciyi", "zamanlayicisini", "sayac", "timer")
    private val SLEEP_WORDS = setOf("uyku", "sleep")
    private val CANCEL_WORDS = setOf("iptal", "sil", "kaldir", "cancel", "clear", "delete", "remove", "disable", "reset")

    /** Minutes named anywhere in the sentence ("30 dakika", "yarim saat", "half an hour", "bir buçuk saat"), or null. */
    private fun durationMinutes(w: List<String>): Int? {
        var total = 0.0
        var found = false
        var i = 0
        while (i < w.size) {
            val t = w[i]
            if (t == "yarim" && HOUR_UNITS.has(w.getOrNull(i + 1))) {
                total += 30.0; found = true; i += 2; continue
            }
            if (t == "half") {
                var j = i + 1
                if (w.getOrNull(j) == "an" || w.getOrNull(j) == "a") j++
                if (HOUR_UNITS.has(w.getOrNull(j))) {
                    total += 30.0; found = true; i = j + 1; continue
                }
            }
            if ((t == "an" || t == "a") && HOUR_UNITS.has(w.getOrNull(i + 1))) {
                total += 60.0; found = true; i += 2; continue
            }
            val num = numberAt(w, i)
            if (num != null) {
                var j = i + num.second
                var half = 0.0
                if (w.getOrNull(j) == "bucuk") { half = 0.5; j++ }
                val unit = w.getOrNull(j)
                if (MIN_UNITS.has(unit)) {
                    total += num.first; found = true; i = j + 1; continue
                }
                if (HOUR_UNITS.has(unit)) {
                    total += (num.first + half) * 60.0; found = true; i = j + 1; continue
                }
            }
            i++
        }
        return if (found) Math.round(total).toInt() else null
    }

    private fun timer(w: List<String>): Cmd? {
        val hasTimerWord = w.any { it in TIMER_WORDS }
        val minutes = durationMinutes(w)
        if (minutes == null) {
            // "zamanlayiciyi iptal et" / "cancel the sleep timer" / "turn off the timer"
            if (hasTimerWord && w.any { it in CANCEL_WORDS || it in STOP_WORDS }) return Cmd.CancelTimer
            return null
        }
        val hasStop = w.any { it in STOP_WORDS }
        val hasSleep = w.any { it in SLEEP_WORDS }
        // "set a timer for 5 minutes" alone is not a sleep timer; it needs "stop / close / sleep".
        if (!hasStop && !hasSleep) return null
        if (minutes !in 1..720) return null
        return Cmd.Timer(minutes)
    }

    // ------------------------------------------------------------------ volume

    private val VOL_WORDS = setOf("ses", "sesi", "sesini", "sesin", "seviyesi", "seviyesini", "volume", "sound")
    private val UP_WORDS = setOf(
        "ac", "acar", "acsana", "acin", "yukselt", "yukseltir", "artir", "arttir", "yuksel", "yuksek", "cogalt",
        "up", "increase", "raise", "louder", "higher", "boost", "bump"
    )
    private val DOWN_WORDS = setOf(
        "kis", "kisar", "kissana", "kisin", "azalt", "dusur", "alcalt", "alcak", "down", "decrease", "lower", "quieter",
        "softer", "reduce", "lessen"
    )
    private val VOL_FILL = setOf(
        "ses", "sesi", "sesini", "sesin", "volume", "sound", "the", "a", "it", "please", "lutfen", "biraz", "daha", "bir",
        "az", "little", "bit", "bi", "tad", "more", "some", "slightly", "turn", "make", "tv", "televizyon", "televizyonu",
        "bunu", "e", "i", "u", "yi", "yu", "ya", "ye"
    )
    private val SET_FILL = setOf(
        "yuzde", "percent", "yap", "ayarla", "ayarlar", "set", "to", "at", "level", "seviye", "seviyesi", "seviyesini",
        "olsun", "lutfen", "please", "the", "volume", "ses", "sesi", "sesini", "sound", "a", "e", "i", "u", "ya", "ye", "ile",
        "kadar", "my", "tv", "turn", "down", "up", "lower", "raise", "increase", "decrease", "reduce", "kis", "dusur",
        "azalt", "artir", "arttir", "yukselt", "ac"
    )

    private fun volume(w: List<String>): Cmd? {
        val hasVol = w.any { it in VOL_WORDS }

        // Set to a level: "sesi 30 yap", "ses seviyesi yuzde 30", "set volume to 50 percent".
        if (hasVol) {
            var level = -1
            val rest = ArrayList<String>()
            var i = 0
            while (i < w.size) {
                val n = if (level < 0) numberAt(w, i) else null
                if (n != null) {
                    level = n.first
                    i += n.second
                } else {
                    rest.add(w[i])
                    i++
                }
            }
            if (level in 0..100 && rest.all { it in SET_FILL }) return Cmd.VolumeSet(level)
        }

        // "sessiz" family: mute / unmute.
        val sessiz = w.any { it.startsWith("sessiz") }
        val unmuteWord = w.any { it == "unmute" }
        if (unmuteWord) return Cmd.Mute(false)
        if (sessiz) {
            val cancel = w.any { it in setOf("kapat", "cik", "cikar", "iptal", "kaldir", "ac", "coz") } && "al" !in w
            val allowed = setOf("sessiz", "sessizi", "sessize", "sessizden", "sessizligi", "al", "yap", "kapat", "cik", "cikar",
                "iptal", "kaldir", "ac", "coz", "et", "lutfen", "tv", "televizyon", "televizyonu", "bunu", "e", "i", "u", "geri")
            if (w.all { it in allowed }) return Cmd.Mute(!cancel)
        }
        if (w.any { it == "mute" || it == "sustur" || it == "silence" }) {
            val allowed = setOf("mute", "sustur", "silence", "the", "tv", "sound", "volume", "it", "please", "lutfen", "ses",
                "sesi", "sesini", "televizyon", "televizyonu", "bunu", "my")
            if (w.all { it in allowed }) return Cmd.Mute(true)
        }
        if (hasVol && w.any { it == "kapat" } && w.all { it in VOL_FILL || it == "kapat" }) return Cmd.Mute(true)
        if (hasVol && "geri" in w && w.any { it == "ac" } && w.all { it in VOL_FILL || it == "geri" || it == "ac" }) {
            return Cmd.Mute(false)
        }

        // One step louder / quieter.
        val up = w.any { it in UP_WORDS }
        val down = w.any { it in DOWN_WORDS }
        if (up == down) return null
        val context = hasVol || "turn" in w || "make" in w || w.any { it == "louder" || it == "quieter" || it == "softer" }
        if (!context) return null
        val extra = if (up) UP_WORDS else DOWN_WORDS
        if (w.all { it in VOL_FILL || it in extra }) return Cmd.VolumeStep(up)
        return null
    }

    // ------------------------------------------------------------------ music control

    private val MEDIA_FILL = setOf(
        "muzigi", "muzik", "muzige", "sarkiyi", "sarki", "sarkiya", "parcayi", "parca", "videoyu", "video", "the", "music",
        "song", "track", "playback", "playing", "it", "this", "bunu", "lutfen", "please", "simdi", "now", "bir", "e", "i", "u",
        "ya", "ye", "a"
    )
    private val NOUNS = setOf("muzigi", "muzik", "muzige", "sarkiyi", "sarki", "sarkiya", "parcayi", "music", "song")

    private fun media(w: List<String>): Cmd? {
        fun only(trigger: Set<String>, extra: Set<String>): Boolean =
            w.any { it in trigger } && w.all { it in trigger || it in extra || it in MEDIA_FILL }

        // Next / previous first ("sonraki sarkiya gec" must not be a plain "gec").
        val nextWords = setOf("sonraki", "sonrakine", "siradaki", "siradakine", "next", "skip", "atla", "atlat")
        if (only(nextWords, setOf("gec", "gecer", "git", "to", "ahead", "forward", "for", "me", "play", "go"))) {
            return Cmd.MediaKey(Media.NEXT)
        }
        if (w.any { it == "gec" } && w.any { it in NOUNS } && w.all { it == "gec" || it in MEDIA_FILL }) {
            return Cmd.MediaKey(Media.NEXT)
        }
        val prevWords = setOf("onceki", "oncekine", "previous", "prev")
        if (only(prevWords, setOf("don", "gec", "git", "to", "go", "play", "last", "for", "me"))) {
            return Cmd.MediaKey(Media.PREVIOUS)
        }

        // Pause / stop.
        val pauseWords = setOf("durdur", "duraklat", "dur", "pause", "stop", "beklet")
        if (only(pauseWords, setOf("et", "lutfen", "please"))) return Cmd.MediaKey(Media.PAUSE)
        val offWords = setOf("kapat", "off", "turn")
        if (w.any { it in offWords } && w.any { it in NOUNS } && w.all { it in offWords || it in MEDIA_FILL }) {
            return Cmd.MediaKey(Media.PAUSE)
        }

        // Resume / play.
        if (only(setOf("devam"), setOf("et", "ettir", "edelim", "edebilir", "edebilirsin", "mi", "misin"))) {
            return Cmd.MediaKey(Media.PLAY)
        }
        if (only(setOf("resume", "continue", "keep"), setOf("playing", "playback"))) return Cmd.MediaKey(Media.PLAY)
        val playVerbs = setOf("play", "oynat", "cal", "calsana", "baslat", "ac", "surdur")
        if (w.size == 1 && w[0] in setOf("play", "oynat", "cal", "calsana")) return Cmd.MediaKey(Media.PLAY)
        if (w.any { it in playVerbs } && w.any { it in NOUNS } && w.all { it in playVerbs || it in MEDIA_FILL }) {
            return Cmd.MediaKey(Media.PLAY)
        }
        return null
    }

    // ------------------------------------------------------------------ doing it

    private fun audio(ctx: Context): AudioManager =
        ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Sends a media button press to whatever player was active last (BitChord, Spotify, a video app ...). */
    fun sendMedia(ctx: Context, key: Media): Boolean {
        val code = when (key) {
            Media.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            Media.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            Media.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            Media.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        return try {
            val am = audio(ctx)
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            Log.i(TAG, "command: media key ${key.name.lowercase()} sent")
            true
        } catch (e: Exception) {
            Log.w(TAG, "command: media key failed: ${e.message}")
            false
        }
    }

    private fun percent(am: AudioManager): Int {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0
        return Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max)
    }

    /**
     * Walks the music volume to [target] one notch at a time and waits for each notch to land. A TV with an HDMI-ARC sound bar takes only
     * one notch per call (a setStreamVolume to 30 moves it by 1), so jumping straight to the target does not work there.
     * Blocking: call it from a worker thread.
     */
    private fun walkTo(ctx: Context, target: Int) {
        val am = audio(ctx)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val goal = target.coerceIn(0, max)
        fun read() = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        var calls = 0
        var stuck = 0
        try {
            if (goal > read() && am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            }
            // The value read right after a call is the optimistic one; the TV settles on its real value a few hundred ms later,
            // so every notch gets a settle time, and the result is checked again once things have gone quiet.
            for (round in 0 until 3) {
                var cur = read()
                while (cur != goal && calls < 60 && stuck < 3) {
                    val before = cur
                    val dir = if (goal > cur) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                    val flags = if (Math.abs(goal - cur) <= 1) AudioManager.FLAG_SHOW_UI else 0
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, flags)
                    calls++
                    Thread.sleep(SETTLE_MS)
                    cur = read()
                    if (cur == before) {
                        Thread.sleep(SETTLE_MS)
                        cur = read()
                    }
                    if (cur == before) stuck++ else stuck = 0
                }
                Thread.sleep(SETTLE_MS)
                if (read() == goal || calls >= 60 || stuck >= 3) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "command: volume failed: ${e.message}")
        }
        Log.i(TAG, "command: volume walk to $goal (max $max) -> ${read()} in $calls calls")
    }

    /** One notch is one press of the remote's volume key; a voice step is five of them (a twentieth of the range). Blocking. */
    fun stepVolume(ctx: Context, up: Boolean): Int {
        val am = audio(ctx)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val step = maxOf(1, Math.round(max / 20f))
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        walkTo(ctx, if (up) cur + step else cur - step)
        val p = percent(am)
        Log.i(TAG, "command: volume ${if (up) "up" else "down"} -> $p%")
        return p
    }

    /** Sets the volume to [percent] of the maximum. Returns the volume in percent afterwards. Blocking. */
    fun setVolume(ctx: Context, percent: Int): Int {
        val am = audio(ctx)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        walkTo(ctx, Math.round(max * percent.coerceIn(0, 100) / 100f))
        val p = percent(am)
        Log.i(TAG, "command: volume set $percent% -> $p%")
        return p
    }

    fun setMute(ctx: Context, mute: Boolean) {
        try {
            audio(ctx).adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (e: Exception) {
            Log.w(TAG, "command: mute failed: ${e.message}")
        }
        Log.i(TAG, "command: ${if (mute) "mute" else "unmute"}")
    }
}
