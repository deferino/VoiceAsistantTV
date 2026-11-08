package com.voiceasistanttv.stt

import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds the Spotify track for a spoken song name.
 *
 * Spotify TV answers "play from search" with its search screen, but it does play a `spotify:track:<id>` link started over
 * its media session ([MusicPlayer]). So the song name is turned into that link here, by whichever of these the user set up
 * (both need a free key; with neither, the search screen is shown):
 *  1) Spotify's own Web API: search with an app token (client credentials, no login). Spotify's own ranking, fastest.
 *     Needs a Spotify developer app (Client ID + Secret; Spotify requires Premium for the app's owner).
 *  2) MusicLink (musiclink.one, free key, limited monthly quota): the best match of Apple's public iTunes Search is mapped
 *     to its Spotify track.
 * Makes network calls: do not call from the main thread. Only the host and the duration are logged, never the song text
 * or the keys.
 */
object SpotifyLookup {
    private const val TAG = "VoiceTV"
    private const val TIMEOUT_MS = 5_000
    private const val SLOW_TIMEOUT_MS = 7_000

    private val SPOTIFY_TRACK = Regex("open\\.spotify\\.com/(?:intl-[a-zA-Z-]+/)?track/([A-Za-z0-9]{22})")

    private class HttpError(val code: Int) : IOException("HTTP $code")

    // App token of the Spotify Web API (client credentials), kept in memory only.
    private var token: String? = null
    private var tokenUntil = 0L
    private var tokenOwner = 0

    // Spotify refused the keys (typically 403: the developer app's owner has no Premium): skip the Spotify search for a
    // while, so every command does not first wait for a refusal before MusicLink is tried. Reset when the keys change.
    private var spotifySkipUntil = 0L
    private var spotifySkipOwner = 0
    private const val SKIP_MS = 30L * 60 * 1000

    private fun keysHash() = (Config.spotifyId.trim() + ":" + Config.spotifySecret.trim()).hashCode()

    private fun spotifyKeys() = Config.spotifyId.isNotBlank() && Config.spotifySecret.isNotBlank()

    /** Spotify keys are set and were not refused lately. */
    private fun spotifyUsable() =
        spotifyKeys() && !(spotifySkipOwner == keysHash() && SystemClock.elapsedRealtime() < spotifySkipUntil)

    private fun spotifyFailed(what: String, e: Exception) {
        Log.w(TAG, "spotify lookup: $what failed: ${e.javaClass.simpleName} ${e.message}")
        if (e is HttpError && (e.code == 400 || e.code == 401 || e.code == 403)) {
            spotifySkipOwner = keysHash()
            spotifySkipUntil = SystemClock.elapsedRealtime() + SKIP_MS
            Log.i(TAG, "spotify lookup: Spotify refused the keys (HTTP ${e.code}); not trying them for 30 min")
        }
    }

    /** Some way to look songs up is set up. */
    fun configured(): Boolean = spotifyUsable() || Config.musicLinkKey.isNotBlank()

    /**
     * Server roots worth warming up (DNS + TLS) while the user speaks, for the lookup path that will be tried first:
     * the Spotify Web API when its keys work (its token host is warmed by [warmUp]), else iTunes + MusicLink.
     * MusicLink is only the fallback when Spotify works, so it is not warmed then.
     */
    fun prewarmUrls(): List<String> = when {
        spotifyUsable() -> listOf("https://api.spotify.com/")
        Config.musicLinkKey.isNotBlank() -> listOf("https://itunes.apple.com/", "https://api.musiclink.one/")
        else -> emptyList()
    }

    /** Gets the Spotify token ahead of the search (while the user speaks), if it is needed. Blocking; any thread. */
    fun warmUp() {
        if (!spotifyUsable()) return
        try {
            token(false)
        } catch (e: Exception) {
            spotifyFailed("token", e)
        }
    }

