package com.bnx.clipme

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/** Whole download pipeline (extract, save by location, record history), shared by the share sheet and the clipboard service. */
object DownloadRunner {
    class Unsupported : Exception()
    enum class Stage { SEARCHING, DOWNLOADING, CONVERTING }

    val LINK = Regex("""https?://\S+""")

    fun supports(link: String) = Networks.extractors.any { it.matches(link) }

    /** Blocking. [onStage] gets the network name and, while downloading, the percent (null = unknown). */
    fun run(
        c: Context, link: String, format: Format = Format.VIDEO, cancelled: () -> Boolean = { false },
        onStage: (Stage, String, Int?) -> Unit,
    ): History.Item {
        val ex = Networks.extractors.firstOrNull { it.matches(link) } ?: throw Unsupported()
        val location = Settings.location(c)
        onStage(Stage.SEARCHING, ex.name, null)
        if (format == Format.AUDIO) {
            if (ex !== YouTubeExtractor) throw Unsupported()
            val media = YouTubeExtractor.extractAudio(link)
            val now = System.currentTimeMillis()
            onStage(Stage.DOWNLOADING, ex.name, 0)
            val (kind, uri) = Downloader.fetchAudio(
                c, media, location,
                { onStage(Stage.DOWNLOADING, ex.name, it) },
                { onStage(Stage.CONVERTING, ex.name, it) },
            )
            return History.Item(now, ex.name, link, now, kind, uri, audio = true).also { History.add(c, it) }
        }
        val media = ex.extract(link, Settings.quality(c, ex.name))
        val now = System.currentTimeMillis()
        onStage(Stage.DOWNLOADING, ex.name, 0)
        val item = if (location == Location.GALLERY) {
            val id = enqueue(c, media)
            awaitDownload(c, id, cancelled) { onStage(Stage.DOWNLOADING, ex.name, it) }
            History.Item(id, ex.name, link, now)
        } else {
            val (kind, uri) = Downloader.fetch(c, media, location) { onStage(Stage.DOWNLOADING, ex.name, it) }
            History.Item(now, ex.name, link, now, kind, uri)
        }
        History.add(c, item)
        return item
    }

    private fun enqueue(c: Context, media: Media): Long {
        val req = DownloadManager.Request(Uri.parse(media.url))
            .setTitle("Clipme")
            .apply { media.headers.forEach { (k, v) -> addRequestHeader(k, v) } }
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_MOVIES, "Clipme/clipme_${System.currentTimeMillis()}.mp4"
            )
        return c.getSystemService(DownloadManager::class.java).enqueue(req)
    }

    /** Polls DownloadManager and reports percent; returns early if [cancelled] (the download goes on by itself). */
    private fun awaitDownload(c: Context, id: Long, cancelled: () -> Boolean, onProgress: (Int) -> Unit) {
        val dm = c.getSystemService(DownloadManager::class.java)
        while (!cancelled()) {
            dm.query(DownloadManager.Query().setFilterById(id)).use {
                if (!it.moveToFirst()) error(tr(R.string.err_download_cancelled))
                when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> return
                    DownloadManager.STATUS_FAILED -> error(tr(R.string.err_download_failed))
                }
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                if (total > 0) onProgress((done * 100 / total).toInt())
            }
            Thread.sleep(300)
        }
    }
}