package com.bnx.clipme

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import kotlin.concurrent.thread

/** Play / share / delete / thumbnail for a finished download, whatever its storage [History.Item.kind]. */
object VideoActions {
    private fun uri(c: Context, item: History.Item): Uri? = when (item.kind) {
        "file" -> FileProvider.getUriForFile(c, "${c.packageName}.files", File(item.uri!!))
        "tree" -> Uri.parse(item.uri)
        else -> c.getSystemService(DownloadManager::class.java).getUriForDownloadedFile(item.id)
    }

    /** Plays in the app itself, so private videos never leave it. */
    fun open(c: Context, item: History.Item) {
        val uri = uri(c, item) ?: return toast(c, c.getString(R.string.toast_video_unavailable))
        c.startActivity(Intent(c, PlayerActivity::class.java).setData(uri))
    }

    fun share(c: Context, item: History.Item) {
        val uri = uri(c, item) ?: return toast(c, c.getString(R.string.toast_video_unavailable))
        val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        c.startActivity(Intent.createChooser(send, null))
    }

    /** Removes the file; the history row stays, shown as "Apagado". */
    fun delete(c: Context, item: History.Item) {
        when (item.kind) {
            "file" -> File(item.uri!!).delete()
            "tree" -> DocumentFile.fromSingleUri(c, Uri.parse(item.uri))?.delete()
            else -> c.getSystemService(DownloadManager::class.java).remove(item.id)
        }
    }

    fun thumbnail(c: Context, item: History.Item, view: ImageView) {
        val uri = uri(c, item) ?: return
        view.tag = item.id
        thread {
            val bmp = runCatching {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(c, uri)
                        getFrameAtTime(0)
                    } finally {
                        release()
                    }
                }
            }.getOrNull() ?: return@thread
            view.post {
                if (view.tag == item.id) {
                    view.setPadding(0, 0, 0, 0)
                    view.imageTintList = null
                    view.scaleType = ImageView.ScaleType.CENTER_CROP
                    view.setImageBitmap(bmp)
                }
            }
        }
    }

    private fun toast(c: Context, msg: String) = Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()
}