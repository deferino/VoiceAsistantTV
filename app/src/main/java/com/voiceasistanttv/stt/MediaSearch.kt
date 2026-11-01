package com.voiceasistanttv.stt

import android.net.Uri
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.text.Normalizer
import java.util.Locale

/**
 * Converts the spoken movie/series name into a title list (because Nuvio has no search link).
 *  - Default: Cinemeta (Stremio's public, keyless IMDb catalog).
 *  - If a TMDB key is entered: TMDB (also matches Turkish titles, language=tr-TR).
 * Makes network calls: do not call from the main thread.
 */
object MediaSearch {
    private const val MAX_ITEMS = 8

    class Item(
        val type: String, // "movie" | "series"
        val imdbId: String, // "tt…" or empty (TMDB result; resolved when picked)
        val title: String,
        val year: String,
        val tmdbId: Long = 0L,
        val poster: String = "",
        val overview: String = "",
        val rating: String = "",
        val genres: String = ""
    ) {
        val typeLabel: String get() = if (type == "movie") Lang.s(R.string.type_movie) else Lang.s(R.string.type_series)
    }

    class Outcome(val items: List<Item>, val error: String?)

    fun search(query: String, tmdbKey: String): Outcome {
        val q = query.trim()
        var tmdbError: String? = null
        var items: List<Item> = emptyList()
        if (tmdbKey.isNotBlank()) {
            try {
                items = tmdb(q, tmdbKey.trim())
            } catch (e: Exception) {
                tmdbError = "TMDB: " + describe(e)
            }
        }
        if (items.isEmpty()) {
            try {
                items = cinemeta(q)
            } catch (e: Exception) {
                return Outcome(emptyList(), tmdbError ?: ("Cinemeta: " + describe(e)))
            }
        }
        return Outcome(rank(items, q).take(MAX_ITEMS), null)
    }

    /** Finds the IMDb id of a TMDB result (the id Nuvio opens most reliably). */
    fun resolveImdb(item: Item, tmdbKey: String): String? = try {
        val kind = if (item.type == "movie") "movie" else "tv"
        val bearer = isBearer(tmdbKey)
        val url = "https://api.themoviedb.org/3/$kind/${item.tmdbId}/external_ids" +
                if (bearer) "" else "?api_key=" + Uri.encode(tmdbKey.trim())
        JSONObject(get(url, if (bearer) tmdbKey.trim() else null)).optString("imdb_id").takeIf { it.startsWith("tt") }
    } catch (_: Exception) {
        null
    }

    // ---------------------------------------------------------------- Cinemeta

    private fun cinemeta(q: String): List<Item> {
        val types = arrayOf("movie", "series")
        val results = arrayOfNulls<List<Item>>(2)
        val errors = arrayOfNulls<Exception>(2)
        val threads = types.indices.map { i ->
            Thread {
                try {
                    results[i] = cinemetaType(types[i], q)
                } catch (e: Exception) {
                    errors[i] = e
                }
            }.also { it.start() }
        }
        threads.forEach { it.join() }
        if (results[0] == null && results[1] == null) throw (errors[0] ?: errors[1] ?: IOException(Lang.s(R.string.err_no_response)))
        return results[0].orEmpty() + results[1].orEmpty()
    }

    private fun cinemetaType(type: String, q: String): List<Item> {
        val url = "https://v3-cinemeta.strem.io/catalog/$type/top/search=" + Uri.encode(q) + ".json"
        val metas = JSONObject(get(url, null)).optJSONArray("metas") ?: return emptyList()
        val list = ArrayList<Item>()
        for (i in 0 until minOf(metas.length(), 8)) {
            val m = metas.optJSONObject(i) ?: continue
            val id = m.optString("imdb_id").ifBlank { m.optString("id") }
            if (!id.startsWith("tt")) continue
            val year = m.optString("year").ifBlank { m.optString("releaseInfo") }.take(9)
            val genreArr = m.optJSONArray("genres") ?: m.optJSONArray("genre")
            val genres = ArrayList<String>()
            if (genreArr != null) for (g in 0 until minOf(genreArr.length(), 3)) genres.add(genreArr.optString(g))
            val imdb = m.optString("imdbRating")
            list.add(
                Item(
                    type, id, m.optString("name"), year,
                    poster = m.optString("poster"),
                    overview = m.optString("description"),
                    rating = if (imdb.isNotBlank()) "IMDb $imdb" else "",
                    genres = genres.joinToString(", ")
                )
            )
        }
        return list
    }

    // ---------------------------------------------------------------- TMDB

    private fun isBearer(key: String) = key.trim().length > 40 // if it is a v4 "Read Access Token" (JWT)

    private fun tmdb(q: String, key: String): List<Item> {
        val bearer = isBearer(key)
        val url = "https://api.themoviedb.org/3/search/multi?language=" + Lang.tmdbLang() + "&include_adult=false&query=" +
                Uri.encode(q) + if (bearer) "" else "&api_key=" + Uri.encode(key)
        val arr = JSONObject(get(url, if (bearer) key else null)).optJSONArray("results") ?: return emptyList()
        val list = ArrayList<Item>()
        for (i in 0 until minOf(arr.length(), 12)) {
            val o = arr.optJSONObject(i) ?: continue
            val kind = o.optString("media_type")
            if (kind != "movie" && kind != "tv") continue
            val isMovie = kind == "movie"
            val title = (if (isMovie) o.optString("title") else o.optString("name"))
                .ifBlank { o.optString("original_title").ifBlank { o.optString("original_name") } }
            val date = o.optString(if (isMovie) "release_date" else "first_air_date")
            val path = o.optString("poster_path")
            val vote = o.optDouble("vote_average", 0.0)
            list.add(
                Item(
                    if (isMovie) "movie" else "series", "", title, date.take(4), o.optLong("id"),
                    poster = if (path.isNotBlank() && path != "null") "https://image.tmdb.org/t/p/w185$path" else "",
                    overview = o.optString("overview"),
                    rating = if (vote > 0.0) "TMDB " + String.format(Locale.US, "%.1f", vote) else ""
                )
            )
        }
        return list
    }

    // ---------------------------------------------------------------- helpers

    private fun norm(s: String): String {
        val folded = Normalizer.normalize(s.replace('ı', 'i').replace('İ', 'I'), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return folded.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    }

    /** Closest matches to the searched name first; ties keep the source order. */
    private fun rank(items: List<Item>, q: String): List<Item> {
        val nq = norm(q)
        fun score(it: Item): Int {
            val t = norm(it.title)
            return when {
                t == nq -> 0
                t.startsWith(nq) -> 1
                t.contains(nq) -> 2
                else -> 3
            }
        }
        return items.sortedBy { score(it) }
    }

    private fun get(url: String, bearer: String?): String = NetLog.timed(url) { getImpl(url, bearer) }

    private fun getImpl(url: String, bearer: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/json")
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer $bearer")
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun describe(e: Exception): String = when (e) {
        is UnknownHostException -> Lang.s(R.string.err_no_internet)
        is SocketTimeoutException -> Lang.s(R.string.err_timeout)
        else -> e.message ?: e.javaClass.simpleName
    }
}
