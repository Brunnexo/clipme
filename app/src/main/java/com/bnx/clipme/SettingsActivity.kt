package com.bnx.clipme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings as AndroidSettings
import android.os.Build
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.bnx.clipme.databinding.ActivitySettingsBinding
import com.bnx.clipme.databinding.ItemSettingBinding

/** Download quality per network and where downloads are stored. */
class SettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySettingsBinding

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            Settings.setFolder(this, uri.toString())
            Settings.setLocation(this, Location.FOLDER)
        }
        renderLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupHeader(R.string.menu_settings, b.root)

        Networks.extractors.forEach { ex ->
            val row = ItemSettingBinding.inflate(layoutInflater, b.qualityList, false)
            row.name.text = ex.name
            row.icon.setImageResource(Networks.icon(ex.name))
            val ids = mapOf(Quality.BEST to row.btnBest.id, Quality.MEDIUM to row.btnMedium.id, Quality.LOW to row.btnLow.id)
            row.toggle.check(ids.getValue(Settings.quality(this, ex.name)))
            row.toggle.addOnButtonCheckedListener { _, id, checked ->
                if (checked) Settings.setQuality(this, ex.name, ids.entries.first { it.value == id }.key)
            }
            b.qualityList.addView(row.root)
        }

        b.rbGallery.setOnClickListener { Settings.setLocation(this, Location.GALLERY); renderLocation() }
        b.rbPrivate.setOnClickListener { Settings.setLocation(this, Location.PRIVATE); renderLocation() }
        b.rbFolder.setOnClickListener {
            if (Settings.folder(this) == null) pickFolder.launch(null)
            else { Settings.setLocation(this, Location.FOLDER); renderLocation() }
        }
        b.btnPickFolder.setOnClickListener { pickFolder.launch(null) }
        val formats = mapOf(YoutubeFormat.ASK to b.btnFmtAsk.id, YoutubeFormat.VIDEO to b.btnFmtVideo.id, YoutubeFormat.AUDIO to b.btnFmtAudio.id)
        b.ytFormat.check(formats.getValue(Settings.youtubeFormat(this)))
        b.ytFormat.addOnButtonCheckedListener { _, id, checked ->
            if (checked) Settings.setYoutubeFormat(this, formats.entries.first { it.value == id }.key)
        }
        b.swCapture.isChecked = Settings.autoCapture(this)
        b.swCapture.setOnCheckedChangeListener { _, on ->
            Settings.setAutoCapture(this, on)
            if (on) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                if (!ClipAccessibilityService.isEnabled(this)) openAccessibilitySettings()
                ClipboardService.start(this)
            } else ClipboardService.stop(this)
            renderCapture()
        }
        b.captureStatus.setOnClickListener { if (!ClipAccessibilityService.isEnabled(this)) openAccessibilitySettings() }
        b.btnAppInfo.setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        b.btnYouTube.setOnClickListener {
            if (YouTubeAuth.isLoggedIn()) {
                YouTubeAuth.clear(this)
                renderAccount()
            } else startActivity(Intent(this, YouTubeLoginActivity::class.java))
        }
        renderLocation()
    }

    override fun onResume() {
        super.onResume()
        renderAccount()
        renderCapture()
    }

    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }


    private fun openAccessibilitySettings() = startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun renderCapture() {
        b.btnAppInfo.visibility =
            if (Settings.autoCapture(this) && !ClipAccessibilityService.isEnabled(this)) android.view.View.VISIBLE else android.view.View.GONE
        b.captureStatus.text = when {
            !Settings.autoCapture(this) -> ""
            ClipAccessibilityService.isEnabled(this) -> getString(R.string.capture_status_on)
            else -> getString(R.string.capture_status_a11y_off)
        }
    }

    private fun renderAccount() {
        val on = YouTubeAuth.isLoggedIn()
        b.ytStatus.text = getString(if (on) R.string.yt_connected else R.string.yt_disconnected)
        b.btnYouTube.setText(if (on) R.string.sign_out else R.string.sign_in)
    }

    private fun renderLocation() {
        b.locGroup.check(
            when (Settings.location(this)) {
                Location.GALLERY -> b.rbGallery.id
                Location.FOLDER -> b.rbFolder.id
                Location.PRIVATE -> b.rbPrivate.id
            }
        )
        b.folderLabel.text = Settings.folder(this)
            ?.let { DocumentFile.fromTreeUri(this, Uri.parse(it))?.name }
            ?: getString(R.string.no_folder)
    }
}