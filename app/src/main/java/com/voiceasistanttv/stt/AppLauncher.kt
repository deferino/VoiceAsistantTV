package com.voiceasistanttv.stt

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * SmartTube ve Nuvio'yu bulur ve arama / başlık bağlantısıyla açar.
 *
 *  - SmartTube: `https://www.youtube.com/results?search_query=…` bağlantısı doğrudan arama ekranını açar
 *    (SmartTube kaynak kodunda IntentExtractor.extractSearchText).
 *  - Nuvio: arama bağlantısı yok; yalnızca başlık sayfası bağlantıları var
 *    (`nuvio://meta?type=movie|series&id=tt…`, `nuvio://tmdb/movie/<id>`). Bu yüzden başlığı
 *    önce MediaSearch ile buluyoruz.
 */
object AppLauncher {
    private const val TAG = "VoiceTV"

    /** Ayarlarda hazır seçenek olarak gösterilen uygulama (ad + paket adı). */
    class AppChoice(private val labelOf: () -> String, val pkg: String) {
        val label: String get() = labelOf()
    }

    val SMARTTUBE_CHOICES = listOf(
        AppChoice({ "SmartTube" }, "org.smarttube.stable"),
        AppChoice({ "SmartTube Beta" }, "org.smarttube.beta")
    )
    val NUVIO_CHOICES = listOf(
        AppChoice({ Lang.s(R.string.nuvio_original) }, "com.nuvio.tv"),
        AppChoice({ "Nuvio Arc" }, "com.nuvio.tv.test"),
        AppChoice({ "Nuvio Native Legacy" }, "space.nuvio.nativelegacy"),
        AppChoice({ "Nuvio Reshaped" }, "com.nuvioreshaped.tv")
    )

    // "Otomatik" modda sırayla denenir (ilk kurulu olan seçilir).
    private val SMARTTUBE_PACKAGES = listOf(
        "org.smarttube.beta", "org.smarttube.stable", "app.smarttube", "app.smarttube.fdroid",
        "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"
    )
    private val NUVIO_PACKAGES = listOf(
        "com.nuvio.tv", "com.nuvio.app", "com.nuviodebug.com", "com.nuvio.appdebug",
        "com.nuvio.tv.test", "space.nuvio.nativelegacy", "com.nuvioreshaped.tv"
    )

    fun isInstalled(ctx: Context, pkg: String): Boolean = pkg.isNotBlank() && installed(ctx.packageManager, pkg)

    private fun installed(pm: PackageManager, pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun smartTubeSearchUri(query: String): Uri =
        Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))

    /** Ayarda paket adı seçildiyse yalnız o kullanılır (kurulu değilse null); boşsa otomatik bulunur. */
    fun smartTubePackage(ctx: Context): String? {
        val pm = ctx.packageManager
        val chosen = Config.smartTubePkg
        if (chosen.isNotEmpty()) return if (installed(pm, chosen)) chosen else null
        SMARTTUBE_PACKAGES.firstOrNull { installed(pm, it) }?.let { return it }
        return try {
            val probe = Intent(Intent.ACTION_VIEW, smartTubeSearchUri("test"))
            pm.queryIntentActivities(probe, 0)
                .map { it.activityInfo.packageName }
                .firstOrNull {
                    it.contains("smarttube", true) || it.contains("liskovsoft", true) || it.contains("teamsmart", true)
                }
        } catch (_: Exception) {
            null
        }
    }

    /** Ayarda paket adı seçildiyse yalnız o kullanılır (kurulu değilse null); boşsa otomatik bulunur. */
    fun nuvioPackage(ctx: Context): String? {
        val pm = ctx.packageManager
        val chosen = Config.nuvioPkg
        if (chosen.isNotEmpty()) return if (installed(pm, chosen)) chosen else null
        NUVIO_PACKAGES.firstOrNull { installed(pm, it) }?.let { return it }
        return try {
            val probe = Intent(Intent.ACTION_VIEW, Uri.parse("nuvio://meta?type=movie&id=tt0000000"))
            pm.queryIntentActivities(probe, 0).map { it.activityInfo.packageName }.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun start(ctx: Context, uri: Uri, pkg: String): Boolean = try {
        val i = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        Log.i(TAG, "açıldı: $uri ($pkg)")
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "açılamadı: $uri ($pkg): ${e.message}")
        false
    } catch (e: Exception) {
        Log.w(TAG, "açılamadı: $uri ($pkg): $e")
        false
    }

    fun searchSmartTube(ctx: Context, query: String): Boolean {
        val pkg = smartTubePackage(ctx) ?: return false
        return start(ctx, smartTubeSearchUri(query), pkg)
    }

    fun openSmartTubeVideo(ctx: Context, videoId: String): Boolean {
        val pkg = smartTubePackage(ctx) ?: return false
        return start(ctx, Uri.parse("https://www.youtube.com/watch?v=" + Uri.encode(videoId)), pkg)
    }

    /** Bu paket verilen bağlantıyı (VIEW) açabiliyor mu? */
    private fun canOpen(ctx: Context, uri: Uri, pkg: String): Boolean = try {
        val i = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
        ctx.packageManager.queryIntentActivities(i, 0).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    /**
     * Başlık sayfasını açar. Önce Nuvio'nun kendi bağlantısı (`nuvio://meta`) denenir; seçili sürüm bunu
     * tanımıyorsa (ör. Nuvio Native Legacy yalnız IMDb / stremio bağlantılarını tanır) IMDb başlık bağlantısına düşülür.
     */
    fun openNuvioMeta(ctx: Context, type: String, imdbId: String): Boolean {
        val pkg = nuvioPackage(ctx) ?: return false
        val nuvio = Uri.parse("nuvio://meta?type=$type&id=" + Uri.encode(imdbId))
        if (canOpen(ctx, nuvio, pkg)) return start(ctx, nuvio, pkg)
        val imdb = Uri.parse("https://www.imdb.com/title/" + Uri.encode(imdbId) + "/")
        if (canOpen(ctx, imdb, pkg)) return start(ctx, imdb, pkg)
        return start(ctx, nuvio, pkg)
    }

    fun openNuvioTmdb(ctx: Context, type: String, tmdbId: Long): Boolean {
        val pkg = nuvioPackage(ctx) ?: return false
        return start(ctx, Uri.parse("nuvio://tmdb/$type/$tmdbId"), pkg)
    }

    /** Nuvio'yu ana ekranından açar (ayar ekranındaki deneme için). */
    fun launchNuvio(ctx: Context): Boolean {
        val pkg = nuvioPackage(ctx) ?: return false
        val pm = ctx.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
