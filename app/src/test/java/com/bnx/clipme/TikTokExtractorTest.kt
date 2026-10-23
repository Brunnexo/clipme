package com.bnx.clipme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokExtractorTest {
    @Test
    fun matches() {
        assertTrue(TikTokExtractor.matches("https://www.tiktok.com/@u/video/1"))
        assertTrue(TikTokExtractor.matches("https://vm.tiktok.com/ZMabc/"))
        assertFalse(TikTokExtractor.matches("https://x.com/u/status/1"))
    }

    @Test
    fun parseVideoUrl() {
        val html = """<html><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">
            {"__DEFAULT_SCOPE__":{"webapp.video-detail":{"itemInfo":{"itemStruct":{"video":
            {"playAddr":"https:\u002F\u002Fv16.tiktok.com\u002Fv.mp4?a=1\u0026b=2"}}}}}}</script></html>"""
        assertEquals("https://v16.tiktok.com/v.mp4?a=1&b=2", TikTokExtractor.parseVideoUrl(html))
        assertNull(TikTokExtractor.parseVideoUrl("<html></html>"))
    }

    @Test
    fun parseVideoUrlByQuality() {
        fun info(rate: Int, url: String) = """{"Bitrate":$rate,"PlayAddr":{"UrlList":["$url"]}}"""
        val html = """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">{"__DEFAULT_SCOPE__":{"webapp.video-detail":
            {"itemInfo":{"itemStruct":{"video":{"playAddr":"https://p","bitrateInfo":[${info(100, "https://low")},${info(900, "https://high")},${info(400, "https://mid")}]}}}}}}</script>"""
        assertEquals("https://high", TikTokExtractor.parseVideoUrl(html, Quality.BEST))
        assertEquals("https://mid", TikTokExtractor.parseVideoUrl(html, Quality.MEDIUM))
        assertEquals("https://low", TikTokExtractor.parseVideoUrl(html, Quality.LOW))
    }
}