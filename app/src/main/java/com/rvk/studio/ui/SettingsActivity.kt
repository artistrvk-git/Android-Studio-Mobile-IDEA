package com.rvk.studio.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.rvk.studio.R
import com.rvk.studio.engine.sdk.SdkDownloadService
import com.rvk.studio.storage.StorageManager
import java.io.File

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupViews()
        loadSdkStatus()
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackSettings).setOnClickListener {
            finish()
        }

        // Dark mode toggle
        val switchDarkMode = findViewById<SwitchMaterial>(R.id.switchDarkMode)
        switchDarkMode.isChecked = true // Default dark mode
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            // Theme switching would require activity recreation
            Toast.makeText(this,
                if (isChecked) "Dark mode enabled" else "Light mode enabled",
                Toast.LENGTH_SHORT).show()
        }

        // Font size
        val seekFontSize = findViewById<SeekBar>(R.id.seekFontSize)
        val tvFontSize = findViewById<TextView>(R.id.tvFontSize)
        seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val fontSize = 8 + progress
                tvFontSize.text = "${fontSize}sp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // SDK Download button
        findViewById<MaterialButton>(R.id.btnDownloadSdks).setOnClickListener {
            SdkDownloadService.startDownloadAll(this)
            Toast.makeText(this, "SDK download started. Check notification for progress.", Toast.LENGTH_LONG).show()
        }

        // JDK version
        val rgJdkVersion = findViewById<RadioGroup>(R.id.rgJdkVersion)
        rgJdkVersion.setOnCheckedChangeListener { _, checkedId ->
            val version = when (checkedId) {
                R.id.rbJdk17 -> "17"
                R.id.rbJdk21 -> "21"
                else -> "17"
            }
            Toast.makeText(this, "JDK $version selected", Toast.LENGTH_SHORT).show()
        }

        // Gradle version
        val rgGradleVersion = findViewById<RadioGroup>(R.id.rgGradleVersion)
        rgGradleVersion.setOnCheckedChangeListener { _, checkedId ->
            val version = when (checkedId) {
                R.id.rbGradle814 -> "8.14.4"
                R.id.rbGradle930 -> "9.3.0"
                R.id.rbGradle940 -> "9.4.0"
                else -> "8.14.4"
            }
            Toast.makeText(this, "Gradle $version selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSdkStatus() {
        val tvSdkStatus = findViewById<TextView>(R.id.tvSdkStatus)
        val sdkDir = StorageManager.getSdkDir(this)

        val components = mapOf(
            "JDK 17" to File(sdkDir, "jdk17"),
            "JDK 21" to File(sdkDir, "jdk21"),
            "NDK" to File(sdkDir, "ndk"),
            "SDK Tools" to File(sdkDir, "sdk-tools"),
            "Gradle 8.14.4" to File(sdkDir, "gradle-8.14.4"),
            "Gradle 9.3.0" to File(sdkDir, "gradle-9.3.0"),
            "Gradle 9.4.0" to File(sdkDir, "gradle-9.4.0"),
            "Flutter" to File(sdkDir, "flutter"),
            "Node.js" to File(sdkDir, "nodejs"),
            "Python" to File(sdkDir, "python")
        )

        val statusText = StringBuilder("SDK Status:\n\n")
        for ((name, dir) in components) {
            val installed = dir.exists() && (dir.listFiles()?.isNotEmpty() ?: false)
            val status = if (installed) "✓ Installed" else "✗ Not installed"
            statusText.appendLine("$name: $status")
        }

        tvSdkStatus.text = statusText.toString()
    }
}
