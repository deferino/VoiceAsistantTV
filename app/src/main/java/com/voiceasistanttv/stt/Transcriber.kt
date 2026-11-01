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
 * Tek bir konuşmayı kaydeder, sessizlik tespitiyle bitirir ve bulut STT'ye gönderir.
 * Sayboard'daki MySpeechService/Recognizer soyutlamasının sadeleştirilmiş, Vosk'suz karşılığıdır.
 *
 * Tüm Listener çağrıları ana iş parçacığında yapılır. Model yüklemesi yok: boşta RAM maliyeti ~0.
 */
class Transcriber(
    private val context: Context,
    private val attribution: Context? = null,
    private val languageOverride: String? = null,
    /** Tani satirlarinda hangi yolun kaydettigini gostermek icin ("asistan" | "klavye"). */
    private val tag: String = "asistan",
    /** null = Config.vadThreshold. Dusuk sesli (uzak mikrofon) kaynaklar icin daha dusuk taban verilir. */
    private val vadFloor: Int? = null,
    /**
     * Uzak alan (TV'nin yerlesik) mikrofonu: konusma cok daha sessiz gelir. Gurultu tabani en sessiz
     * ilk cerceveden olculur (konusma hemen baslasa da sismez), esik gurultunun 3 kati, klip basina/
     * sonuna daha fazla pay birakilir. Kumanda mikrofonu yolu (AssistActivity) bunu KULLANMAZ.
     */
    private val farField: Boolean = false,
    /** null = Config.audioSource; deneme icin (Config.kbAudioSource). */
    private val sourceOverride: String? = null
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

    private val main = Handler(Looper.getMainLooper())
    private val call = SttClient.Call()

    @Volatile
    private var stopRequested = false

    @Volatile
    private var cancelled = false

    @Volatile
    private var started = false

    /** Kaydı başlatır (arka plan iş parçacığında). */
    fun start(l: Listener) {
        if (started) return
        started = true
        Thread({ runCapture(l) }, "VoiceTV-capture").start()
    }

    /** Kullanıcı bitirdi: kaydı kapat ve çevir. */
    fun stopNow() {
        stopRequested = true
    }

    /** Her şeyi iptal et, geri çağrı yapılmaz. */
    fun cancel() {
        cancelled = true
        stopRequested = true
        call.cancel()
    }

    private fun post(block: () -> Unit) {
        main.post { if (!cancelled) block() }
    }

    private fun fail(l: Listener, code: Code, msg: String) {
        Log.w(TAG, "hata $code: $msg")
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
        val rec = createRecord()
        if (rec == null) {
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
        // Her ~100 ms'lik cercevenin baslangic ornegi ve seviyesi: konusmanin sessiz baslangicini
        // ("mer-haba" daki m/e/r) esigin altinda kalsa da geriye dogru bulabilmek icin.
        val lvlStart = IntArray(maxSamples / 100 + 64)
        val lvlVal = IntArray(lvlStart.size)
        var firstSpeechFrame = -1
        var routeShort = "?"
        val silenceMs = Config.silenceMs
        val noSpeechMs = Config.noSpeechSeconds * 1000L
        val minThreshold = (vadFloor ?: Config.vadThreshold).toDouble()
        val noiseMul = if (farField) 3.0 else 2.5
        val startMs = SystemClock.elapsedRealtime()

        try {
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                fail(l, Code.MIC_UNAVAILABLE, Lang.s(R.string.err_mic_start))
                return
            }
            post { l.onReady() }
            diag("basla[$tag]: kaynak=${sourceOverride?.takeIf { it.isNotBlank() } ?: Config.audioSource} girisler=[${inputList()}] esik-tabani=${minThreshold.toInt()}")

            while (!cancelled) {
                val r = rec.read(buf, 0, frame)
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
                if (frames == 6) {
                    diag("yonlendirme[$tag]: ${routeInfo(rec)}")
                    routeShort = routeShortName(rec)
                }
                if (frames <= lvlStart.size) {
                    lvlStart[frames - 1] = n - c
                    lvlVal[frames - 1] = level
                }
                val threshold = maxOf(minThreshold, noise * noiseMul)
                val loud = level > threshold
                // İlk 300 ms ortam gürültüsünü ölç, sonra yavaşça güncelle
                if (frames <= 3) {
                    noise = if (farField) (if (frames == 1) level.toDouble() else minOf(noise, level.toDouble()))
                    else maxOf(noise, level.toDouble())
                } else if (!loud) noise = noise * 0.95 + level * 0.05

                if (loud) {
                    consecutiveLoud++
                    silentFrames = 0
                    lastSpeech = n
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

                if (stopRequested) break
                if (speaking && silentFrames * 100 >= silenceMs) break
                if (n >= maxSamples) break
                if (!speaking && SystemClock.elapsedRealtime() - startMs >= noSpeechMs) break
            }
        } finally {
            try {
                rec.stop()
            } catch (_: Exception) {
            }
            rec.release()
        }
        if (cancelled) return

        diag(
            "kayit[$tag]: ${n * 1000L / SAMPLE_RATE} ms, konusma=$speaking, elle-durdu=$stopRequested, " +
                "tepe=$peak, gurultu=${noise.toInt()}, esik=${maxOf(minThreshold, noise * noiseMul).toInt()}"
        )
        if (!speaking && !stopRequested) {
            fail(l, Code.NO_SPEECH, Lang.s(R.string.err_no_speech, peak))
            return
        }
        // Esik ("loud") konusmanin ilk sessiz seslerini kesiyordu ("merhaba" -> "haba"). Konusma
        // basladigi cercevenin onundeki, ortam gurultusunun belirgin ustundeki cerceveleri de kat.
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
        val pre = frame * (if (farField) 5 else 3)
        val post = frame * (if (farField) 6 else 4)
        val from = if (startSample >= 0) maxOf(0, startSample - pre) else 0
        val to = if (lastSpeech >= 0) minOf(n, lastSpeech + post) else n
        if (to - from < SAMPLE_RATE / 3) {
            fail(l, Code.EMPTY, Lang.s(R.string.err_too_short, peak))
            return
        }

        diag("gonderilen klip: ${(to - from) * 1000L / SAMPLE_RATE} ms (geri-adim=$back, aralik=${from * 1000L / SAMPLE_RATE}-${to * 1000L / SAMPLE_RATE} ms)")
        saveDebugClip(pcm, n, routeShort)
        post { l.onProcessing() }
        val wav = Wav.encode(pcm, from, to, SAMPLE_RATE)
        val lang = languageOverride ?: Config.language
        val res = SttClient.transcribe(
            wav, Config.baseUrl, Config.apiKey, Config.model, lang, Config.prompt, call
        )
        if (cancelled) return
        when (res) {
            is SttClient.Result.Ok -> {
                val text = res.text.trim()
                diag("sonuc: ${text.length} karakter", " -> $text")
                if (text.isEmpty()) fail(l, Code.EMPTY, Lang.s(R.string.err_not_understood))
                else post { l.onResult(text) }
            }
            is SttClient.Result.Err ->
                fail(l, if (res.network) Code.NETWORK else Code.API, res.message)
        }
    }

    /**
     * Tani satiri: logcat'e yazar; yalnizca debug surumde ayrica files/diag.log'a ekler (en fazla ~64 KB)
     * ve metni de icerir. Surum (release) derlemesinde metin hicbir yere yazilmaz.
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
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "yerlesik-mik"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt-sco"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "kablolu"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "usb"
        AudioDeviceInfo.TYPE_TV_TUNER -> "tuner"
        AudioDeviceInfo.TYPE_HDMI -> "hdmi"
        26 -> "ble-kulaklik"
        else -> "tip$t"
    }

    private fun describe(d: AudioDeviceInfo?): String =
        if (d == null) "yok" else "${typeName(d.type)}(id=${d.id}, ${d.productName})"

    private fun inputList(): String = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.getDevices(AudioManager.GET_DEVICES_INPUTS).joinToString(", ") { describe(it) }
    } catch (e: Exception) {
        "?"
    }

    /** Hangi mikrofon cihazindan kayit alindigi (yonlendirme asenkron: birkac cerceve sonra okunur). */
    private fun routeInfo(rec: AudioRecord): String {
        val sb = StringBuilder("cihaz=").append(describe(rec.routedDevice))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val c = rec.activeRecordingConfiguration
                if (c != null) sb.append(", sessiz-alindi=").append(c.isClientSilenced)
                    .append(", kaynak=").append(c.clientAudioSource)
            } catch (e: Exception) {
            }
        }
        return sb.toString()
    }

    private fun routeShortName(rec: AudioRecord): String = when (rec.routedDevice?.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "mic"
        25 -> "submix"
        26 -> "ble"
        null -> "yok"
        else -> "tip${rec.routedDevice?.type}"
    }

    /** Yalnizca debug surum: tum ham kaydi files/clip-<yol>-<n>-<kaynak>.wav olarak saklar (son 8). */
    private fun saveDebugClip(pcm: ShortArray, n: Int, route: String) {
        val debuggable =
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
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
        val bytes = maxOf(minBuf, SAMPLE_RATE * 2) // en az 1 sn
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
            Log.w(TAG, "AudioRecord oluşturulamadı (kaynak $source): ${e.message}")
            null
        }
    }

    private fun createRecord(): AudioRecord? {
        val preferred = sourceId(sourceOverride?.takeIf { it.isNotBlank() } ?: Config.audioSource)
        val rec = build(preferred) ?: if (preferred != MediaRecorder.AudioSource.MIC) {
            Log.w(TAG, "Seçili ses kaynağı açılamadı, MIC deneniyor")
            build(MediaRecorder.AudioSource.MIC)
        } else null
        if (rec != null && Config.inputDeviceId != 0) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val dev: AudioDeviceInfo? =
                am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == Config.inputDeviceId }
            if (dev != null) rec.preferredDevice = dev
            else Log.w(TAG, "Seçili mikrofon cihazı bulunamadı, varsayılan kullanılıyor")
        }
        return rec
    }

    companion object {
        private const val TAG = "VoiceTV"
        const val SAMPLE_RATE = 16000
    }
}
