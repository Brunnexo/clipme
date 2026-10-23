package com.bnx.clipme

import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

/** Minimal in-app player for the video uri passed as intent data. */
class PlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val video = VideoView(this)
        setContentView(
            FrameLayout(this).apply {
                addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            }
        )
        video.setMediaController(MediaController(this).also { it.setAnchorView(video) })
        video.setVideoURI(intent.data)
        video.setOnPreparedListener { video.start() }
    }
}