package com.voiceasistanttv.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Records a single utterance, ends it with silence detection and sends it to the cloud STT.
 * A simplified, Vosk-free counterpart of Sayboard's MySpeechService/Recognizer abstraction.
 *
 * All Listener calls are made on the main thread. There is no model loading: idle RAM cost is ~0.
 */
class Transcriber(
    private val context: Context,
    private val attribution: Context? = null,
    private val languageOverride: String? = null,
    /** Shows in the diagnostic lines which path made the recording ("assist" | "keyboard"). */
    private val tag: String = "assist",
    /** null = Config.vadThreshold. Quiet (far-field microphone) sources get a lower floor. */
    private val vadFloor: Int? = null,
    /**
     * Far-field (the TV's built-in) microphone: speech arrives much quieter. The noise floor is measured
     * from the quietest first frame (so it does not inflate even if speech starts right away), the threshold is 3x the noise,
     * and more padding is left at the start/end of the clip. The remote-microphone path (AssistActivity) does NOT use this.
     */
    private val farField: Boolean = false,
    /** null = Config.audioSource; for experiments (Config.kbAudioSource). */
    private val sourceOverride: String? = null,
    /**
     * Speculative transcription (assistant path): a short silence after speech already sends the clip to the STT while
     * recording continues. If the silence lasts, that answer is used right away (the STT time is hidden inside the silence wait);
     * if speech resumes, the answer is thrown away and nothing is lost but one extra request.
     */
    private val speculative: Boolean = false,
    /** Debug builds only: instead of the microphone, play saved clips (see [DebugFeed]) to test without speaking. */
    private val debugFeed: String? = null,
    /** null = Config.prompt. The assistant path adds the app names (see Config.assistPrompt). */
    private val promptOverride: String? = null
) {
    enum class Code { NO_PERMISSION, MIC_UNAVAILABLE, NO_SPEECH, EMPTY, NETWORK, API, CONFIG }

    interface Listener {
        fun onReady()
        fun onLevel(rms: Int, speaking: Boolean)
        fun onSpeechStart()
        fun onProcessing()
        fun onResult(text: String)
        fun onError(code: Code, message: String)
    }

    private val promptText: String get() = promptOverride ?: Config.prompt

    private val main = Handler(Looper.getMainLooper())
    private val call = SttClient.Call()

    /**
     * Debug-only microphone replacement for tests. [spec] is a comma-separated list: file names under files/ (16 kHz mono
     * 16-bit WAV, such as the saved clip-*.wav; "name@fromMs-toMs" takes a slice) and numbers (ms of silence).
     * Plays in real time, then silence forever.
     */
    private class DebugFeed(private val pcm: ShortArray) {
        private var pos = 0

        fun read(buf: ShortArray, len: Int): Int {
            try {
                Thread.sleep(len * 1000L / SAMPLE_RATE)
            } catch (_: InterruptedException) {
            }
            for (i in 0 until len) buf[i] = if (pos < pcm.size) pcm[pos++] else 0
            return len
        }

        companion object {
            fun load(ctx: Context, spec: String): DebugFeed? = try {
                val out = ArrayList<Short>()
                for (part in spec.split(',')) {
                    val t = part.trim()
                    val ms = t.toIntOrNull()
                    if (ms != null) {
                        repeat(ms * SAMPLE_RATE / 1000) { out.add(0) }
                    } else {
                        // name or name@fromMs-toMs (a slice of the clip)
                        val name = t.substringBefore('@')
                        val range = t.substringAfter('@', "").split('-')
                        val b = java.io.File(ctx.filesDir, name).readBytes()
                        var i = 44 + (range.getOrNull(0)?.toIntOrNull() ?: 0) * SAMPLE_RATE / 1000 * 2
                        val end = range.getOrNull(1)?.toIntOrNull()?.let { minOf(b.size, 44 + it * SAMPLE_RATE / 1000 * 2) } ?: b.size
                        while (i + 1 < end) {
                            out.add(((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort())
                            i += 2
                        }
                    }
                }
                DebugFeed(out.toShortArray())
            } catch (e: Exception) {
                Log.w(TAG, "debug feed failed: ${e.message}")
                null
            }
        }
    }

    /** One speculative request: the clip range it covers and its (later) answer. */
    private class Spec(val from: Int, val to: Int, val startedAt: Long, val silenceMs: Long) {
        @Volatile var result: SttClient.Result? = null
        @Volatile var finishedAt = 0L
        @Volatile var stale = false
        var thread: Thread? = null
    }

    @Volatile
    private var specCall: SttClient.Call? = null

    @Volatile
    private var stopRequested = false

    @Volatile
    private var cancelled = false

    @Volatile
    private var started = false

    /** Starts recording (on a background thread). */
    fun start(l: Listener) {
        if (started) return
        started = true
        Thread({ runCapture(l) }, "VoiceTV-capture").start()
    }

    /** The user is done: close the recording and transcribe. */
    fun stopNow() {
        stopRequested = true
    }

    /** Cancels everything; no callback is made. */
    fun cancel() {
        cancelled = true
        stopRequested = true
        call.cancel()
        specCall?.cancel()
    }

    private fun post(block: () -> Unit) {
        main.post { if (!cancelled) block() }
    }

    private fun fail(l: Listener, code: Code, msg: String) {
        Log.w(TAG, "error $code: $msg")
        post { l.onError(code, msg) }
    }

    private fun runCapture(l: Listener) {
        if (Config.baseUrl.isBlank()) {
            fail(l, Code.CONFIG, Lang.s(R.string.err_api_url_empty))
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail(l, Code.NO_PERMISSION, Lang.s(R.string.err_no_mic_permission))
            return
        }
        val feed = if (debugFeed != null && isDebuggable()) DebugFeed.load(context, debugFeed) else null
        val rec = if (feed != null) null else createRecord()
        if (rec == null && feed == null) {
            fail(l, Code.MIC_UNAVAILABLE, Lang.s(R.string.err_mic_open))
            return
        }

        val frame = SAMPLE_RATE / 10 // 100 ms
        val maxSamples = SAMPLE_RATE * Config.maxSeconds
        val pcm = ShortArray(maxSamples + frame)
        val buf = ShortArray(frame)
        var n = 0
        var frames = 0
        var noise = 0.0
        var speaking = false
        var consecutiveLoud = 0
        var silentFrames = 0
        var firstSpeech = -1
        var lastSpeech = -1
        var peak = 0
        // Start sample and level of every ~100 ms frame: so that the quiet start of speech
        // (the m/e/r in "mer-haba") can still be found backwards even if it stays below the threshold.
        val lvlStart = IntArray(maxSamples / 100 + 64)
        val lvlVal = IntArray(lvlStart.size)
        var firstSpeechFrame = -1
        var routeShort = "?"
        val silenceMs = Config.silenceMs
        val noSpeechMs = Config.noSpeechSeconds * 1000L
        val minThreshold = (vadFloor ?: Config.vadThreshold).toDouble()
        val noiseMul = if (farField) 3.0 else 2.5
        val startMs = SystemClock.elapsedRealtime()
        // Speculative transcription: start after specAt ms of silence; accept the answer once silence >= acceptAt ms.
        val specAt = if (speculative) maxOf(400, silenceMs * 2 / 5) else Int.MAX_VALUE
        val acceptAt = maxOf(specAt, silenceMs * 2 / 3)
        var spec: Spec? = null

        // The part of the recording that is sent: speech plus a little padding before/after (returns from, to, back-step).
        fun clipRange(): IntArray {
            // The threshold ("loud") was cutting off the first quiet sounds of speech ("merhaba" -> "haba"). Also include
            // the frames just before the frame where speech started that are clearly above the ambient noise.
            var startSample = firstSpeech
            var back = 0
            if (firstSpeechFrame > 0) {
                val onset = maxOf(minThreshold * 0.25, noise * 2.0)
                var k = minOf(firstSpeechFrame, lvlStart.size)
                while (k > 0 && back < (if (farField) 12 else 8) && lvlVal[k - 1] > onset) {
                    k--
                    back++
                }
                if (back > 0) startSample = lvlStart[k]
            }
            val padBefore = frame * (if (farField) 5 else 3)
            val padAfter = frame * (if (farField) 6 else 4)
            val from = if (startSample >= 0) maxOf(0, startSample - padBefore) else 0
            val to = if (lastSpeech >= 0) minOf(n, lastSpeech + padAfter) else n
            return intArrayOf(from, to, back)
        }

        fun startSpec(r: IntArray): Spec {
            val sp = Spec(r[0], r[1], SystemClock.elapsedRealtime(), silentFrames * 100L)
            val sc = SttClient.Call()
            specCall = sc
            val lang = languageOverride ?: Config.language
            sp.thread = Thread({
                val wav = Wav.encode(pcm, sp.from, sp.to, SAMPLE_RATE)
                sp.result = SttClient.transcribe(wav, Config.baseUrl, Config.apiKey, Config.model, lang, promptText, sc)
                sp.finishedAt = SystemClock.elapsedRealtime()
            }, "VoiceTV-stt-spec").also { it.isDaemon = true; it.start() }
            diag("speculative clip: ${(sp.to - sp.from) * 1000L / SAMPLE_RATE} ms after ${sp.silenceMs} ms of silence")
            return sp
        }

        try {
            rec?.startRecording()
            if (rec != null && rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                fail(l, Code.MIC_UNAVAILABLE, Lang.s(R.string.err_mic_start))
                return
            }
            post { l.onReady() }
            diag("start[$tag]: source=${sourceOverride?.takeIf { it.isNotBlank() } ?: Config.audioSource} inputs=[${inputList()}] threshold-floor=${minThreshold.toInt()}")

            while (!cancelled) {
                val r = feed?.read(buf, frame) ?: rec!!.read(buf, 0, frame)
                if (r < 0) {
                    fail(l, Code.MIC_UNAVAILABLE, Lang.s(R.string.err_mic_read, r))
                    return
                }
                if (r == 0) continue
                val c = minOf(r, pcm.size - n)
                System.arraycopy(buf, 0, pcm, n, c)
                n += c

                val level = rms(buf, r)
                if (level > peak) peak = level
                frames++
                if (frames == 6 && rec != null) {
                    diag("routing[$tag]: ${routeInfo(rec)}")
                    routeShort = routeShortName(rec)
                }
                if (frames <= lvlStart.size) {
                    lvlStart[frames - 1] = n - c
                    lvlVal[frames - 1] = level
                }
                val threshold = maxOf(minThreshold, noise * noiseMul)
                val loud = level > threshold
                // Measure the ambient noise over the first 300 ms, then update slowly
                if (frames <= 3) {
                    noise = if (farField) (if (frames == 1) level.toDouble() else minOf(noise, level.toDouble()))
                    else maxOf(noise, level.toDouble())
                } else if (!loud) noise = noise * 0.95 + level * 0.05

                if (loud) {
                    consecutiveLoud++
                    silentFrames = 0
                    lastSpeech = n
                    // Speech resumed: the speculative answer no longer covers everything that was said.
                    spec?.let { it.stale = true }
                    spec = null
                    if (!speaking && consecutiveLoud >= 2) {
                        speaking = true
                        firstSpeech = maxOf(0, n - consecutiveLoud * frame)
                        firstSpeechFrame = maxOf(0, frames - consecutiveLoud)
                        post { l.onSpeechStart() }
                    }
                } else {
                    consecutiveLoud = 0
                    if (speaking) silentFrames++
                }

                val sp = speaking
                post { l.onLevel(level, sp) }

                if (speaking && spec == null && !stopRequested && silentFrames * 100 >= specAt) {
                    val r = clipRange()
                    if (r[1] - r[0] >= SAMPLE_RATE / 3) spec = startSpec(r)
                }
                if (stopRequested) break
                if (speaking && silentFrames * 100 >= silenceMs) break
                if (speaking && silentFrames * 100 >= acceptAt && spec?.result != null) break
                if (n >= maxSamples) break
                if (!speaking && SystemClock.elapsedRealtime() - startMs >= noSpeechMs) break
            }
        } finally {
            try {
                rec?.stop()
            } catch (_: Exception) {
            }
            rec?.release()
        }
        if (cancelled) return

        diag(
            "record[$tag]: ${n * 1000L / SAMPLE_RATE} ms, speech=$speaking, manual-stop=$stopRequested, " +
                "peak=$peak, noise=${noise.toInt()}, threshold=${maxOf(minThreshold, noise * noiseMul).toInt()}"
        )
        if (!speaking && !stopRequested) {
            fail(l, Code.NO_SPEECH, Lang.s(R.string.err_no_speech, peak))
            return
        }
        // A speculative request that is still valid (no speech after it): its answer is the answer.
        var res: SttClient.Result? = null
        var sttMs = 0L
        var speculativeUsed = false
        val sp = spec
        if (sp != null && !sp.stale) {
            saveDebugClip(pcm, n, routeShort)
            post { l.onProcessing() }
            sp.thread?.join(40_000L)
            if (cancelled) return
            val r = sp.result
            if (r is SttClient.Result.Ok) {
                res = r
                sttMs = sp.finishedAt - sp.startedAt
                speculativeUsed = true
            } else {
                diag("speculative request failed, retrying normally")
            }
        }
        if (res == null) {
            val range = clipRange()
            val from = range[0]
            val to = range[1]
            if (to - from < SAMPLE_RATE / 3) {
                fail(l, Code.EMPTY, Lang.s(R.string.err_too_short, peak))
                return
            }
            diag("sent clip: ${(to - from) * 1000L / SAMPLE_RATE} ms (back-step=${range[2]}, range=${from * 1000L / SAMPLE_RATE}-${to * 1000L / SAMPLE_RATE} ms)")
            saveDebugClip(pcm, n, routeShort)
            post { l.onProcessing() }
            val wav = Wav.encode(pcm, from, to, SAMPLE_RATE)
            val lang = languageOverride ?: Config.language
            val sttStart = SystemClock.elapsedRealtime()
            res = SttClient.transcribe(
                wav, Config.baseUrl, Config.apiKey, Config.model, lang, promptText, call
            )
            sttMs = SystemClock.elapsedRealtime() - sttStart
        }
        if (cancelled) return
        when (res) {
            is SttClient.Result.Ok -> {
                val text = res.text.trim()
                diag(
                    "result: ${text.length} chars, stt=$sttMs ms, speculative=$speculativeUsed, " +
                        "silence-wait=${(n - lastSpeech) * 1000L / SAMPLE_RATE} ms", " -> $text"
                )
                if (text.isEmpty()) fail(l, Code.EMPTY, Lang.s(R.string.err_not_understood))
                else post { l.onResult(text) }
            }
            is SttClient.Result.Err ->
                fail(l, if (res.network) Code.NETWORK else Code.API, res.message)
        }
    }

    /**
     * Diagnostic line: writes to logcat; in debug builds only, it is also appended to files/diag.log (at most ~64 KB)
     * and includes the text. In release builds the text is never written anywhere.
     */
    private fun diag(msg: String, debugExtra: String = "") {
        val debuggable =
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val line = if (debuggable) msg + debugExtra else msg
        Log.i(TAG, line)
        if (!debuggable) return
        try {
            val f = java.io.File(context.filesDir, "diag.log")
            if (f.length() > 64 * 1024) f.writeText("")
            val ts = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
            f.appendText("$ts $line\n")
        } catch (_: Exception) {
        }
    }

    private fun typeName(t: Int): String = when (t) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "builtin-mic"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt-sco"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "usb"
        AudioDeviceInfo.TYPE_TV_TUNER -> "tuner"
        AudioDeviceInfo.TYPE_HDMI -> "hdmi"
        26 -> "ble-headset"
        else -> "type$t"
    }

    private fun describe(d: AudioDeviceInfo?): String =
        if (d == null) "none" else "${typeName(d.type)}(id=${d.id}, ${d.productName})"

    private fun inputList(): String = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.getDevices(AudioManager.GET_DEVICES_INPUTS).joinToString(", ") { describe(it) }
    } catch (e: Exception) {
        "?"
    }

    /** Which microphone device the recording came from (routing is asynchronous: read a few frames later). */
    private fun routeInfo(rec: AudioRecord): String {
        val sb = StringBuilder("device=").append(describe(rec.routedDevice))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val c = rec.activeRecordingConfiguration
                if (c != null) sb.append(", silenced=").append(c.isClientSilenced)
                    .append(", source=").append(c.clientAudioSource)
            } catch (e: Exception) {
            }
        }
        return sb.toString()
    }

    private fun routeShortName(rec: AudioRecord): String = when (rec.routedDevice?.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "mic"
        25 -> "submix"
        26 -> "ble"
        null -> "none"
        else -> "type${rec.routedDevice?.type}"
    }

    /** Debug builds only: keeps the whole raw recording as files/clip-<path>-<n>-<source>.wav (last 8). */
    private fun isDebuggable(): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun saveDebugClip(pcm: ShortArray, n: Int, route: String) {
        if (!isDebuggable() || debugFeed != null) return
        try {
            val dir = context.filesDir
            val seq = System.currentTimeMillis() / 1000
            java.io.File(dir, "clip-$tag-$seq-$route.wav").writeBytes(Wav.encode(pcm, 0, n, SAMPLE_RATE))
            dir.listFiles { f -> f.name.startsWith("clip-") }?.sortedBy { it.name.split("-").getOrNull(2)?.toLongOrNull() ?: 0L }
                ?.dropLast(8)?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    private fun rms(b: ShortArray, len: Int): Int {
        var sum = 0.0
        for (i in 0 until len) {
            val v = b[i].toDouble()
            sum += v * v
        }
        return Math.sqrt(sum / len).toInt()
    }

    private fun sourceId(name: String): Int = when (name) {
        Config.SOURCE_MIC -> MediaRecorder.AudioSource.MIC
        Config.SOURCE_VOICE_COMMUNICATION -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        Config.SOURCE_UNPROCESSED ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) MediaRecorder.AudioSource.UNPROCESSED
            else MediaRecorder.AudioSource.MIC
        else -> MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    private fun build(source: Int): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bytes = maxOf(minBuf, SAMPLE_RATE * 2) // at least 1 s
        return try {
            val b = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bytes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && attribution != null) {
                b.setContext(attribution)
            }
            val rec = b.build()
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                null
            } else rec
        } catch (e: Exception) {
            Log.w(TAG, "Could not create AudioRecord (source $source): ${e.message}")
            null
        }
    }

    private fun createRecord(): AudioRecord? {
        val preferred = sourceId(sourceOverride?.takeIf { it.isNotBlank() } ?: Config.audioSource)
        val rec = build(preferred) ?: if (preferred != MediaRecorder.AudioSource.MIC) {
            Log.w(TAG, "Selected audio source could not be opened, trying MIC")
            build(MediaRecorder.AudioSource.MIC)
        } else null
        if (rec != null && Config.inputDeviceId != 0) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val dev: AudioDeviceInfo? =
                am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == Config.inputDeviceId }
            if (dev != null) rec.preferredDevice = dev
            else Log.w(TAG, "Selected microphone device not found, using the default")
        }
        return rec
    }

    companion object {
        private const val TAG = "VoiceTV"
        const val SAMPLE_RATE = 16000
    }
}
