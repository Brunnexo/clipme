package com.bnx.clipme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramExtractorTest {
    @Test
    fun shortcode() {
        assertEquals("Abc_1-x", InstagramExtractor.shortcode("https://www.instagram.com/reel/Abc_1-x/?igsh=1"))
        assertEquals("Abc", InstagramExtractor.shortcode("https://instagram.com/user.name/p/Abc/"))
        assertNull(InstagramExtractor.shortcode("https://x.com/a/status/1"))
    }

    @Test
    fun parseVideoUrl() {
        val want = "https://scontent.cdninstagram.com/v/a.mp4?x=1&y=2%3D%3D"
        val html = """{"x":1,"video_versions":[{"type":101,"url":"https:\/\/scontent.cdninstagram.com\/v\/a.mp4?x=1&y=2\u00253D\u00253D","width":720}]}"""
        assertEquals(want, InstagramExtractor.parseVideoUrl(html))
        val two = """"video_versions":[{"url":"https://hi","width":1080},{"url":"https://lo","width":480}]"""
        assertEquals("https://hi", InstagramExtractor.parseVideoUrl(two, Quality.BEST))
        assertEquals("https://lo", InstagramExtractor.parseVideoUrl(two, Quality.LOW))
        assertNull(InstagramExtractor.parseVideoUrl("<html></html>"))
    }
}

