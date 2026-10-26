package com.bnx.clipme

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.concurrent.Executors

/**
 * Auto-capture. Android 10+ only lets the focused app read the clipboard or hear about changes, so this cannot watch
 * other apps silently (tried: change listener, 1px overlay + flash activity, log sniffing; all blocked by the OS).
 * What it does: keeps a notification with a "Baixar link copiado" button (one tap from any app, see
 * [ClipCaptureActivity]), downloads in the background with progress notifications, and [capture] is also called
 * whenever one of our windows gets focus (app opened, in-app copy) so a copied link starts downloading by itself.
 */
class ClipboardService : Service() {
    private val pool = Executors.newSingleThreadExecutor()
    private lateinit var clipboard: ClipboardManager

    /** Fires only while we are focused (Android 10+). */
    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull()
        if (text != null) capture(this, text)
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(MONITOR, getString(R.string.notif_channel_capture), NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(DOWNLOADS, getString(R.string.notif_channel_downloads), NotificationManager.IMPORTANCE_LOW))
        val capture = PendingIntent.getActivity(
            this, 1, Intent(this, ClipCaptureActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val monitor = NotificationCompat.Builder(this, MONITOR)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(getString(R.string.notif_capture_title))
            .setContentText(getString(R.string.notif_capture_text))
            .setOngoing(true)
            .setContentIntent(open())
            .addAction(R.drawable.ic_download, getString(R.string.notif_capture_action), capture)
            .build()
        ServiceCompat.startForeground(
            this, 1, monitor,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.addPrimaryClipChangedListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_LINK)?.let { link ->
            val format = runCatching { Format.valueOf(intent.getStringExtra(EXTRA_FORMAT) ?: "") }.getOrDefault(Format.VIDEO)
            pool.execute { download(link, format) }
        }
        return START_STICKY
    }

    private fun download(link: String, format: Format) {
        val nm = getSystemService(NotificationManager::class.java)
        val id = (System.currentTimeMillis() % 100_000).toInt() + 10
        fun builder() = NotificationCompat.Builder(this, DOWNLOADS).setSmallIcon(R.drawable.ic_download).setContentIntent(open())
        var lastPct = -1
        val result = runCatching {
            DownloadRunner.run(this, link, format) { stage, network, pct ->
                if (stage == DownloadRunner.Stage.CONVERTING) {
                    nm.notify(id, builder().setContentTitle(getString(R.string.status_converting)).setContentText(network).setOngoing(true).setProgress(100, pct ?: 0, pct == null).build())
                } else if (stage == DownloadRunner.Stage.SEARCHING) {
                    nm.notify(id, builder().setContentTitle(getString(R.string.status_searching)).setContentText(network).setOngoing(true).setProgress(0, 0, true).build())
                } else if (pct != lastPct) {
                    lastPct = pct ?: 0
                    nm.notify(id, builder().setContentTitle(getString(R.string.status_downloading)).setContentText(network).setOngoing(true).setProgress(100, lastPct, pct == null).build())
                }
            }
        }
        val done = result.fold(
            { builder().setContentTitle(getString(R.string.status_done)).setContentText(it.network).setAutoCancel(true) },
            { builder().setContentTitle(getString(R.string.status_failed)).setContentText(it.message ?: getString(R.string.unknown_error)).setAutoCancel(true) }
        )
        nm.notify(id, done.build())
    }

    private fun open() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(listener)
        pool.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val MONITOR = "monitor"
        private const val DOWNLOADS = "downloads"
        private const val EXTRA_LINK = "link"
        private const val EXTRA_FORMAT = "format"

        fun start(c: Context) {
            c.startForegroundService(Intent(c, ClipboardService::class.java))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, ClipboardService::class.java))
        }

        /**
         * Starts a background download for the supported link in [text]. Without [force] the same link captured in
         * the last 10 minutes is skipped, so refocusing with the same clipboard does not download twice. Call from a
         * context that may start a foreground service (our own focused windows).
         * @return true if a download was started.
         */
        fun capture(c: Context, text: String, force: Boolean = false): Boolean {
            val link = DownloadRunner.LINK.find(text)?.value?.takeIf(DownloadRunner::supports) ?: return false
            val repeat = Settings.lastCaptured(c) == link && System.currentTimeMillis() - Settings.lastCapturedAt(c) < 10 * 60_000
            if (!force && repeat) return false
            Settings.setLastCaptured(c, link)
            val youtube = YouTubeExtractor.matches(link)
            if (youtube && Settings.youtubeFormat(c) == YoutubeFormat.ASK) {
                // needs an answer: show the video/MP3 chooser (we are in the foreground here)
                c.startActivity(
                    Intent(c, ShareActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            }
            val format = if (youtube && Settings.youtubeFormat(c) == YoutubeFormat.AUDIO) Format.AUDIO else Format.VIDEO
            c.startForegroundService(
                Intent(c, ClipboardService::class.java).putExtra(EXTRA_LINK, link).putExtra(EXTRA_FORMAT, format.name)
            )
            Toast.makeText(c.applicationContext, R.string.toast_starting_download, Toast.LENGTH_SHORT).show()
            return true
        }
    }
}