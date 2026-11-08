package com.voiceasistanttv.stt

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * "spotify <song>", "bitchord <song>", "music <song>": plays the best match in a music app (see [MusicApps]).
 *
 * The apps' TV screens do not act on voice/search links, so this talks to their public media service instead: connect to
 * the app's MediaBrowserService, then send the standard "play from search" transport command (what an assistant would
 * send). The app searches songs and starts the first match. Framework classes only.
 *  - BitChord (https://github.com/kushagrasinghx/BitChord, GPL-3.0) accepts it.
 *  - Spotify TV answers "play from search" with its search screen, but plays a `spotify:track:<id>` link started over the
 *    session ("play from URI"). So the song is looked up first ([SpotifyLookup], while the connection is opening) and the
 *    search screen is only the fallback when the lookup finds nothing.
 *
 * Speed: the connection ([connect]) can be opened while the user is still speaking, so starting the app's process and
 * binding its service is already done when the command is ready ([Link.play]).
 */
object MusicPlayer {
    private const val TAG = "VoiceTV"

    /** Gives up (error card) when nothing started within this time. */
    private const val TIMEOUT_MS = 10_000L

    /** Same song already playing: nothing changes, so after this long a playing player counts as success. */
    private const val SETTLE_MS = 3_500L

    /** Debug builds only: extra fields for the play-from-search command (experiments), set by AssistActivity. */
    @Volatile
    var debugExtras: Bundle? = null

    /** Debug builds only: a `spotify:track:<id>` link to play instead of looking the song up (experiments). */
    @Volatile
    var debugUri: String? = null

    /** Apps that show a results screen ([MusicApps.App.listsResults]): how long after the command it is reported as opened. */
    private const val RESULTS_SHOWN_MS = 1_200L

    /** Looking a song up for an app that plays by link: after this long the command goes out without a link (results screen). */
    private const val LOOKUP_MAX_MS = 6_500L

    /** After "buffering" is seen, wait this long for the new title to arrive before reporting it. */
    private const val TITLE_GRACE_MS = 300L

    enum class Err { MISSING, CONNECT, TIMEOUT }

    /** [resultsShown]: the app opened its search results instead of starting a song (the user presses OK there). */
    class Result(val ok: Boolean, val title: String?, val error: Err?, val resultsShown: Boolean = false)

    /** Opens a connection to the app's media service (asynchronous). Null if the app has none (not installed). Main thread. */
    fun connect(ctx: Context, app: MusicApps.App): Link? {
        val appCtx = ctx.applicationContext
        val comp = MusicApps.service(appCtx, app) ?: return null
        return Link(appCtx, app, comp).also { it.open() }
    }

    /** Plays [query] in [app]. Call on the main thread; [done] is called once, on the main thread. */
    fun play(ctx: Context, app: MusicApps.App, query: String, done: (Result) -> Unit) {
        val link = connect(ctx, app)
        if (link == null) done(Result(false, null, Err.MISSING)) else link.play(query, done)
    }

    class Link internal constructor(private val app: Context, val music: MusicApps.App, private val comp: ComponentName) {
        private val handler = Handler(Looper.getMainLooper())
        private val t0 = SystemClock.elapsedRealtime()
        private val debuggable = (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        /** A song was sent to the app as a link ("play from URI"): an app with its own player screen now shows it. */
        @Volatile
        var playedByLink = false
            private set

        private var browser: MediaBrowser? = null
        private var controller: MediaController? = null
        private var ready = false
        private var failed = false
        private var closed = false
        private var waiting: Run? = null
        private var current: Run? = null

        private fun ms() = SystemClock.elapsedRealtime() - t0

        internal fun open() {
            ready = false
            failed = false
            try {
                val b = MediaBrowser(app, comp, object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        if (closed) return
                        val token = try { browser?.sessionToken } catch (e: Exception) { null }
                        if (token == null) {
                            connectionLost()
                            return
                        }
                        controller = MediaController(app, token)
                        ready = true
                        Log.i(TAG, "music(${music.id}): connected after ${ms()} ms")
                        waiting?.also { waiting = null }?.start()
                    }

                    override fun onConnectionFailed() = connectionLost()
                    override fun onConnectionSuspended() = connectionLost()
                }, null)
                browser = b
                b.connect()
            } catch (e: Exception) {
                Log.w(TAG, "music(${music.id}): connect failed: ${e.message}")
                connectionLost()
            }
        }

        private fun connectionLost() {
            if (closed) return
            ready = false
            failed = true
            waiting?.also { waiting = null }?.finish(Result(false, null, Err.CONNECT))
        }

        /** Sends the song request over this connection (reconnecting first if it was lost while waiting). */
        fun play(query: String, done: (Result) -> Unit) {
            if (closed) {
                done(Result(false, null, Err.CONNECT))
                return
            }
            val run = Run(query, done)
            current = run
            run.lookUp()
            handler.postDelayed({
                if (!run.finished) run.finish(Result(false, null, if (run.sent) Err.TIMEOUT else Err.CONNECT))
            }, TIMEOUT_MS)
            if (failed) {
                try { browser?.disconnect() } catch (_: Exception) {}
                waiting = run
                open()
            } else if (ready) {
                run.start()
            } else {
                waiting = run
            }
        }

        /** Drops the connection (also cancels a request that has not finished). */
        fun close() {
            if (closed) return
            closed = true
            Log.i(TAG, "music(${music.id}): link closed at ${ms()} ms")
            handler.removeCallbacksAndMessages(null)
            current?.let { it.finished = true; it.detach() }
            try { browser?.disconnect() } catch (_: Exception) {}
        }

        /** A finished request lets the command settle a moment, then the connection goes away. */
        private fun releaseLater() {
            handler.postDelayed({ close() }, if (debuggable) 8000L else 800L)
        }

        private inner class Run(val query: String, val done: (Result) -> Unit) {
            var finished = false
            var sent = false
            private val askedAt = ms()
            private var startId: String? = null
            private var startTitle: String? = null
            private var sawBuffering = false
            private var graceScheduled = false
            private var callback: MediaController.Callback? = null

            // Apps that play by link: the looked-up link (null = not found), and whether the lookup has finished.
            // Without a way to look songs up (see SpotifyLookup) the app just gets the search command.
            private val fixedUri = if (debuggable) debugUri?.takeIf { it.isNotBlank() } else null
            private val byUri = music.playsByUri && (fixedUri != null || SpotifyLookup.configured())
            private var lookupDone = !byUri
            private var uri: String? = null
            private var started = false

            // The app was told to search and shows a results screen (no song starts by itself).
            private var resultsMode = false

            private fun describe(md: MediaMetadata?): String? {
                val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
                val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                return if (artist != null) "$title - $artist" else title
            }

            private fun active(c: MediaController): Boolean {
                val st = c.playbackState?.state
                return st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING || st == PlaybackState.STATE_CONNECTING
            }

            fun start() {
                val c = controller ?: return
                if (finished) return
                startId = c.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                startTitle = c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        if (debuggable) Log.i(TAG, "music(${music.id}): state=${state?.state} at ${ms()} ms")
                        check()
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) = check()
                }
                callback = cb
                c.registerCallback(cb, handler)
                sent = true
                started = true
                trySend()
            }

            /** Looks the song up on a worker thread (network) while the connection is still opening. */
            fun lookUp() {
                if (!byUri) return
                if (fixedUri != null) {
                    lookupDone = true
                    uri = fixedUri
                    return
                }
                Thread({
                    val found = try { SpotifyLookup.find(query) } catch (e: Exception) { null }
                    handler.post {
                        if (finished || lookupDone) return@post
                        uri = found
                        lookupDone = true
                        trySend()
                    }
                }, "spotify-lookup").start()
                // A slow lookup must not leave the user waiting for the 10 s time-out: fall back to the results screen.
                handler.postDelayed({
                    if (!finished && !lookupDone) {
                        Log.i(TAG, "music(${music.id}): lookup too slow, using the search screen")
                        lookupDone = true
                        trySend()
                    }
                }, LOOKUP_MAX_MS)
            }

            /** Sends the command once the connection is ready and (for apps that play by link) the lookup has finished. */
            private fun trySend() {
                if (finished || commanded || !started || !lookupDone) return
                val link = uri
                if (link != null) sendFromUri(link) else sendFromSearch()
            }

            private fun sendFromUri(link: String) {
                val c = controller ?: return
                commanded = true
                playedByLink = true
                c.transportControls.playFromUri(Uri.parse(link), Bundle())
                Log.i(TAG, "music(${music.id}): play-from-uri sent at ${ms()} ms")
                settleLater()
            }

            private var commanded = false

            private fun settleLater() {
                handler.postDelayed({
                    // The same song was already playing: no state or title change to wait for.
                    val cc = controller
                    if (!finished && cc != null && active(cc)) finish(Result(true, describe(cc.metadata), null))
                }, SETTLE_MS)
            }

            /** The standard "play from search" command: the app searches and decides what to start. */
            private fun sendFromSearch() {
                val c = controller ?: return
                if (finished || commanded) return
                commanded = true
                val extras = (if (debuggable) debugExtras else null) ?: Bundle()
                c.transportControls.playFromSearch(query, extras)
                Log.i(TAG, "music(${music.id}): play-from-search sent (${query.length} chars, ${extras.size()} extras) at ${ms()} ms")
                if (music.listsResults) {
                    resultsMode = true
                    afterResultsCommand()
                } else {
                    settleLater()
                }
            }

            /**
             * The app answers with a results screen (its best match highlighted; the user starts it with OK): report that
             * shortly after the command. Its pause is kept: the old song was only paused by the listening screen and must
             * not resume while the user looks at the results.
             */
            private fun afterResultsCommand() {
                handler.postDelayed({ if (!finished) showResultsOnly() }, RESULTS_SHOWN_MS)
            }

            private fun showResultsOnly() {
                // Explicit pause: otherwise the song paused by the listening screen resumes when the focus is given back.
                try { controller?.transportControls?.pause() } catch (_: Exception) {}
                finish(Result(true, null, null, resultsShown = true))
            }

            private fun check() {
                val c = controller ?: return
                if (!sent || finished) return
                if (c.playbackState?.state == PlaybackState.STATE_BUFFERING) sawBuffering = true
                if (!active(c)) return
                val md = c.metadata
                val id = md?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                val changed = (id != null && id != startId) || (title != null && title != startTitle)
                if (changed) {
                    finish(Result(true, describe(md), null))
                } else if (sawBuffering && !graceScheduled && !resultsMode) {
                    // Buffering after the command, but the title did not change yet: either the same song starts again,
                    // or the new title is about to arrive. A short wait tells them apart.
                    graceScheduled = true
                    handler.postDelayed({
                        val cc = controller
                        if (!finished && cc != null) finish(Result(true, describe(cc.metadata), null))
                    }, TITLE_GRACE_MS)
                }
            }

            fun detach() {
                val cb = callback ?: return
                try { controller?.unregisterCallback(cb) } catch (_: Exception) {}
                callback = null
            }

            fun finish(r: Result) {
                if (finished) return
                finished = true
                Log.i(TAG, "music(${music.id}): ok=${r.ok} err=${r.error} at ${ms()} ms (${ms() - askedAt} ms after the command)")
                if (r.ok) MusicApps.markUsed(music)
                // Debug builds keep watching the player for a while (timing logs); release builds stop at once.
                if (!debuggable) detach()
                releaseLater()
                done(r)
            }
        }
    }
}
