package com.bnx.clipme

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks the InnerTube player endpoint as a mobile app client, which returns direct (unciphered) URLs.
 * ponytail: only progressive mp4 (audio+video muxed, usually 360p) since merging adaptive streams needs ffmpeg;
 * YouTube bot-checks some IPs/videos (LOGIN_REQUIRED) and client versions age out, so keep the client list current.
 */
object YouTubeExtractor : Extractor {
    override val name = "YouTube"
    private val VIDEO_ID = Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?(?:\S*?&)?v=|shorts/|embed/|live/))([\w-]{11})""")

    /** YouTube asked for a signed-in session (bot check, age gate or expired login). */
    class LoginRequired(message: String) : IllegalStateException(message)

    private class Client(val ua: String, val json: String)

    private val CLIENTS = listOf(
        Client(
            "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            """{"clientName":"ANDROID_VR","clientVersion":"1.60.19","deviceMake":"Oculus","deviceModel":"Quest 3","osName":"Android","osVersion":"12L","androidSdkVersion":32,"hl":"en"}"""
        ),
        Client(
            "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
            """{"clientName":"IOS","clientVersion":"19.45.4","deviceMake":"Apple","deviceModel":"iPhone16,2","osName":"iPhone","osVersion":"18.1.0.22B83","hl":"en"}"""
        ),
    )

    fun videoId(url: String): String? = VIDEO_ID.find(url)?.groupValues?.get(1)

    override fun matches(url: String) = videoId(url) != null

    /** Direct mp4 URL (audio+video) by height according to [quality]; throws with YouTube's reason when not playable. */
    fun parseVideoUrl(response: JSONObject, quality: Quality): String? {
        val status = response.optJSONObject("playabilityStatus")
        if (status != null && status.optString("status") != "OK") {
            val reason = status.optString("reason").ifEmpty { tr(R.string.err_youtube_unavailable) }
            if (status.optString("status") == "LOGIN_REQUIRED") throw LoginRequired(reason)
            error(reason)
        }
        val formats = response.optJSONObject("streamingData")?.optJSONArray("formats") ?: return null
        val urls = (0 until formats.length()).map { formats.getJSONObject(it) }
            .filter { it.optString("mimeType").startsWith("video/mp4") && it.optString("url").isNotEmpty() }
            .sortedByDescending { it.optInt("height") }.map { it.getString("url") }
        return if (urls.isEmpty()) null else quality.pick(urls)
    }

    override fun extract(url: String, quality: Quality): Media {
        val id = videoId(url) ?: error(tr(R.string.err_youtube_link))
        var failure: Exception? = null
        // Fast path: anonymous mobile clients (direct URLs, no cipher) while YouTube does not bot-check this IP/video.
        for (client in CLIENTS) {
            try {
                val response = player(id, client)
                parseVideoUrl(response, quality)?.let { return Media(it, mapOf("User-Agent" to client.ua)) }
                Log.d("ClipmeYT", "${client.json.take(40)} -> sem formato direto")
            } catch (e: Exception) {
                Log.d("ClipmeYT", "${client.json.take(40)} -> ${e.message}")
                // prefer the first real reason, but a login request beats everything else
                if (failure == null || (e is LoginRequired && failure !is LoginRequired)) failure = e
            }
        }
        // Signed in: let the real player resolve the URL (web clients return ciphered formats).
        if (YouTubeAuth.isLoggedIn()) return YouTubeWebResolver.resolve(YouTubeAuth.app, id)
        throw failure ?: IllegalStateException(tr(R.string.err_youtube_no_format))
    }

    private fun player(id: String, client: Client): JSONObject {
        val body = JSONObject().put("context", JSONObject().put("client", JSONObject(client.json)))
            .put("videoId", id).put("contentCheckOk", true).put("racyCheckOk", true).toString()
        val conn = URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", client.ua)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val stream = if (conn.responseCode < 400) conn.inputStream else conn.errorStream
            return JSONObject(stream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}