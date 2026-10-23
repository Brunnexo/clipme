package com.bnx.clipme

import android.content.Context
import android.webkit.CookieManager

/**
 * Cookies of the user's YouTube/Google session, captured by [YouTubeLoginActivity].
 * ponytail: stored as plain text in private SharedPreferences; encrypt (Keystore) if that matters.
 */
object YouTubeAuth {
    @Volatile
    var cookie: String? = null
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("youtube_auth", Context.MODE_PRIVATE)

    lateinit var app: Context
        private set

    fun init(c: Context) {
        app = c.applicationContext
        cookie = prefs(c).getString("cookie", null)
    }

    fun save(c: Context, value: String) {
        cookie = value
        prefs(c).edit().putString("cookie", value).apply()
    }

    fun clear(c: Context) {
        cookie = null
        prefs(c).edit().remove("cookie").apply()
        CookieManager.getInstance().removeAllCookies(null)
    }

    fun isLoggedIn() = cookie?.let { sapisid(it) } != null
    private fun sapisid(cookie: String): String? =
        Regex("""(?:^|;\s*)(?:SAPISID|__Secure-3PAPISID)=([^;]+)""").find(cookie)?.groupValues?.get(1)
}
