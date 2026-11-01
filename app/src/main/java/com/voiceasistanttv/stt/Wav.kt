package com.voiceasistanttv.stt

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16-bit mono PCM -> WAV (RIFF) dönüştürücü. */
object Wav {
    fun encode(pcm: ShortArray, from: Int, to: Int, sampleRate: Int): ByteArray {
        val samples = to - from
        val dataLen = samples * 2
        val buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataLen)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(1) // mono
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2)
        buf.putShort(2)
        buf.putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataLen)
        for (i in from until to) buf.putShort(pcm[i])
        return buf.array()
    }

    fun silence(seconds: Int, sampleRate: Int): ByteArray {
        val n = seconds * sampleRate
        return encode(ShortArray(n), 0, n, sampleRate)
    }
}
