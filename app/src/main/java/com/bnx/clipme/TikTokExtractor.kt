package com.bnx.clipme

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Reads the hydration JSON embedded in the public video page. The CDN rejects requests without the page's cookies. */
object TikTokExtractor : Extractor {
    override val name = "TikTok"
    private val HOST = Regex("""https?://(?:[\w-]+\.)?tiktok\.com/""")
    private val DATA = Regex("""<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    override fun matches(url: String) = HOST.containsMatchIn(url)

    /** Direct mp4 URL from the page HTML (by `bitrateInfo` per [quality], else `playAddr`), or null if unavailable. */
    fun parseVideoUrl(html: String, quality: Quality = Quality.BEST): String? {
        val json = DATA.find(html)?.groupValues?.get(1) ?: return null
        val video = JSONObject(json).optJSONObject("__DEFAULT_SCOPE__")?.optJSONObject("webapp.video-detail")
            ?.optJSONObject("itemInfo")?.optJSONObject("itemStruct")?.optJSONObject("video") ?: return null
        val rates = video.optJSONArray("bitrateInfo")
        val urls = (0 until (rates?.length() ?: 0)).map { rates!!.getJSONObject(it) }
            .sortedByDescending { it.optLong("Bitrate") }
            .mapNotNull { it.optJSONObject("PlayAddr")?.optJSONArray("UrlList")?.optString(0)?.ifEmpty { null } }
        if (urls.isNotEmpty()) return quality.pick(urls)
        return video.optString("playAddr").ifEmpty { video.optString("downloadAddr") }.ifEmpty { null }
    }

    override fun extract(url: String, quality: Quality): Media {
        // HttpURLConnection follows the vm.tiktok.com / tiktok.com/t/ short-link redirects by itself.
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", UA)
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        val html: String
        val cookies: String
        try {
            html = conn.inputStream.bufferedReader().use { it.readText() }
            cookies = conn.headerFields["Set-Cookie"].orEmpty().joinToString("; ") { it.substringBefore(';') }
        } finally {
            conn.disconnect()
        }
        val video = parseVideoUrl(html, quality) ?: error(tr(R.string.err_tiktok_not_found))
        return Media(video, mapOf("Cookie" to cookies, "Referer" to "https://www.tiktok.com/", "User-Agent" to UA))
    }
}