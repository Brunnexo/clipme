package com.bnx.clipme

/** Direct video URL plus any headers the CDN requires (cookies, referer, ...). */
data class Media(val url: String, val headers: Map<String, String> = emptyMap())

/** One extractor per social network; scraping formats change often, so keep each isolated. */
interface Extractor {
    val name: String

    fun matches(url: String): Boolean

    /** Blocking network call: returns the media to download or throws. */
    fun extract(url: String, quality: Quality): Media
}