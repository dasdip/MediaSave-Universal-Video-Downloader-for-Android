package com.materialtube.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.materialtube.downloader.data.SettingsStore
import com.materialtube.downloader.engine.DownloadRepository
import com.materialtube.downloader.engine.EngineManager

class MaterialTubeApp : Application() {

    lateinit var settingsStore: SettingsStore
        private set
    lateinit var engineManager: EngineManager
        private set
    lateinit var downloadRepository: DownloadRepository
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        settingsStore = SettingsStore(this)
        engineManager = EngineManager(this)
        downloadRepository = DownloadRepository(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.download_channel_desc)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "materialtube_downloads"
    }
}
