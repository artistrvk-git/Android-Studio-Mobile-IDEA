package com.rvk.studio

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class RVKStudioApp : Application() {

    companion object {
        const val CHANNEL_BUILD = "build_channel"
        const val CHANNEL_SDK = "sdk_channel"
        lateinit var instance: RVKStudioApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val buildChannel = NotificationChannel(
                CHANNEL_BUILD,
                "Build Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows build progress and status"
            }

            val sdkChannel = NotificationChannel(
                CHANNEL_SDK,
                "SDK Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows SDK download progress"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(buildChannel)
            manager.createNotificationChannel(sdkChannel)
        }
    }
}
