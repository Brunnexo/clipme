package com.bnx.clipme

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Uses the public syndication endpoint that powers embedded tweets; no login needed. */
object TwitterExtractor : Extractor {
    override val name = "X"
    private val TWEET_ID = Regex("""(?:twitter|x)\.com/(?:\w+|i/web)/status(?:es)?/(\d+)""")

    fun tweetId(url: String): String? = TWEET_ID.find(url)?.groupValues?.get(1)

    /** Same token the embed widget computes: (id / 1e15 * PI) in base 36, without zeros and dot. */
    fun token(id: String): String {
        var x = id.toDouble() / 1e15 * Math.PI
        val sb = StringBuilder(java.lang.Long.toString(x.toLong(), 36))
        x -= x.toLong()
        repeat(12) {
            x *= 36
            sb.append(Character.forDigit(x.toInt(), 36))
            x -= x.toInt()
        }
        return sb.toString().replace("0", "")
    }

    /** mp4 variant chosen by bitrate according to [quality], from the tweet's (or its quoted tweet's) media. */
    fun parseVideoUrl(tweet: JSONObject, quality: Quality = Quality.BEST): String? {
        val media = tweet.optJSONArray("mediaDetails")
        for (i in 0 until (media?.length() ?: 0)) {
            val variants = media!!.getJSONObject(i).optJSONObject("video_info")?.optJSONArray("variants") ?: continue
            val urls = (0 until variants.length()).map { variants.getJSONObject(it) }
                .filter { it.optString("content_type") == "video/mp4" }
                .sortedByDescending { it.optInt("bitrate") }.map { it.optString("url") }
            return if (urls.isEmpty()) null else quality.pick(urls)
        }
        return tweet.optJSONObject("quoted_tweet")?.let { parseVideoUrl(it, quality) }
    }

    override fun matches(url: String) = tweetId(url) != null

    override fun extract(url: String, quality: Quality): Media {
        val id = tweetId(url) ?: error(tr(R.string.err_x_link))
        val conn = URL("https://cdn.syndication.twimg.com/tweet-result?id=$id&token=${token(id)}")
            .openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val tweet = runCatching { JSONObject(body) }.getOrNull() ?: error(tr(R.string.err_tweet_not_found))
        return parseVideoUrl(tweet, quality)?.let(::Media) ?: error(tr(R.string.err_tweet_no_video))
    }
}

