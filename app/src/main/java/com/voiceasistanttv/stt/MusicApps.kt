package com.voiceasistanttv.stt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.media.MediaBrowserService

/**
 * Music apps the voice commands can play songs in ("spotify <song>", "bitchord <song>", "music <song>").
 *
 * Which app a command uses:
 *  - an app named in the command ("spotify duman bal") always uses that app,
 *  - a general music command ("duman bal cal", "music duman bal") uses the app chosen in the settings; "Automatic" means
 *    the app used last, otherwise the first installed one.
 */
object MusicApps {
    /** [id] is what the settings store; [nameHint] finds other builds of the same app by a word in their package name. */
    class App(
        val id: String,
        val label: String,
        val packages: List<String>,
        val nameHint: String? = null,
        /** The app answers "play from search" with a results screen instead of starting a song (Spotify TV). */
        val listsResults: Boolean = false,
        /**
         * The app plays a `spotify:track:<id>` link started over its media session: the song is looked up first
         * ([SpotifyLookup]); the results screen ([listsResults]) is the fallback when that finds nothing.
         */
        val playsByUri: Boolean = false,
        /**
         * Its player is its own screen (Spotify TV plays inside a web view), which it brings to the front when a song
         * starts. The voice command then goes back to the home screen so the music plays "in the background".
         */
        val showsPlayerScreen: Boolean = false
    )

    val SPOTIFY = App(
        "spotify", "Spotify", listOf("com.spotify.tv.android", "com.spotify.music"),
        listsResults = true, playsByUri = true, showsPlayerScreen = true
    )
    val BITCHORD = App(
        "bitchord", "BitChord",
        listOf("com.music.bitchord.tv", "com.dev.bitchord.tv", "com.music.bitchord"), "bitchord"
    )

    /** Ready-made choices in the settings, in the order "Automatic" tries them. */
    val KNOWN = listOf(SPOTIFY, BITCHORD)

    /**
     * The connection is opened ahead of the command (while the user speaks) only if a music command worked within this
     * time, i.e. a music session is going on. The apps are large: they are not started for someone who used them days ago.
     */
    private const val PRECONNECT_WINDOW_MS = 2L * 60 * 60 * 1000

    fun custom(pkg: String) = App(pkg, pkg, listOf(pkg))

    /** A stored setting: one of [KNOWN] by id, or a package name. Null for "Automatic" / unknown text. */
    fun forId(id: String): App? {
        if (id.isBlank()) return null
        KNOWN.firstOrNull { it.id == id }?.let { return it }
        return if (id.contains('.')) custom(id) else null
    }

    /** The media services (public MediaBrowserService) of every app that has one. */
    private fun services(ctx: Context): List<ComponentName> {
        val list = try {
            ctx.packageManager.queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE), 0)
        } catch (e: Exception) {
            emptyList()
        }
        return list.mapNotNull { it.serviceInfo }.map { ComponentName(it.packageName, it.name) }
    }

    /** The media service of an installed build of [app], or null. */
    fun service(ctx: Context, app: App): ComponentName? {
        val found = services(ctx).filter { c ->
            c.packageName in app.packages || (app.nameHint != null && c.packageName.contains(app.nameHint, ignoreCase = true))
        }
        return app.packages.firstNotNullOfOrNull { p -> found.firstOrNull { it.packageName == p } } ?: found.firstOrNull()
    }

    fun isInstalled(ctx: Context, app: App): Boolean =
        app.packages.any { AppLauncher.isInstalled(ctx, it) } || service(ctx, app) != null

    /** The package to open for [app] (its launcher screen), or null. */
    fun packageOf(ctx: Context, app: App): String? =
        service(ctx, app)?.packageName ?: app.packages.firstOrNull { AppLauncher.isInstalled(ctx, it) }

    /** App for a general music command: the setting, or in "Automatic" the app used last, else the first installed one. */
    fun chosen(ctx: Context): App? {
        forId(Config.musicApp)?.let { return it }
        val last = forId(Config.lastMusicApp)
        if (last != null && isInstalled(ctx, last)) return last
        return KNOWN.firstOrNull { isInstalled(ctx, it) }
    }

    fun app(route: VoiceRoute.App, ctx: Context): App? = when (route) {
        VoiceRoute.App.SPOTIFY -> SPOTIFY
        VoiceRoute.App.BITCHORD -> BITCHORD
        VoiceRoute.App.MUSIC -> chosen(ctx)
        else -> null
    }

    /**
     * App worth connecting ahead of the voice command: the one a music command worked in recently and that is still
     * installed (so nothing is started on every mic press for someone who only uses it now and then).
     */
    fun preconnectApp(ctx: Context): App? {
        val last = Config.lastMusicUse
        if (last <= 0L || System.currentTimeMillis() - last > PRECONNECT_WINDOW_MS) return null
        val app = forId(Config.lastMusicApp) ?: return null
        return app.takeIf { isInstalled(ctx, it) }
    }

    /** Remembers that a song started in [app] (pre-connection and "Automatic" use it). */
    fun markUsed(app: App) {
        Config.lastMusicApp = app.id
        Config.lastMusicUse = System.currentTimeMillis()
    }

    /** Opens the app's own home screen (a voice command with only the app name: "spotify ac"). */
    fun launch(ctx: Context, app: App): Boolean {
        val pkg = packageOf(ctx, app) ?: return false
        return AppLauncher.launchPackage(ctx, pkg)
    }
}
