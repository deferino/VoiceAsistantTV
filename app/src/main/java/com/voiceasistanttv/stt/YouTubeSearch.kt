package com.voiceasistanttv.stt

import android.net.Uri
import android.os.Build
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * YouTube video search (to list results that will play in SmartTube).
 *  1) The official Data API v3 (search.list) if a YouTube key is entered,
 *  2) otherwise / on failure the keyless web search endpoint (the InnerTube used by apps like SmartTube/NewPipe).
 * Makes network calls: do not call from the main thread.
 */
object YouTubeSearch {
    private const val MAX_ITEMS = 6

    class Video(
        val id: String,
        val title: String,
        val channel: String,
        val duration: String,
        val views: String = "",
        val published: String = ""
    )

    class Outcome(val videos: List<Video>, val error: String?)

    fun search(query: String, apiKey: String): Outcome {
        val q = query.trim()
        var firstError: String? = null
        if (apiKey.isNotBlank()) {
            try {
                val v = official(q, apiKey.trim())
                if (v.isNotEmpty()) return Outcome(v.take(MAX_ITEMS), null)
            } catch (e: Exception) {
                firstError = "YouTube API: " + MediaSearch.describe(e)
            }
        }
        return try {
            Outcome(innerTube(q).take(MAX_ITEMS), null)
        } catch (e: Exception) {
            Outcome(emptyList(), firstError ?: ("YouTube: " + MediaSearch.describe(e)))
        }
    }

    // ---------------------------------------------------------------- official API

    private fun official(q: String, key: String): List<Video> {
        val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=8" +
                "&regionCode=" + Lang.region() + "&relevanceLanguage=" + Lang.code() + "&q=" + Uri.encode(q) + "&key=" + Uri.encode(key)
        val items = JSONObject(request(url, null, emptyMap())).optJSONArray("items") ?: return emptyList()
        val list = ArrayList<Video>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val id = o.optJSONObject("id")?.optString("videoId").orEmpty()
            if (id.isBlank()) continue
            val sn = o.optJSONObject("snippet")
            list.add(
                Video(
                    id,
                    unescape(sn?.optString("title").orEmpty()),
                    unescape(sn?.optString("channelTitle").orEmpty()),
                    "",
                    "",
                    sn?.optString("publishedAt").orEmpty().take(10)
                )
            )
        }
        return list
    }

    // ---------------------------------------------------------------- keyless web endpoint

    private fun innerTube(q: String): List<Video> {
        val client = JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", "2.20251001.00.00")
            .put("hl", Lang.code())
            .put("gl", Lang.region())
        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("query", q)
            .put("params", "EgIQAQ==") // videos only
        val headers = mapOf(
            "Content-Type" to "application/json",
            "X-YouTube-Client-Name" to "1",
            "X-YouTube-Client-Version" to "2.20251001.00.00",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
            "Accept-Language" to Lang.acceptLanguage()
        )
        val root = JSONObject(request("https://www.youtube.com/youtubei/v1/search?prettyPrint=false", body.toString(), headers))
        val sections = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnSearchResultsRenderer")
            ?.optJSONObject("primaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()
        val list = ArrayList<Video>()
        for (i in 0 until sections.length()) {
            val inner = sections.optJSONObject(i)?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: continue
            collectVideos(inner, list)
        }
        return list
    }

    private fun collectVideos(arr: JSONArray, out: MutableList<Video>) {
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i)?.optJSONObject("videoRenderer") ?: continue
            val id = v.optString("videoId")
            if (id.isBlank()) continue
            val title = runsText(v.optJSONObject("title"))
            val channel = runsText(v.optJSONObject("ownerText")).ifBlank { runsText(v.optJSONObject("longBylineText")) }
            val dur = v.optJSONObject("lengthText")?.optString("simpleText").orEmpty()
            val views = runsText(v.optJSONObject("shortViewCountText")).ifBlank { runsText(v.optJSONObject("viewCountText")) }
            val published = runsText(v.optJSONObject("publishedTimeText"))
            out.add(Video(id, title, channel, dur, views, published))
        }
    }

    private fun runsText(o: JSONObject?): String {
        if (o == null) return ""
        o.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        return sb.toString()
    }

    // ---------------------------------------------------------------- helpers

    private fun unescape(s: String): String =
        if (Build.VERSION.SDK_INT >= 24) Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()
        else @Suppress("DEPRECATION") Html.fromHtml(s).toString()

    private fun request(url: String, body: String?, headers: Map<String, String>): String =
        NetLog.timed(url) { requestImpl(url, body, headers) }

    private fun requestImpl(url: String, body: String?, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
