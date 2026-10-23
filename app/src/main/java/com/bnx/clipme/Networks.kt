package com.bnx.clipme

/** Single registry of supported networks; order here is the order shown in the UI. */
object Networks {
    val extractors = listOf<Extractor>(InstagramExtractor, TwitterExtractor, TikTokExtractor, YouTubeExtractor)

    fun icon(name: String) = when (name) {
        "Instagram" -> R.drawable.ic_instagram
        "TikTok" -> R.drawable.ic_tiktok
        "YouTube" -> R.drawable.ic_youtube
        else -> R.drawable.ic_x
    }
}