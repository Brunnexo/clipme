package com.bnx.clipme

import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Minimal in-app player for the uri passed as intent data (a note icon is shown over the black screen for audio). */
class PlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val video = VideoView(this)
        val root = FrameLayout(this).apply { addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)) }
        if (intent.getBooleanExtra(EXTRA_AUDIO, false)) {
            val size = (120 * resources.displayMetrics.density).toInt()
            root.addView(
                ImageView(this).apply {
                    setImageResource(R.drawable.ic_music)
                    setColorFilter(ContextCompat.getColor(context, R.color.yellow))
                },
                FrameLayout.LayoutParams(size, size, Gravity.CENTER)
            )
        }
        setContentView(root)
        video.setMediaController(MediaController(this).also { it.setAnchorView(video) })
        video.setVideoURI(intent.data)
        video.setOnPreparedListener { video.start() }
    }

    companion object {
        const val EXTRA_AUDIO = "audio"
    }
}