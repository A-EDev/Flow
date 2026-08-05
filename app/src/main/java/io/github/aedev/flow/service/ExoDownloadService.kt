package io.github.aedev.flow.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import io.github.aedev.flow.R
import io.github.aedev.flow.data.download.DownloadUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ExoDownloadService : DownloadService(
    NOTIFICATION_ID,
    1000L,
    CHANNEL_ID,
    R.string.download,
    0
) {
    @Inject
    lateinit var downloadUtil: DownloadUtil

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val JOB_ID = 1
        const val NOTIFICATION_ID = 1
    }

    override fun getDownloadManager(): DownloadManager {
        return downloadUtil.getDownloadManagerInstance()
    }

    override fun getScheduler(): Scheduler? {
        return PlatformScheduler(this, JOB_ID)
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return downloadUtil.downloadNotificationHelper.buildProgressNotification(
            this,
            io.github.aedev.flow.R.drawable.ic_music_note,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }
}
