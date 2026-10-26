package com.bnx.clipme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bnx.clipme.databinding.ActivityShareBinding
import kotlin.concurrent.thread

/** Bottom sheet shown when a social app shares a link (or MainActivity forwards one): resolves and downloads. */
class ShareActivity : AppCompatActivity() {
    private lateinit var b: ActivityShareBinding
    private var pending: String? = null
    private var pendingFormat = Format.VIDEO
    private var saved: History.Item? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityShareBinding.inflate(layoutInflater)
        setContentView(b.root)

        val pad = b.content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            b.content.setPadding(
                b.content.paddingLeft, b.content.paddingTop, b.content.paddingRight,
                pad + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            )
            insets
        }
        b.root.setOnClickListener { finish() }
        b.btnClose.setOnClickListener { finish() }
        b.btnShare.setOnClickListener { saved?.let { VideoActions.share(this, it) } }
        b.btnOpen.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        val text = intent.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        val link = text?.let { DownloadRunner.LINK.find(it)?.value }
        if (link == null) show(R.drawable.ic_error, true, getString(R.string.share_no_link_title), getString(R.string.share_no_link_msg))
        else if (YouTubeExtractor.matches(link)) when (Settings.youtubeFormat(this)) {
            YoutubeFormat.ASK -> showChoice(link)
            YoutubeFormat.VIDEO -> start(link, Format.VIDEO)
            YoutubeFormat.AUDIO -> start(link, Format.AUDIO)
        } else start(link, Format.VIDEO)
    }

    /** YouTube link and no default format chosen: ask video or MP3. */
    private fun showChoice(link: String) {
        show(R.drawable.ic_download, true, getString(R.string.choice_title), "YouTube")
        b.actions.visibility = View.GONE
        b.choice.visibility = View.VISIBLE
        b.btnVideo.setOnClickListener { start(link, Format.VIDEO) }
        b.btnAudio.setOnClickListener { start(link, Format.AUDIO) }
    }

    private fun start(link: String, format: Format) {
        b.choice.visibility = View.GONE
        val location = Settings.location(this)
        // DownloadManager into public dirs needs storage permission only before Android 10.
        if (location == Location.GALLERY && format == Format.VIDEO && Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pending = link
            pendingFormat = format
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            return
        }
        val app = applicationContext
        thread {
            val result = runCatching {
                DownloadRunner.run(app, link, format, { isDestroyed }) { stage, network, pct ->
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        when (stage) {
                            DownloadRunner.Stage.SEARCHING -> show(R.drawable.ic_download, false, getString(R.string.status_searching), network)
                            DownloadRunner.Stage.DOWNLOADING -> show(R.drawable.ic_download, false, getString(R.string.status_downloading), network, pct ?: 0)
                            DownloadRunner.Stage.CONVERTING -> show(R.drawable.ic_music, false, getString(R.string.status_converting), network, pct ?: 0)
                        }
                    }
                }
            }
            runOnUiThread {
                result.onSuccess {
                    saved = it
                    val where = when (location) {
                        Location.GALLERY -> getString(if (format == Format.AUDIO) R.string.saved_gallery_audio else R.string.saved_gallery)
                        Location.FOLDER -> getString(R.string.saved_folder)
                        Location.PRIVATE -> getString(R.string.saved_private)
                    }
                    if (isDestroyed) Toast.makeText(app, app.getString(R.string.toast_prefix, where), Toast.LENGTH_SHORT).show()
                    else {
                        show(R.drawable.ic_check_circle, true, getString(R.string.status_done), where)
                        b.root.postDelayed({ if (!isFinishing) finish() }, 8000)
                    }
                }.onFailure {
                    if (isDestroyed) Toast.makeText(app, app.getString(R.string.toast_prefix, it.message), Toast.LENGTH_LONG).show()
                    else if (it is DownloadRunner.Unsupported) {
                        show(R.drawable.ic_error, true, getString(R.string.share_unsupported_title), getString(R.string.share_unsupported_msg, link))
                    } else if (it is YouTubeExtractor.LoginRequired) {
                        show(R.drawable.ic_error, true, getString(R.string.share_yt_login_title), getString(R.string.share_yt_login_msg))
                        b.btnOpen.setText(R.string.sign_in)
                        b.btnOpen.setOnClickListener {
                            startActivity(Intent(this, YouTubeLoginActivity::class.java))
                            finish()
                        }
                    } else show(R.drawable.ic_error, true, getString(R.string.status_failed), it.message ?: getString(R.string.unknown_error))
                }
            }
        }
    }

    /** [progress] null = indeterminate bar, otherwise determinate at that percent. */
    private fun show(icon: Int, done: Boolean, title: String, message: String, progress: Int? = null) {
        b.icon.setImageResource(icon)
        b.icon.setBackgroundResource(if (icon == R.drawable.ic_error) R.drawable.bg_icon_circle_error else R.drawable.bg_icon_circle)
        b.title.text = title
        b.message.text = message
        b.choice.visibility = View.GONE
        b.progress.visibility = if (done) View.GONE else View.VISIBLE
        b.actions.visibility = if (done) View.VISIBLE else View.GONE
        b.btnShare.visibility = if (icon == R.drawable.ic_check_circle) View.VISIBLE else View.GONE
        if (progress == null) {
            if (!b.progress.isIndeterminate) b.progress.isIndeterminate = true
        } else {
            b.progress.isIndeterminate = false
            b.progress.setProgressCompat(progress, false)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        val link = pending ?: return
        pending = null
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) start(link, pendingFormat)
        else show(R.drawable.ic_error, true, getString(R.string.share_perm_denied_title), getString(R.string.share_perm_denied_msg))
    }
}