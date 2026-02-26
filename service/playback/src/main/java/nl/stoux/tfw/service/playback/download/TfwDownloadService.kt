package nl.stoux.tfw.service.playback.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import dagger.hilt.android.AndroidEntryPoint
import nl.stoux.tfw.service.playback.R
import javax.inject.Inject

/**
 * Foreground service that handles liveset downloads in the background.
 * Uses Media3's DownloadService infrastructure for reliable downloads.
 */
@UnstableApi
@AndroidEntryPoint
class TfwDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {
    @Inject
    lateinit var downloadManagerProvider: DownloadManager

    @Inject
    lateinit var downloadNotificationHelper: DownloadNotificationHelper

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 2
        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "tfw_download_channel"
        private const val JOB_ID = 1

        /**
         * Starts the download service to process any pending downloads.
         */
        fun start(context: Context) {
            val intent = Intent(context, TfwDownloadService::class.java).apply {
                action = ACTION_INIT
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Sends a download request to the service.
         */
        fun sendAddDownload(
            context: Context,
            downloadRequest: androidx.media3.exoplayer.offline.DownloadRequest,
            foreground: Boolean,
        ) {
            DownloadService.sendAddDownload(context, TfwDownloadService::class.java, downloadRequest, foreground)
        }

        /**
         * Sends a remove download request to the service.
         */
        fun sendRemoveDownload(
            context: Context,
            downloadId: String,
            foreground: Boolean,
        ) {
            DownloadService.sendRemoveDownload(context, TfwDownloadService::class.java, downloadId, foreground)
        }

        /**
         * Sends a pause downloads request to the service.
         */
        fun sendPauseDownloads(
            context: Context,
            foreground: Boolean,
        ) {
            DownloadService.sendPauseDownloads(context, TfwDownloadService::class.java, foreground)
        }

        /**
         * Sends a resume downloads request to the service.
         */
        fun sendResumeDownloads(
            context: Context,
            foreground: Boolean,
        ) {
            DownloadService.sendResumeDownloads(context, TfwDownloadService::class.java, foreground)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun getDownloadManager(): DownloadManager = downloadManagerProvider

    override fun getScheduler(): Scheduler? {
        // Use PlatformScheduler to resume downloads after device reboot or when network becomes available
        return PlatformScheduler(this, JOB_ID)
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return downloadNotificationHelper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            /* contentIntent */ null,
            /* message */ buildProgressMessage(downloads),
            downloads,
            notMetRequirements,
        )
    }

    private fun buildProgressMessage(downloads: List<Download>): String? {
        val inProgress = downloads.filter { it.state == Download.STATE_DOWNLOADING }
        if (inProgress.isEmpty()) return null

        return if (inProgress.size == 1) {
            val download = inProgress.first()
            val progress = (download.percentDownloaded).toInt()
            "Downloading liveset... $progress%"
        } else {
            "Downloading ${inProgress.size} livesets..."
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            DOWNLOAD_NOTIFICATION_CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Liveset download progress"
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
