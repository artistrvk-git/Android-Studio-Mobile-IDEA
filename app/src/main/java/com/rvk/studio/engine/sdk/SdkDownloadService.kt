package com.rvk.studio.engine.sdk

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rvk.studio.R
import com.rvk.studio.RVKStudioApp
import com.rvk.studio.storage.StorageManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class SdkDownloadService : Service() {

    companion object {
        const val TAG = "SdkDownloadService"
        const val NOTIFICATION_ID = 2001
        const val ACTION_DOWNLOAD_ALL = "download_all"
        const val ACTION_DOWNLOAD_COMPONENT = "download_component"
        const val EXTRA_COMPONENT_NAME = "component_name"

        private var listener: SdkDownloadListener? = null
        private var isRunning = false

        fun setListener(l: SdkDownloadListener?) {
            listener = l
        }

        fun isRunning(): Boolean = isRunning

        fun startDownloadAll(context: Context) {
            val intent = Intent(context, SdkDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD_ALL
            }
            context.startForegroundService(intent)
        }

        fun startDownloadComponent(context: Context, componentName: String) {
            val intent = Intent(context, SdkDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD_COMPONENT
                putExtra(EXTRA_COMPONENT_NAME, componentName)
            }
            context.startForegroundService(intent)
        }
    }

    interface SdkDownloadListener {
        fun onProgress(component: String, progress: Int, message: String)
        fun onComponentComplete(component: String, success: Boolean, message: String)
        fun onAllComplete(success: Boolean, message: String)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Initializing SDK downloads..."))
        isRunning = true

        when (intent?.action) {
            ACTION_DOWNLOAD_ALL -> downloadAll()
            ACTION_DOWNLOAD_COMPONENT -> {
                val name = intent.getStringExtra(EXTRA_COMPONENT_NAME) ?: return START_NOT_STICKY
                downloadSingle(name)
            }
        }

        return START_NOT_STICKY
    }

    private fun downloadAll() {
        scope.launch {
            val components = SdkConfig.getAllComponents()
            var allSuccess = true

            for ((index, component) in components.withIndex()) {
                val sdkDir = StorageManager.getSdkDir(this@SdkDownloadService)
                val targetDir = File(sdkDir, component.targetDir)

                // Skip if already downloaded
                if (targetDir.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
                    listener?.onProgress(component.name, 100, "${component.description} already installed")
                    listener?.onComponentComplete(component.name, true, "Already installed")
                    continue
                }

                val success = downloadAndExtract(component, sdkDir)
                if (!success) allSuccess = false

                val overallProgress = ((index + 1) * 100) / components.size
                updateNotification("SDK Setup: $overallProgress% complete")
            }

            // Make all binaries executable
            val sdkDir = StorageManager.getSdkDir(this@SdkDownloadService)
            StorageManager.makeExecutableRecursive(sdkDir)

            withContext(Dispatchers.Main) {
                listener?.onAllComplete(allSuccess, if (allSuccess) "All SDK components installed successfully" else "Some components failed to install")
            }

            isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun downloadSingle(componentName: String) {
        scope.launch {
            val component = SdkConfig.getComponentByName(componentName)
            if (component == null) {
                listener?.onAllComplete(false, "Unknown component: $componentName")
                stopSelf()
                return@launch
            }

            val sdkDir = StorageManager.getSdkDir(this@SdkDownloadService)
            val success = downloadAndExtract(component, sdkDir)

            // Make binaries executable
            StorageManager.makeExecutableRecursive(File(sdkDir, component.targetDir))

            withContext(Dispatchers.Main) {
                listener?.onAllComplete(success, if (success) "${component.description} installed" else "Failed to install ${component.description}")
            }

            isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun downloadAndExtract(component: SdkComponent, sdkDir: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                listener?.onProgress(component.name, 0, "Downloading ${component.description}...")
                updateNotification("Downloading ${component.description}...")

                val tempFile = File(StorageManager.getTempDir(this@SdkDownloadService), "${component.name}.zip")
                tempFile.parentFile?.mkdirs()

                // Download
                val request = Request.Builder().url(component.url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed for ${component.name}: ${response.code}")
                    listener?.onComponentComplete(component.name, false, "Download failed: HTTP ${response.code}")
                    return@withContext false
                }

                val body = response.body ?: run {
                    listener?.onComponentComplete(component.name, false, "Empty response")
                    return@withContext false
                }

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                FileOutputStream(tempFile).use { fos ->
                    BufferedInputStream(body.byteStream()).use { bis ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (bis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                listener?.onProgress(component.name, progress, "Downloading ${component.description}: $progress%")
                            }
                        }
                    }
                }

                listener?.onProgress(component.name, 90, "Extracting ${component.description}...")
                updateNotification("Extracting ${component.description}...")

                // Extract
                val targetDir = File(sdkDir, component.targetDir)
                targetDir.mkdirs()

                ZipInputStream(tempFile.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(targetDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                val buffer = ByteArray(8192)
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                // Cleanup temp file
                tempFile.delete()

                listener?.onProgress(component.name, 100, "${component.description} installed successfully")
                listener?.onComponentComplete(component.name, true, "Installed successfully")

                Log.i(TAG, "Successfully installed ${component.name} to ${targetDir.absolutePath}")
                true

            } catch (e: Exception) {
                Log.e(TAG, "Error downloading ${component.name}", e)
                listener?.onComponentComplete(component.name, false, "Error: ${e.message}")
                false
            }
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, RVKStudioApp.CHANNEL_SDK)
            .setContentTitle("Android Studio Mobile - SDK Setup")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        scope.cancel()
        isRunning = false
        super.onDestroy()
    }
}
