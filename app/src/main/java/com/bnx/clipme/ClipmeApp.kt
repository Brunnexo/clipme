package com.bnx.clipme

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class ClipmeApp : Application() {
    companion object {
        lateinit var instance: ClipmeApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // The neo-brutalist palette (cream paper, black ink) is designed for light mode only.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        YouTubeAuth.init(this)
    }
}