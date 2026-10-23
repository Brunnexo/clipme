package com.bnx.clipme

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.bnx.clipme.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        if (Settings.autoCapture(this)) ClipboardService.start(this)
        b.btnMenu.setOnClickListener { b.drawer.openDrawer(GravityCompat.START) }
        mapOf(
            b.navVideos to VideosActivity::class.java,
            b.navHistory to HistoryActivity::class.java,
            b.navSettings to SettingsActivity::class.java,
            b.navAbout to AboutActivity::class.java,
        ).forEach { (item, screen) ->
            item.setOnClickListener {
                b.drawer.closeDrawers()
                startActivity(Intent(this, screen))
            }
        }
        onBackPressedDispatcher.addCallback(this) {
            if (b.drawer.isDrawerOpen(GravityCompat.START)) b.drawer.closeDrawers()
            else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        b.btnPaste.setOnClickListener {
            val clip = getSystemService(ClipboardManager::class.java).primaryClip
            b.linkInput.setText(clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty())
        }
        b.linkInput.doAfterTextChanged { b.inputError.visibility = View.GONE }
        b.btnDownload.setOnClickListener { download() }
        b.linkInput.setOnEditorActionListener { _, id, _ ->
            (id == EditorInfo.IME_ACTION_GO).also { if (it) download() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !Settings.autoCapture(this)) return
        // focused now, so the clipboard is readable: a freshly copied link starts downloading by itself
        val text = getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text != null) ClipboardService.capture(this, text)
    }

    private fun download() {
        val text = b.linkInput.text?.toString().orEmpty()
        if (Regex("""https?://\S+""").find(text) == null) {
            b.inputError.text = getString(R.string.input_invalid_link)
            b.inputError.visibility = View.VISIBLE
            return
        }
        b.inputError.visibility = View.GONE
        b.linkInput.text = null
        startActivity(
            Intent(this, ShareActivity::class.java)
                .setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        )
    }
}