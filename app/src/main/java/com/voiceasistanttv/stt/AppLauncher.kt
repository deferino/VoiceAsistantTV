package com.voiceasistanttv.stt

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * Finds SmartTube and Nuvio and opens them with a search / title link.
 *
 *  - SmartTube: the `https://www.youtube.com/results?search_query=…` link opens the search screen directly
 *    (see IntentExtractor.extractSearchText in the SmartTube source).
 *  - Nuvio: there is no search link; only title-page links exist
 *    (`nuvio://meta?type=movie|series&id=tt…`, `nuvio://tmdb/movie/<id>`). That is why we find the title
 *    with MediaSearch first.
 */
object AppLauncher {
    private const val TAG = "VoiceTV"

    /** App shown as a ready-made option in the settings (name + package name). */
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

    // In "Automatic" mode they are tried in order (the first installed one is chosen).
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

    /** If a package name is chosen in the settings only that one is used (null if not installed); if empty it is auto-detected. */
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

    /** If a package name is chosen in the settings only that one is used (null if not installed); if empty it is auto-detected. */
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
        Log.i(TAG, "opened: $uri ($pkg)")
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "could not open: $uri ($pkg): ${e.message}")
        false
    } catch (e: Exception) {
        Log.w(TAG, "could not open: $uri ($pkg): $e")
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

    /** Can this package open the given link (VIEW)? */
    private fun canOpen(ctx: Context, uri: Uri, pkg: String): Boolean = try {
        val i = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
        ctx.packageManager.queryIntentActivities(i, 0).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    /**
     * Opens the title page. Nuvio's own link (`nuvio://meta`) is tried first; if the selected build does not
     * recognize it (e.g. Nuvio Native Legacy only recognizes IMDb / stremio links), it falls back to the IMDb title link.
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

    /** Opens an app from its home screen (its TV launcher entry, else the normal one). */
    fun launchPackage(ctx: Context, pkg: String): Boolean {
        val pm = ctx.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Opens Nuvio from its home screen (for the test in the settings screen). */
    fun launchNuvio(ctx: Context): Boolean {
        val pkg = nuvioPackage(ctx) ?: return false
        return launchPackage(ctx, pkg)
    }

    /** Opens SmartTube from its home screen (voice command "smarttube ac"). */
    fun launchSmartTube(ctx: Context): Boolean {
        val pkg = smartTubePackage(ctx) ?: return false
        return launchPackage(ctx, pkg)
    }
}