    /** "spotify:track:<id>" for the best match of [query], or null (nothing found, no keys, no network, quota ...). */
    fun find(query: String): String? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val t0 = SystemClock.elapsedRealtime()
        var id: String? = null
        var how = "none"
        if (spotifyUsable()) {
            id = try {
                spotifySearch(q)
            } catch (e: Exception) {
                spotifyFailed("Spotify search", e)
                null
            }
            if (id != null) how = "spotify"
        }
        if (id == null && Config.musicLinkKey.isNotBlank()) {
            id = attempt("MusicLink") { apple(q)?.let { musicLink(it) } }
            if (id != null) how = "musiclink"
        }
        Log.i(TAG, "spotify lookup: " + (if (id != null) "found $id ($how)" else "nothing found") + " after ${SystemClock.elapsedRealtime() - t0} ms")
        return id?.let { "spotify:track:$it" }
    }

    private inline fun <T> attempt(what: String, block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "spotify lookup: $what failed: ${e.javaClass.simpleName} ${e.message}")
        null
    }

    // ---------------------------------------------------------------- Spotify Web API

    @Synchronized
    private fun token(force: Boolean): String {
        val id = Config.spotifyId.trim()
        val secret = Config.spotifySecret.trim()
        val owner = (id + ":" + secret).hashCode()
        val now = SystemClock.elapsedRealtime()
        val cur = token
        if (!force && cur != null && now < tokenUntil && owner == tokenOwner) return cur
        val basic = Base64.encodeToString("$id:$secret".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val o = JSONObject(request("https://accounts.spotify.com/api/token", "grant_type=client_credentials", "Basic $basic", TIMEOUT_MS))
        val t = o.getString("access_token")
        token = t
        tokenUntil = now + (o.optLong("expires_in", 3600L) - 60L) * 1000L
        tokenOwner = owner
        return t
    }

    private fun spotifySearch(q: String): String? {
        val url = "https://api.spotify.com/v1/search?type=track&limit=5&market=${Lang.region()}&q=${Uri.encode(q)}"
        val body = try {
            request(url, null, "Bearer ${token(false)}", TIMEOUT_MS)
        } catch (e: HttpError) {
            if (e.code != 401) throw e
            request(url, null, "Bearer ${token(true)}", TIMEOUT_MS) // the token had expired
        }
        val items = JSONObject(body).optJSONObject("tracks")?.optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            if (o.has("is_playable") && !o.optBoolean("is_playable", true)) continue
            val id = o.optString("id")
            if (id.length == 22) return id
        }
        return null
    }

    // ---------------------------------------------------------------- iTunes search + MusicLink

    /** Link of the best song for [q] in Apple's catalog (a music.apple.com song page), or null. */
    private fun apple(q: String): String? {
        val url = "https://itunes.apple.com/search?media=music&entity=song&limit=5&country=${Lang.region()}&term=${Uri.encode(q)}"
        val results = JSONObject(request(url, null, null, TIMEOUT_MS)).optJSONArray("results") ?: return null
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val trackId = o.optLong("trackId", 0L)
            if (trackId > 0L) return "https://music.apple.com/song/$trackId"
            val link = o.optString("trackViewUrl")
            if (link.startsWith("https://")) return link
        }
        return null
    }

    /** The Spotify track id of that Apple Music song, as MusicLink maps it, or null. */
    private fun musicLink(appleLink: String): String? {
        val url = "https://api.musiclink.one/v2/convert?to=spotify&q=${Uri.encode(appleLink)}"
        val o = JSONObject(request(url, null, "Bearer ${Config.musicLinkKey.trim()}", SLOW_TIMEOUT_MS))
        val link = o.optJSONObject("data")?.optJSONObject("to")?.optString("value").orEmpty()
        return SPOTIFY_TRACK.find(link)?.groupValues?.get(1)
    }

    // ---------------------------------------------------------------- HTTP

    /** GET, or POST of a form when [form] is given. Throws [HttpError] for a status outside 2xx. */
    private fun request(url: String, form: String?, authorization: String?, timeoutMs: Int): String =
        NetLog.timed(url) { requestImpl(url, form, authorization, timeoutMs) }

    private fun requestImpl(url: String, form: String?, authorization: String?, timeoutMs: Int): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("Accept", "application/json")
            if (authorization != null) c.setRequestProperty("Authorization", authorization)
            if (form != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw HttpError(code)
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
