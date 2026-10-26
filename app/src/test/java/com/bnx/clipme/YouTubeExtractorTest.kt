package com.bnx.clipme

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class YouTubeExtractorTest {
    @Test
    fun videoId() {
        assertEquals("dQw4w9WgXcQ", YouTubeExtractor.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=5"))
        assertEquals("dQw4w9WgXcQ", YouTubeExtractor.videoId("https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeExtractor.videoId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("abcdefghijk", YouTubeExtractor.videoId("https://youtube.com/shorts/abcdefghijk"))
        assertNull(YouTubeExtractor.videoId("https://x.com/u/status/1"))
    }

    @Test
    fun parseVideoUrl() {
        val ok = JSONObject(
            """{"playabilityStatus":{"status":"OK"},"streamingData":{"formats":[
            {"mimeType":"video/mp4; codecs=\"avc1\"","height":360,"url":"https://lo"},
            {"mimeType":"video/mp4; codecs=\"avc1\"","height":720,"url":"https://hi"},
            {"mimeType":"video/mp4","height":1080,"signatureCipher":"s=x"}]}}"""
        )
        assertEquals("https://hi", YouTubeExtractor.parseVideoUrl(ok, Quality.BEST))
        assertEquals("https://lo", YouTubeExtractor.parseVideoUrl(ok, Quality.LOW))
        val blocked = JSONObject("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in"}}""")
        assertEquals("Sign in", assertThrows(IllegalStateException::class.java) { YouTubeExtractor.parseVideoUrl(blocked, Quality.BEST) }.message)
    }

    @Test
    fun parseAudioUrl() {
        val ok = JSONObject(
            """{"playabilityStatus":{"status":"OK"},"streamingData":{"adaptiveFormats":[
            {"mimeType":"video/mp4; codecs=\"avc1\"","bitrate":9000000,"url":"https://video"},
            {"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":130677,"url":"https://aac"},
            {"mimeType":"audio/webm; codecs=\"opus\"","bitrate":136544,"url":"https://opus"},
            {"mimeType":"audio/webm; codecs=\"opus\"","bitrate":990000,"signatureCipher":"s=x"}]}}"""
        )
        assertEquals("https://opus", YouTubeExtractor.parseAudioUrl(ok))
        assertNull(YouTubeExtractor.parseAudioUrl(JSONObject("""{"playabilityStatus":{"status":"OK"},"streamingData":{}}""")))
    }
}