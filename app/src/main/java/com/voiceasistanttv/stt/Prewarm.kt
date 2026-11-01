package com.voiceasistanttv.stt

import android.os.SystemClock
import android.util.Log
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

/**
 * Kullanıcı konuşurken, sonra gerekecek sunuculara boş bir HEAD isteği atıp bağlantıyı (DNS + TCP + TLS) açık tutar.
 * Böylece konuşma bitince STT ve arama istekleri hazır bağlantıyı kullanır (TV'de soğuk TMDB isteği ~4,5 sn sürebiliyordu).
 * Hiçbir kullanıcı verisi gönderilmez; yalnızca sunucu köküne HEAD yapılır.
 */
object Prewarm {
    private const val TAG = "VoiceTV"

    /** @param withSearch arama sunucularını da ısıt (yalnız asistan yolunda; uygulamalardan gelen dikte isteğinde değil). */
    fun forAssist(withSearch: Boolean) {
        val urls = LinkedHashSet<String>()
        origin(Config.baseUrl)?.let { urls.add(it) }
        if (withSearch) {
            val t = Config.searchTarget
            if (t == Config.TARGET_BOTH || t == Config.TARGET_SMARTTUBE) {
                urls.add("https://www.youtube.com/")
                urls.add("https://i.ytimg.com/")
            }
            if (t == Config.TARGET_BOTH || t == Config.TARGET_NUVIO) {
                if (Config.tmdbKey.isNotBlank()) {
                    urls.add("https://api.themoviedb.org/")
                    urls.add("https://image.tmdb.org/")
                } else {
                    urls.add("https://v3-cinemeta.strem.io/")
                }
            }
        }
        for (u in urls) {
            Thread({ one(u) }, "VoiceTV-prewarm").apply { isDaemon = true }.start()
        }
    }

    /** Yalnız https sunucuların kökü (yerel/http adreslerde TLS maliyeti yok, ısıtma gereksiz). */
    private fun origin(baseUrl: String): String? = try {
        val u = URL(baseUrl.trim())
        if (u.protocol == "https") "https://" + u.host + (if (u.port > 0) ":" + u.port else "") + "/" else null
    } catch (_: Exception) {
        null
    }

    private fun one(url: String) {
        val t0 = SystemClock.elapsedRealtime()
        val host = try {
            URL(url).host
        } catch (_: Exception) {
            return
        }
        try {
            val addrs = InetAddress.getAllByName(host)
            val dns = SystemClock.elapsedRealtime() - t0
            val v6 = addrs.count { it is Inet6Address }
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "HEAD"
            c.instanceFollowRedirects = false
            c.connectTimeout = 7000
            c.readTimeout = 7000
            val code = c.responseCode
            // Akışı kapatmak bağlantıyı havuza geri verir (disconnect() çağırma: soketi kapatır).
            (if (code >= 400) c.errorStream else c.inputStream)?.close()
            Log.i(TAG, "prewarm $host: dns=$dns ms (v4=${addrs.size - v6}, v6=$v6), toplam=${SystemClock.elapsedRealtime() - t0} ms, HTTP $code")
        } catch (e: Exception) {
            Log.i(TAG, "prewarm $host: ${e.javaClass.simpleName} (${SystemClock.elapsedRealtime() - t0} ms)")
        }
    }
}
