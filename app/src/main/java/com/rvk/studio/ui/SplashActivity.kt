package com.rvk.studio.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rvk.studio.R
import com.rvk.studio.bridge.NativeBridge
import com.rvk.studio.bridge.WebSocketServer
import com.rvk.studio.storage.StorageManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        statusText = findViewById(R.id.splashStatus)

        // Initialize components in background
        Handler(Looper.getMainLooper()).postDelayed({
            statusText.text = "Checking permissions..."
        }, 500)

        Handler(Looper.getMainLooper()).postDelayed({
            statusText.text = "Initializing storage..."
            initializeStorage()
        }, 1000)

        Handler(Looper.getMainLooper()).postDelayed({
            statusText.text = "Starting backend server..."
            initializeBackend()
        }, 1500)

        Handler(Looper.getMainLooper()).postDelayed({
            statusText.text = "Loading IDE..."
            navigateToMain()
        }, 2500)
    }

    private fun initializeStorage() {
        // Create base directories
        StorageManager.getBaseDir(this)
        StorageManager.getProjectsDir(this)
        StorageManager.getSdkDir(this)
        StorageManager.getTempDir(this)
    }

    private fun initializeBackend() {
        // Start WebSocket/HTTP server
        try {
            val server = WebSocketServer.getInstance(this)
            if (!server.isRunning()) {
                server.start()
            }
        } catch (e: Exception) {
            // Server start failure is non-fatal
        }

        // Initialize native bridge
        try {
            val bridge = NativeBridge(this)
            bridge.initialize()
        } catch (e: Exception) {
            // Native bridge failure is non-fatal
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
