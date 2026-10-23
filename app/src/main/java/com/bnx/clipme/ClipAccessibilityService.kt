package com.bnx.clipme

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.view.accessibility.AccessibilityEvent

/**
 * Makes auto-capture hands-free. Android 10+ hides the clipboard (and its change events) from background apps, even
 * from accessibility services (tested), but an accessibility service may start activities from the background and
 * sees UI events. So it only watches for "copy" actions (a click or toast whose label mentions copy/copiar/...) and
 * flashes [ClipCaptureActivity], which has focus and can therefore read the clip and start the download.
 * It reads no screen content beyond those short event labels, and ignores everything while auto-capture is off.
 */
class ClipAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastTrigger = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName == packageName || !Settings.autoCapture(this)) return
        val label = buildString {
            event.text?.forEach { append(it).append(' ') }
            append(event.contentDescription ?: "")
        }
        if (!COPY.containsMatchIn(label)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastTrigger < 1500) return
        lastTrigger = now
        // give the app a moment to put the link on the clipboard
        handler.postDelayed({
            runCatching {
                startActivity(
                    Intent(this, ClipCaptureActivity::class.java)
                        .putExtra(ClipCaptureActivity.EXTRA_AUTO, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }, 400)
    }

    override fun onInterrupt() {}

    companion object {
        /** copy, copied, copiar, copiado/a, copié, copier... */
        private val COPY = Regex("(?i)cop[iy]")

        fun isEnabled(c: Context): Boolean {
            val enabled = AndroidSettings.Secure.getString(c.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return enabled?.contains(ComponentName(c, ClipAccessibilityService::class.java).flattenToString()) == true
        }
    }
}