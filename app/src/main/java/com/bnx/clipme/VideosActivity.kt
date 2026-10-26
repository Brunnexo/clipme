package com.bnx.clipme

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.bnx.clipme.databinding.ActivityVideosBinding
import com.bnx.clipme.databinding.ItemSectionBinding
import com.bnx.clipme.databinding.ItemVideoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Finished downloads grouped by network, with play / share / delete. */
class VideosActivity : AppCompatActivity() {
    private lateinit var b: ActivityVideosBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityVideosBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupHeader(R.string.menu_videos, b.root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val done = History.all(this).filter { History.state(this, it) == History.State.DONE }
        // keep the empty-state view, drop everything added before
        (b.content.childCount - 1 downTo 0).map { b.content.getChildAt(it) }.filter { it.id != R.id.empty }.forEach(b.content::removeView)
        b.empty.visibility = if (done.isEmpty()) View.VISIBLE else View.GONE

        Networks.extractors.forEach { ex ->
            val videos = done.filter { it.network == ex.name }
            if (videos.isEmpty()) return@forEach
            val header = ItemSectionBinding.inflate(layoutInflater, b.content, false)
            header.icon.setImageResource(Networks.icon(ex.name))
            header.label.text = "${ex.name} · ${videos.size}"
            b.content.addView(header.root, b.content.indexOfChild(b.empty))
            videos.forEach { item ->
                val row = ItemVideoBinding.inflate(layoutInflater, b.content, false)
                row.title.text = DateUtils.getRelativeTimeSpanString(item.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
                row.subtitle.text = item.link
                row.root.setOnClickListener { VideoActions.open(this, item) }
                row.btnShare.setOnClickListener { VideoActions.share(this, item) }
                row.btnDelete.setOnClickListener { confirmDelete(item) }
                if (item.audio) row.thumb.setImageResource(R.drawable.ic_music) else VideoActions.thumbnail(this, item, row.thumb)
                b.content.addView(row.root, b.content.indexOfChild(b.empty))
            }
        }
    }

    private fun confirmDelete(item: History.Item) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(R.string.dialog_delete_message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                VideoActions.delete(this, item)
                refresh()
            }
            .show()
    }
}