package com.bnx.clipme

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** In-process download for the PRIVATE and FOLDER locations (DownloadManager cannot write there). Blocking. */
object Downloader {
    fun fetch(c: Context, media: Media, location: Location, onProgress: (Int) -> Unit): Pair<String, String> {
        val name = "clipme_${System.currentTimeMillis()}.mp4"
        val (out, kind, uri, undo) = when (location) {
            Location.PRIVATE -> {
                val file = File(File(c.filesDir, "videos").apply { mkdirs() }, name)
                Target(file.outputStream(), "file", file.absolutePath) { file.delete() }
            }
            else -> {
                val dir = Settings.folder(c)?.let { DocumentFile.fromTreeUri(c, Uri.parse(it)) }
                    ?.takeIf { it.canWrite() } ?: error(tr(R.string.err_folder_unavailable))
                val doc = dir.createFile("video/mp4", name) ?: error(tr(R.string.err_cannot_create_file))
                Target(c.contentResolver.openOutputStream(doc.uri)!!, "tree", doc.uri.toString()) { doc.delete() }
            }
        }
        try {
            val conn = URL(media.url).openConnection() as HttpURLConnection
            media.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            val total = conn.contentLengthLong
            conn.inputStream.use { input -> out.use { copy(input, it, total, onProgress) } }
            conn.disconnect()
        } catch (e: Exception) {
            undo()
            throw e
        }
        return kind to uri
    }

    private data class Target(val out: OutputStream, val kind: String, val uri: String, val undo: () -> Unit)

    private fun copy(input: java.io.InputStream, out: OutputStream, total: Long, onProgress: (Int) -> Unit) {
        val buf = ByteArray(64 * 1024)
        var done = 0L
        var last = -1
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            done += n
            if (total > 0) (done * 100 / total).toInt().let { if (it != last) { last = it; onProgress(it) } }
        }
    }
}