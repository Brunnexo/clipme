package com.bnx.clipme

import android.content.Context

enum class Quality {
    BEST, MEDIUM, LOW;

    /** [bestFirst] is ordered from highest to lowest quality; a single option is returned for every level. */
    fun <T> pick(bestFirst: List<T>): T = when (this) {
        BEST -> bestFirst.first()
        MEDIUM -> bestFirst[bestFirst.size / 2]
        LOW -> bestFirst.last()
    }
}

/** What to save: the video, or (YouTube only) just the audio as MP3. */
enum class Format { VIDEO, AUDIO }

/** What to do with YouTube links: ask every time, or always use one format. */
enum class YoutubeFormat { ASK, VIDEO, AUDIO }

/** GALLERY = public Movies/Clipme via DownloadManager; FOLDER = user-picked SAF tree; PRIVATE = app-internal storage. */
enum class Location { GALLERY, FOLDER, PRIVATE }

/** User preferences, stored in SharedPreferences. */
object Settings {
    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun quality(c: Context, network: String): Quality =
        runCatching { Quality.valueOf(prefs(c).getString("quality_$network", null) ?: "") }.getOrDefault(Quality.BEST)

    fun setQuality(c: Context, network: String, q: Quality) =
        prefs(c).edit().putString("quality_$network", q.name).apply()

    fun location(c: Context): Location =
        runCatching { Location.valueOf(prefs(c).getString("location", null) ?: "") }.getOrDefault(Location.GALLERY)

    fun setLocation(c: Context, l: Location) = prefs(c).edit().putString("location", l.name).apply()

    /** Last link started by auto-capture, so the same clipboard is not downloaded twice. */
    fun lastCaptured(c: Context): String? = prefs(c).getString("last_captured", null)

    fun lastCapturedAt(c: Context): Long = prefs(c).getLong("last_captured_at", 0)

    fun setLastCaptured(c: Context, link: String) =
        prefs(c).edit().putString("last_captured", link).putLong("last_captured_at", System.currentTimeMillis()).apply()

    fun youtubeFormat(c: Context): YoutubeFormat =
        runCatching { YoutubeFormat.valueOf(prefs(c).getString("youtube_format", null) ?: "") }.getOrDefault(YoutubeFormat.ASK)

    fun setYoutubeFormat(c: Context, f: YoutubeFormat) = prefs(c).edit().putString("youtube_format", f.name).apply()

    fun autoCapture(c: Context) = prefs(c).getBoolean("auto_capture", false)

    fun setAutoCapture(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto_capture", on).apply()

    /** Persisted tree URI of the chosen folder, or null. */
    fun folder(c: Context): String? = prefs(c).getString("folder", null)

    fun setFolder(c: Context, uri: String) = prefs(c).edit().putString("folder", uri).apply()
}