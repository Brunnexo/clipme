package com.bnx.clipme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Lets YouTube's own player do the hard part (signature, `n` throttling, PO token): a hidden WebView with the
 * user's session opens the watch page with MediaSource removed, which makes the player fall back to a progressive
 * mp4 and put the already-deciphered googlevideo URL in `<video>.src`.
 * ponytail: relies on that fallback and on the page structure; ~10 s per resolve.
 */
object YouTubeWebResolver {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val NO_MSE =
        "window.MediaSource=undefined;window.ManagedMediaSource=undefined;window.WebKitMediaSource=undefined;"
    private const val PROBE = """(function(){var v=document.querySelector('video');if(!v)return '';
        try{v.muted=true;v.play();}catch(e){}return v.currentSrc||v.src||'';})()"""

    /** Blocking; returns the direct video URL or throws. Must not be called on the main thread. */
    @SuppressLint("SetJavaScriptEnabled")
    fun resolve(ctx: Context, videoId: String, timeoutSec: Long = 40): Media {
        val main = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        var url: String? = null
        var web: WebView? = null
        main.post {
            val wv = WebView(ctx).also { web = it }
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.mediaPlaybackRequiresUserGesture = false
            wv.settings.userAgentString = UA
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(wv, NO_MSE, setOf("https://www.youtube.com"))
            } else {
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, u: String, f: android.graphics.Bitmap?) = view.evaluateJavascript(NO_MSE, null)
                }
            }
            val poll = object : Runnable {
                override fun run() {
                    wv.evaluateJavascript(PROBE) { raw ->
                        val src = raw?.trim('"').orEmpty()
                        if (src.startsWith("https://") && src.contains("googlevideo.com")) {
                            url = src
                            latch.countDown()
                        } else main.postDelayed(this, 700)
                    }
                }
            }
            wv.loadUrl("https://www.youtube.com/watch?v=$videoId")
            main.postDelayed(poll, 1500)
        }
        val ok = latch.await(timeoutSec, TimeUnit.SECONDS)
        main.post { web?.destroy() }
        if (!ok || url == null) error(tr(R.string.err_youtube_web))
        return Media(url!!, mapOf("User-Agent" to UA))
    }
}