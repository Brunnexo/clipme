package com.bnx.clipme

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object InstagramExtractor : Extractor {
    override val name = "Instagram"
    private val SHORTCODE = Regex("""instagram\.com/(?:[\w.]+/)?(?:p|reels?|tv)/([\w-]+)""")
    private val VERSIONS = Regex("""\"video_versions\"\s*:\s*(\[.*?\])""", RegexOption.DOT_MATCHES_ALL)

    // Logged-out browsers get a login wall and no media; the Googlebot UA gets server-rendered HTML with the
    // JSON (`video_versions`, progressive mp4). ponytail: relies on Instagram serving crawlers; fallback = login via WebView.
    private const val UA = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"

    fun shortcode(url: String): String? = SHORTCODE.find(url)?.groupValues?.get(1)

    /** Progressive mp4 from the page JSON (`video_versions`), picked by resolution according to [quality]. */
    fun parseVideoUrl(html: String, quality: Quality = Quality.BEST): String? {
        val raw = VERSIONS.find(html)?.groupValues?.get(1) ?: return null
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        val urls = (0 until arr.length()).map { arr.getJSONObject(it) }
            .sortedByDescending { it.optInt("width") }.map { it.optString("url") }.filter { it.isNotEmpty() }
        return if (urls.isEmpty()) null else quality.pick(urls)
    }

    override fun matches(url: String) = shortcode(url) != null

    override fun extract(url: String, quality: Quality): Media {
        val code = shortcode(url) ?: error(tr(R.string.err_instagram_link))
        val conn = URL("https://www.instagram.com/p/$code/").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", UA)
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        val html = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        return parseVideoUrl(html, quality)?.let(::Media) ?: error(tr(R.string.err_instagram_not_found))
    }
}


