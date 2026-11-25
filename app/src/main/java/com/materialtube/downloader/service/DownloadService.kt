package com.materialtube.downloader.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.materialtube.downloader.MainActivity
import com.materialtube.downloader.MaterialTubeApp
import com.materialtube.downloader.engine.JobStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Preparing download...", 0, indeterminate = true))
        val repo = (application as MaterialTubeApp).downloadRepository
        scope.launch {
            repo.jobs.collectLatest { jobs ->
                val active = jobs.firstOrNull {
                    it.status == JobStatus.DOWNLOADING || it.status == JobStatus.PROCESSING
                }
                val queuedCount = jobs.count { it.status == JobStatus.QUEUED }

                if (active == null && queuedCount == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else if (active != null) {
                    val pct = active.progress.toInt().coerceIn(0, 100)
                    val title = active.title.take(48)
                    val subtitle = "${active.phase} ($pct%)"
                    val nm = getSystemService(android.app.NotificationManager::class.java)
                    nm?.notify(
                        NOTIFICATION_ID,
                        buildNotification("$title — $subtitle", pct, active.status == JobStatus.PROCESSING),
                    )
                }
            }
        }
    }

    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, MaterialTubeApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("MediaSave")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
