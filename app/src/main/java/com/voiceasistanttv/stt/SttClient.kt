package com.voiceasistanttv.stt

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI uyumlu `POST {baseUrl}/audio/transcriptions` istemcisi.
 * Groq, OpenAI ve OpenAI uyumlu kendi sunucuların (faster-whisper-server, whisper.cpp server vb.) ile çalışır.
 * Sadece Android framework sınıflarını kullanır (OkHttp vb. yok).
 */
object SttClient {
    sealed class Result {
        class Ok(val text: String) : Result()
        class Err(val message: String, val network: Boolean) : Result()
    }

    /** İptal edilebilir çağrı tutacağı. */
    class Call {
        @Volatile
        var conn: HttpURLConnection? = null

        fun cancel() {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    fun transcribe(
        wav: ByteArray,
        baseUrl: String,
        apiKey: String,
        model: String,
        language: String,
        prompt: String,
        call: Call = Call()
    ): Result = NetLog.timed(baseUrl) { transcribeImpl(wav, baseUrl, apiKey, model, language, prompt, call) }

    private fun transcribeImpl(
        wav: ByteArray,
        baseUrl: String,
        apiKey: String,
        model: String,
        language: String,
        prompt: String,
        call: Call
    ): Result {
        var conn: HttpURLConnection? = null
        try {
            val boundary = "----VoiceTV" + System.currentTimeMillis()
            val body = ByteArrayOutputStream(wav.size + 1024)
            fun field(name: String, value: String) {
                body.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                        .toByteArray(Charsets.UTF_8)
                )
            }
            field("model", model)
            field("response_format", "json")
            field("temperature", "0")
            if (language.isNotBlank()) field("language", language)
            if (prompt.isNotBlank()) field("prompt", prompt)
            body.write(
                ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n" +
                        "Content-Type: audio/wav\r\n\r\n").toByteArray(Charsets.UTF_8)
            )
            body.write(wav)
            body.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            val payload = body.toByteArray()

            val url = URL(baseUrl.trim().trimEnd('/') + "/audio/transcriptions")
            conn = url.openConnection() as HttpURLConnection
            call.conn = conn
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            conn.setFixedLengthStreamingMode(payload.size)
            conn.outputStream.use { it.write(payload) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return if (code in 200..299) parseOk(text) else Result.Err(describe(code, text), false)
        } catch (e: IOException) {
            return Result.Err(Lang.s(R.string.err_network, e.message ?: e.javaClass.simpleName), true)
        } catch (e: Exception) {
            return Result.Err(Lang.s(R.string.err_generic, e.message ?: e.javaClass.simpleName), false)
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /** Ayarları denemek için 1 sn sessizlik gönderir; 200 dönerse anahtar/adres/model doğrudur. */
    fun ping(baseUrl: String, apiKey: String, model: String, language: String): Result =
        transcribe(Wav.silence(1, 16000), baseUrl, apiKey, model, language, "")

    private fun parseOk(text: String): Result = try {
        Result.Ok(JSONObject(text).optString("text", ""))
    } catch (e: Exception) {
        Result.Err(Lang.s(R.string.err_unexpected_response, text.take(120)), false)
    }

    private fun describe(code: Int, body: String): String {
        val api = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }
        val head = when (code) {
            401, 403 -> Lang.s(R.string.http_401, code)
            404 -> Lang.s(R.string.http_404)
            413 -> Lang.s(R.string.http_413)
            429 -> Lang.s(R.string.http_429)
            else -> Lang.s(R.string.http_other, code)
        }
        val tail = api.ifBlank { body.take(120) }
        return if (tail.isBlank()) head else "$head: ${tail.take(160)}"
    }
}
