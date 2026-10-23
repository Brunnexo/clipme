package com.bnx.clipme

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity

/** Google sign-in in a WebView; once a YouTube session cookie shows up it is saved for [YouTubeExtractor]. */
class YouTubeLoginActivity : AppCompatActivity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Google refuses sign-in from WebViews that identify as one ("; wv" and "Version/x.y").
            settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex("""Version/\S+\s"""), "")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) = checkLogin(url)
            }
        }
        setContentView(web)
        onBackPressedDispatcher.addCallback(this) {
            if (web.canGoBack()) web.goBack()
            else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
        web.loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F")
    }

    private fun checkLogin(url: String) {
        if (!url.contains("youtube.com")) return
        val cookie = CookieManager.getInstance().getCookie("https://www.youtube.com") ?: return
        if (Regex("""(?:^|;\s*)(?:SAPISID|__Secure-3PAPISID)=""").containsMatchIn(cookie)) {
            YouTubeAuth.save(this, cookie)
            Toast.makeText(this, getString(R.string.toast_yt_connected), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}