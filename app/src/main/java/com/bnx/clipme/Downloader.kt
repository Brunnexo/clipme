package com.bnx.clipme

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-process downloads: videos for the PRIVATE and FOLDER locations (DownloadManager cannot write there) and the
 * MP3 conversion of YouTube audio for every location. Blocking.
 */
object Downloader {
    /** Where a finished file goes. [finish] runs after [out] is closed, [undo] removes a partial file. */
    private class Target(
        val out: OutputStream, val kind: String, val uri: String,
        val undo: () -> Unit, val finish: () -> Unit = {},
    )

    fun fetch(c: Context, media: Media, location: Location, onProgress: (Int) -> Unit): Pair<String, String> {
        val target = open(c, location, "clipme_${System.currentTimeMillis()}.mp4", "video/mp4", audio = false)
        try {
            target.out.use { download(media, it, onProgress) }
            target.finish()
        } catch (e: Exception) {
            target.undo()
            throw e
        }
        return target.kind to target.uri
    }

    /** Downloads [media] (an audio-only stream, or a video whose audio track is used) and saves it as MP3. */
    fun fetchAudio(
        c: Context, media: Media, location: Location, onDownload: (Int) -> Unit, onConvert: (Int) -> Unit,
    ): Pair<String, String> {
        val tmp = File.createTempFile("clipme-audio", ".bin", c.cacheDir)
        try {
            tmp.outputStream().use { download(media, it, onDownload) }
            val target = open(c, location, "clipme_${System.currentTimeMillis()}.mp3", "audio/mpeg", audio = true)
            try {
                target.out.use { AudioConverter.toMp3(tmp, it, onConvert) }
                target.finish()
            } catch (e: Exception) {
                target.undo()
                throw e
            }
            return target.kind to target.uri
        } finally {
            tmp.delete()
        }
    }

    private fun open(c: Context, location: Location, name: String, mime: String, audio: Boolean): Target = when {
        location == Location.PRIVATE -> {
            val file = File(File(c.filesDir, if (audio) "audio" else "videos").apply { mkdirs() }, name)
            Target(file.outputStream(), "file", file.absolutePath, { file.delete() })
        }
        location == Location.FOLDER -> {
            val dir = Settings.folder(c)?.let { DocumentFile.fromTreeUri(c, Uri.parse(it)) }
                ?.takeIf { it.canWrite() } ?: error(tr(R.string.err_folder_unavailable))
            val doc = dir.createFile(mime, name) ?: error(tr(R.string.err_cannot_create_file))
            Target(c.contentResolver.openOutputStream(doc.uri)!!, "tree", doc.uri.toString(), { doc.delete() })
        }
        // GALLERY, audio only (gallery videos go through DownloadManager): public Music/Clipme
        Build.VERSION.SDK_INT >= 29 -> {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Clipme")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = c.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error(tr(R.string.err_cannot_create_file))
            Target(
                c.contentResolver.openOutputStream(uri)!!, "ms", uri.toString(),
                { c.contentResolver.delete(uri, null, null) },
                { c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null) },
            )
        }
        else -> {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Clipme").apply { mkdirs() }
            val file = File(dir, name)
            Target(
                file.outputStream(), "file", file.absolutePath, { file.delete() },
                { MediaScannerConnection.scanFile(c, arrayOf(file.absolutePath), arrayOf(mime), null) },
            )
        }
    }

    private fun download(media: Media, out: OutputStream, onProgress: (Int) -> Unit) {
        val conn = URL(media.url).openConnection() as HttpURLConnection
        try {
            media.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (media.ranged) conn.setRequestProperty("Range", "bytes=0-")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            copy(conn.inputStream, out, conn.contentLengthLong, onProgress)
        } finally {
            conn.disconnect()
        }
    }

    private fun copy(input: InputStream, out: OutputStream, total: Long, onProgress: (Int) -> Unit) {
        input.use {
            val buf = ByteArray(64 * 1024)
            var done = 0L
            var last = -1
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                done += n
                if (total > 0) (done * 100 / total).toInt().let { pct -> if (pct != last) { last = pct; onProgress(pct) } }
            }
        }
    }
}