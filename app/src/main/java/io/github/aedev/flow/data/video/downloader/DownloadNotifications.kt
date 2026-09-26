package io.github.aedev.flow.data.video.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.R
import java.util.Locale

/**
 * The download service's notifications: one per download, plus the queue summary that doubles as
 * the foreground notification.
 */
internal class DownloadNotifications(
    private val service: Service,
    private val missions: () -> Collection<FlowDownloadMission>,
) {
    // Last text posted on the summary, so an unchanged summary is not re-posted 4x/second
    @Volatile
    private var lastSummary: String? = null

    /** Forgets the posted summary, so the next one is posted even when its text is unchanged. */
    fun resetSummary() {
        lastSummary = null
    }

    private fun openAppIntent(requestCode: Int): PendingIntent {
        val tapIntent =
            Intent(service, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        return PendingIntent.getActivity(
            service,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * The service's own foreground notification, describing the whole queue rather than one video.
     *
     * It is the group summary, so Android folds it away while a single download is running and only
     * shows it once there are several. Marking it as such is what stops it from reading as a second,
     * frozen copy of the download that is already listed below it.
     */
    private fun buildSummaryNotification(
        text: String,
        indeterminate: Boolean,
    ): android.app.Notification =
        NotificationCompat
            .Builder(service, FlowDownloadService.CHANNEL_ID)
            .setContentTitle(service.getString(R.string.notification_channel_downloads_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(0, 0, indeterminate)
            .setContentIntent(openAppIntent(FOREGROUND_NOTIFICATION_ID))
            .setGroup(FlowDownloadService.NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .build()

    /**
     * Repoints the foreground notification at what the service is currently doing.
     *
     * Previously it was posted once, from the service's onStartCommand, and never touched again — it stayed on
     * "Download started…" for the rest of the service's life. That is most visible after a pause,
     * where the service deliberately stays alive so the download can be resumed.
     */
    private fun refreshSummary() {
        val outstanding = missions().filterNot { it.isFinished() || it.isFailed() }
        if (outstanding.isEmpty()) return
        val active = outstanding.count { it.status == MissionStatus.RUNNING || it.status == MissionStatus.PENDING }
        val summary =
            if (active > 0) {
                service.resources.getQuantityString(R.plurals.notification_downloads_active, active, active)
            } else {
                service.resources.getQuantityString(R.plurals.notification_downloads_paused, outstanding.size, outstanding.size)
            }
        if (summary == lastSummary) return
        lastSummary = summary
        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(FOREGROUND_NOTIFICATION_ID, buildSummaryNotification(summary, indeterminate = active > 0))
    }

    fun startForegroundPlaceholder() {
        lastSummary = null
        startDataSyncForeground(
            buildSummaryNotification(service.getString(R.string.download_started_toast), indeterminate = true),
        )
    }

    private fun startDataSyncForeground(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            service,
            FOREGROUND_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun createNotification(
        mission: FlowDownloadMission,
        videoId: String,
        isComplete: Boolean = false,
        isMuxing: Boolean = false,
    ): android.app.Notification {
        val progress = (mission.progress * 100).toInt()
        val contentText =
            when {
                isComplete -> {
                    mission.fallbackFolder?.let { service.getString(R.string.notification_download_saved_elsewhere, it) }
                        ?: service.getString(R.string.notification_download_complete)
                }

                isMuxing -> {
                    service.getString(R.string.download_merging_audio_video)
                }

                mission.isFailed() -> {
                    mission.error ?: service.getString(R.string.notification_download_failed)
                }

                mission.status == MissionStatus.PAUSED -> {
                    service.getString(
                        R.string.notification_download_paused,
                        mission.error ?: service.getString(R.string.notification_download_paused_hint),
                    )
                }

                else -> {
                    service.getString(
                        R.string.notification_download_progress,
                        progress,
                        formatBytes(mission.downloadedBytes + mission.audioDownloadedBytes),
                        formatBytes(mission.totalBytes + mission.audioTotalBytes),
                    )
                }
            }

        val tapPendingIntent = openAppIntent(videoId.hashCode())

        val builder =
            NotificationCompat
                .Builder(service, FlowDownloadService.CHANNEL_ID)
                .setContentTitle(mission.video.title)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true)
                .setContentIntent(tapPendingIntent)
                .setGroup(FlowDownloadService.NOTIFICATION_GROUP)

        if (!isComplete && !mission.isFailed()) {
            if (isMuxing) {
                builder.setProgress(100, 100, true)
            } else {
                builder.setProgress(100, progress, false)

                if (mission.status == MissionStatus.PAUSED) {
                    // Show Resume button
                    val resumeIntent =
                        Intent(service, FlowDownloadService::class.java).apply {
                            action = FlowDownloadService.ACTION_RESUME_DOWNLOAD
                            putExtra("video_id", videoId)
                        }
                    val resumePending =
                        PendingIntent.getService(
                            service,
                            "resume_$videoId".hashCode(),
                            resumeIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    builder.addAction(android.R.drawable.ic_media_play, service.getString(R.string.resume), resumePending)
                } else {
                    // Show Pause button
                    val pauseIntent =
                        Intent(service, FlowDownloadService::class.java).apply {
                            action = FlowDownloadService.ACTION_PAUSE_DOWNLOAD
                            putExtra("video_id", videoId)
                        }
                    val pausePending =
                        PendingIntent.getService(
                            service,
                            "pause_$videoId".hashCode(),
                            pauseIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    builder.addAction(android.R.drawable.ic_media_pause, service.getString(R.string.pause), pausePending)
                }

                // Cancel button
                val cancelIntent =
                    Intent(service, FlowDownloadService::class.java).apply {
                        action = FlowDownloadService.ACTION_CANCEL_DOWNLOAD
                        putExtra("video_id", videoId)
                    }
                val cancelPending =
                    PendingIntent.getService(
                        service,
                        "cancel_$videoId".hashCode(),
                        cancelIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, service.getString(R.string.cancel), cancelPending)
            }
        } else {
            builder.setProgress(0, 0, false)
            if (isComplete) {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                builder.setAutoCancel(true)
            }
        }

        return builder.build()
    }

    fun update(
        mission: FlowDownloadMission,
        videoId: String,
        isComplete: Boolean = false,
        isMuxing: Boolean = false,
    ) {
        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId(videoId), createNotification(mission, videoId, isComplete, isMuxing))
        refreshSummary()
    }

    /**
     * Leaves a download's notification on a state the download can actually be in.
     *
     * Called from `finally`, so it also runs when the coroutine is cancelled part-way — the case
     * that used to leave "Merging audio & video…" on screen beside a file that was already finished
     * and moved to its destination. A mission that is no longer tracked was cancelled by the user,
     * and `handleCancel` has already taken its notification down.
     */
    fun settle(
        mission: FlowDownloadMission,
        videoId: String,
        tracked: Boolean,
    ) {
        when (settledNotificationFor(mission.status, tracked)) {
            SettledNotification.COMPLETE -> {
                update(mission, videoId, isComplete = true)
            }

            SettledNotification.KEEP_STATE -> {
                update(mission, videoId)
            }

            SettledNotification.DISMISS -> {
                cancel(videoId)
                refreshSummary()
            }
        }
    }

    fun cancel(videoId: String) {
        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId(videoId))
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    FlowDownloadService.CHANNEL_ID,
                    service.getString(R.string.notification_channel_downloads_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = service.getString(R.string.notification_download_progress_description)
                }
            val nm = service.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun notificationId(videoId: String): Int {
        val hash = videoId.hashCode()
        return when (hash) {
            0 -> 1
            FOREGROUND_NOTIFICATION_ID -> hash xor Int.MIN_VALUE
            else -> hash
        }
    }

    private fun formatBytes(bytes: Long): String =
        when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024 * 1024 * 1024.0))
            bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024 * 1024.0))
            bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }

    private companion object {
        const val FOREGROUND_NOTIFICATION_ID = 724
    }
}
