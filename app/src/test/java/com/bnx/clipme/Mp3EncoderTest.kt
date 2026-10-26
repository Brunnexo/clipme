package com.bnx.clipme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.sin

class Mp3EncoderTest {
    private fun encode(sampleRate: Int, channels: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val enc = Mp3Encoder(out, sampleRate, channels)
        val frames = sampleRate // 1 second
        val pcm = ShortArray(frames * channels) { i -> (sin(2 * PI * 440 * (i / channels) / sampleRate) * 12000).toInt().toShort() }
        enc.write(pcm, frames)
        enc.finish()
        return out.toByteArray()
    }

    private fun assertMp3(bytes: ByteArray) {
        assertTrue("no output", bytes.size > 1000)
        // MPEG audio frame sync: 11 set bits
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xE0, bytes[1].toInt() and 0xE0)
    }

    @Test
    fun stereo44100() {
        val mp3 = encode(44100, 2)
        assertMp3(mp3)
        // 320 kbps CBR: one second is about 40 KB
        assertTrue("size ${mp3.size}", mp3.size in 35_000..45_000)
    }

    @Test
    fun mono48000() = assertMp3(encode(48000, 1))
}