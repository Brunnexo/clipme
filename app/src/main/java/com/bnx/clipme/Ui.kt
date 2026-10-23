package com.bnx.clipme

import android.view.View
import androidx.annotation.StringRes
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Wires the shared `view_header` (back + title) and system-bar insets of a secondary screen. */
fun AppCompatActivity.setupHeader(title: Int, root: View) {
    findViewById<TextView>(R.id.screenTitle).setText(title)
    findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}
/** String from the app context, for code without an Activity (extractors, downloader); follows the system language. */
fun tr(@StringRes id: Int, vararg args: Any): String = ClipmeApp.instance.getString(id, *args)
