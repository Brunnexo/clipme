package com.bnx.clipme

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TwitterExtractorTest {
    @Test
    fun tweetId() {
        assertEquals("123", TwitterExtractor.tweetId("https://x.com/user/status/123?s=20"))
        assertEquals("123", TwitterExtractor.tweetId("https://twitter.com/i/web/status/123"))
        assertNull(TwitterExtractor.tweetId("https://www.instagram.com/p/abc/"))
    }

    @Test
    fun parseVideoUrl() {
        val json = """{"mediaDetails":[{"video_info":{"variants":[
            {"content_type":"application/x-mpegURL","url":"https://v/a.m3u8"},
            {"content_type":"video/mp4","bitrate":256000,"url":"https://v/low.mp4"},
            {"content_type":"video/mp4","bitrate":2176000,"url":"https://v/high.mp4"}]}}]}"""
        assertEquals("https://v/high.mp4", TwitterExtractor.parseVideoUrl(JSONObject(json)))
        assertEquals("https://v/low.mp4", TwitterExtractor.parseVideoUrl(JSONObject(json), Quality.LOW))
        val quoted = """{"quoted_tweet":$json}"""
        assertEquals("https://v/high.mp4", TwitterExtractor.parseVideoUrl(JSONObject(quoted)))
        assertNull(TwitterExtractor.parseVideoUrl(JSONObject("""{"mediaDetails":[{}]}""")))
    }
}
