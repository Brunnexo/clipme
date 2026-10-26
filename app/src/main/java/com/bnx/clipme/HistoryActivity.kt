package com.bnx.clipme

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.bnx.clipme.databinding.ActivityHistoryBinding
import com.bnx.clipme.databinding.ItemDownloadBinding

/** Every download attempt, newest first, tagged with its network. */
class HistoryActivity : AppCompatActivity() {
    private lateinit var b: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupHeader(R.string.menu_history, b.root)
        findViewById<View>(R.id.btnAction).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                History.clear(this@HistoryActivity)
                refresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = History.all(this)
        (b.content.childCount - 1 downTo 0).map { b.content.getChildAt(it) }.filter { it.id != R.id.empty }.forEach(b.content::removeView)
        b.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnAction).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        items.forEach { item ->
            val row = ItemDownloadBinding.inflate(layoutInflater, b.content, false)
            val state = History.state(this, item)
            val (icon, label) = when (state) {
                History.State.DONE -> R.drawable.ic_check_circle to getString(R.string.state_done)
                History.State.RUNNING -> R.drawable.ic_download to getString(R.string.status_downloading)
                History.State.FAILED -> R.drawable.ic_error to getString(R.string.state_failed)
                History.State.GONE -> R.drawable.ic_delete to getString(R.string.state_gone)
            }
            row.icon.setImageResource(icon)
            row.state.text = label
            row.title.text = if (item.audio) "${item.network} · MP3" else item.network
            row.subtitle.text = "${DateUtils.getRelativeTimeSpanString(item.time)} · ${item.link}"
            if (state == History.State.DONE) row.root.setOnClickListener { VideoActions.open(this, item) }
            b.content.addView(row.root, b.content.indexOfChild(b.empty))
        }
    }
}