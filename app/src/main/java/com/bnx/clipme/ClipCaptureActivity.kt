package com.bnx.clipme

import android.app.Activity
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible activity that exists only to get window focus, the condition for reading the clipboard on Android 10+,
 * then hands the copied link to [ClipboardService]. Opened by the notification button, or by
 * [ClipAccessibilityService] after a copy ([EXTRA_AUTO]: stays silent when the clip is not a supported link).
 */
class ClipCaptureActivity : Activity() {
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || done) return
        done = true
        val auto = intent.getBooleanExtra(EXTRA_AUTO, false)
        val text = getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        val started = text != null && ClipboardService.capture(this, text, force = !auto)
        if (!started && !auto) Toast.makeText(this, R.string.toast_no_link, Toast.LENGTH_SHORT).show()
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_AUTO = "auto"
    }
}