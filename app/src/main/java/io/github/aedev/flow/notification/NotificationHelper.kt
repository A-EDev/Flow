package io.github.aedev.flow.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.entity.NotificationEntity
import io.github.aedev.flow.data.update.AppRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Comprehensive notification helper for the app
 * Handles all notification channels and provides methods for showing various notification types
 */
object NotificationHelper {
    // Notification Channel IDs
    const val CHANNEL_DOWNLOADS = "downloads_channel"
    const val CHANNEL_SUBSCRIPTIONS = "subscriptions_channel"
    const val CHANNEL_PLAYBACK = "playback_channel"
    const val CHANNEL_MUSIC_PLAYBACK = "music_playback_channel"
    const val CHANNEL_GENERAL = "general_channel"
    const val CHANNEL_REMINDERS = "reminders_channel"
    const val CHANNEL_UPDATES = "updates_channel"
    const val EXTRA_OPEN_UPDATE = "io.github.aedev.flow.extra.OPEN_UPDATE"
    const val CHANNEL_IMPORTS = "imports_channel"

    // Notification IDs
    const val NOTIFICATION_DOWNLOAD_PROGRESS = 1001
    const val NOTIFICATION_DOWNLOAD_COMPLETE = 1002
    const val NOTIFICATION_DOWNLOAD_FAILED = 1003
    const val NOTIFICATION_NEW_VIDEO = 2000 // Base ID, per-video IDs are this + (videoId hash & 0xFFFF)
    const val NOTIFICATION_NEW_VIDEO_SUMMARY = 1999 // Group summary; kept below the per-video range
    private const val GROUP_NEW_VIDEOS = "new_videos"
    const val NOTIFICATION_PLAYBACK = 3001
    const val NOTIFICATION_MUSIC_PLAYBACK = 3002
    const val NOTIFICATION_GENERAL = 4000
    const val NOTIFICATION_REMINDER = 5000
    const val NOTIFICATION_IMPORT_PROGRESS = 6001
    const val NOTIFICATION_IMPORT_COMPLETE = 6002
    private const val NOTIFICATION_BITMAP_MAX_PX = 512

    private var channelsCreated = false

    /**
     * Store notification in database
     */
    private suspend fun storeNotification(
        context: Context,
        entity: NotificationEntity,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                db.notificationDao().insertNotification(entity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("NotificationHelper", "Failed to store notification", e)
            }
        }
    }

    /**
     * Initialize all notification channels
     * Should be called once when the app starts (e.g., in Application.onCreate())
     */
    fun createNotificationChannels(context: Context) {
        if (channelsCreated) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Downloads channel - High importance for active downloads
            val downloadsChannel =
                NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    context.getString(R.string.notification_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_downloads_description)
                    setShowBadge(true)
                    enableLights(true)
                    enableVibration(false)
                }

            // Subscriptions channel - Default importance for new videos
            val subscriptionsChannel =
                NotificationChannel(
                    CHANNEL_SUBSCRIPTIONS,
                    context.getString(R.string.notification_channel_new_videos),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notification_channel_new_videos_description)
                    setShowBadge(true)
                    enableLights(true)
                    enableVibration(true)
                }

            // Video playback channel - Low importance for background playback
            val playbackChannel =
                NotificationChannel(
                    CHANNEL_PLAYBACK,
                    context.getString(R.string.notification_channel_video_playback),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_video_playback_description)
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }

            // Music playback channel - Low importance for background music
            val musicPlaybackChannel =
                NotificationChannel(
                    CHANNEL_MUSIC_PLAYBACK,
                    context.getString(R.string.notification_channel_music_playback),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_music_playback_description)
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }

            // General notifications channel
            val generalChannel =
                NotificationChannel(
                    CHANNEL_GENERAL,
                    context.getString(R.string.notification_channel_general),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notification_channel_general_description)
                    setShowBadge(true)
                }

            val remindersChannel =
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    context.getString(R.string.notification_channel_reminders),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notification_channel_break_reminders_description)
                    setShowBadge(true)
                }

            // Updates channel
            val updatesChannel =
                NotificationChannel(
                    CHANNEL_UPDATES,
                    context.getString(R.string.notification_channel_updates),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_updates_description)
                    setShowBadge(true)
                }

            val importsChannel =
                NotificationChannel(
                    CHANNEL_IMPORTS,
                    context.getString(R.string.notification_channel_imports),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_import_description)
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }

            notificationManager.createNotificationChannels(
                listOf(
                    downloadsChannel,
                    subscriptionsChannel,
                    playbackChannel,
                    musicPlaybackChannel,
                    generalChannel,
                    remindersChannel,
                    updatesChannel,
                    importsChannel,
                ),
            )

            channelsCreated = true
        }
    }

    /**
     * Check if notification permission is granted (Android 13+)
     */
    suspend fun hasNotificationPermission(context: Context): Boolean {
        if (!PlayerPreferences(context).notificationsEnabled.first()) {
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ========== IMPORT NOTIFICATIONS ==========

    /**
     * Show (or update) the import-in-progress notification.
     * When total == 0 the progress bar is indeterminate.
     */
    suspend fun showImportProgress(
        context: Context,
        label: String,
        current: Int,
        total: Int,
    ) {
        if (!hasNotificationPermission(context)) return
        val contentText = if (total > 0) "$current / $total" else context.getString(R.string.notification_import_starting)
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_IMPORTS)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.notification_importing, label))
                .setContentText(contentText)
                .apply {
                    if (total > 0) {
                        setProgress(total, current, false)
                    } else {
                        setProgress(0, 0, true)
                    }
                }.setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_IMPORT_PROGRESS, builder.build())
    }

    /** Replace the progress notification with a one-shot completion notification. */
    suspend fun showImportComplete(
        context: Context,
        label: String,
        count: Int,
        message: String? = null,
    ) {
        if (!hasNotificationPermission(context)) return
        // cancel progress first
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_IMPORT_PROGRESS)
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_IMPORTS)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.notification_import_complete))
                .setContentText(message ?: context.getString(R.string.notification_imported, count, label.lowercase()))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_IMPORT_COMPLETE, builder.build())
    }

    /** Cancel the ongoing import progress notification (e.g. on error). */
    fun cancelImportNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_IMPORT_PROGRESS)
    }

    data class NewVideoEntry(
        val channelName: String,
        val videoTitle: String,
        val videoId: String,
        val thumbnailUrl: String?,
    )

    /**
     * Dispatcher for subscription update notifications.
     */
    suspend fun showSubscriptionUpdates(
        context: Context,
        videos: List<NewVideoEntry>,
    ) {
        if (!hasNotificationPermission(context)) return
        if (!PlayerPreferences(context).notifNewVideosEnabled.first()) return
        if (videos.isEmpty()) return

        videos.forEach { v ->
            storeNotification(
                context,
                NotificationEntity(
                    videoId = v.videoId,
                    title = v.videoTitle,
                    channelName = v.channelName,
                    thumbnailUrl = v.thumbnailUrl,
                    type = "NEW_VIDEO",
                ),
            )
        }

        val manager = NotificationManagerCompat.from(context)
        val multiple = videos.size > 1

        videos.forEach { v ->
            val notifId = NOTIFICATION_NEW_VIDEO + v.videoId.hashCode().and(0xFFFF)
            val watchIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("notification_video_id", v.videoId)
                    putExtra("video_id", v.videoId)
                    putExtra("video_title", v.videoTitle)
                }
            val watchPendingIntent =
                PendingIntent.getActivity(
                    context,
                    notifId,
                    watchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val builder =
                NotificationCompat
                    .Builder(context, CHANNEL_SUBSCRIPTIONS)
                    .setSmallIcon(R.drawable.ic_notification_logo)
                    .setContentTitle(v.channelName)
                    .setContentText(v.videoTitle)
                    .setContentIntent(watchPendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                    .setGroup(GROUP_NEW_VIDEOS)
            v.thumbnailUrl?.let { url ->
                getBitmapFromUrl(context, url)?.let { bm ->
                    builder.setLargeIcon(bm)
                    builder.setStyle(
                        NotificationCompat.BigPictureStyle().bigPicture(bm).bigLargeIcon(null as Bitmap?),
                    )
                }
            }
            manager.notify(notifId, builder.build())
        }

        if (!multiple) return

        val summaryIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val summaryPendingIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_NEW_VIDEO_SUMMARY,
                summaryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val inboxStyle =
            NotificationCompat
                .InboxStyle()
                .setBigContentTitle(
                    context.resources.getQuantityString(
                        R.plurals.notification_new_videos_from_subscriptions,
                        videos.size,
                        videos.size,
                    ),
                )
        videos.take(6).forEach { v ->
            inboxStyle.addLine("${v.channelName}: ${v.videoTitle}")
        }
        if (videos.size > 6) {
            inboxStyle.setSummaryText(context.getString(R.string.notification_more, videos.size - 6))
        }

        val summaryNotification =
            NotificationCompat
                .Builder(context, CHANNEL_SUBSCRIPTIONS)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.notification_new_videos))
                .setContentText(
                    context.resources.getQuantityString(
                        R.plurals.notification_new_videos_from_subscriptions,
                        videos.size,
                        videos.size,
                    ),
                ).setContentIntent(summaryPendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setStyle(inboxStyle)
                .setGroup(GROUP_NEW_VIDEOS)
                .setGroupSummary(true)
                .setNumber(videos.size)
                .build()

        manager.notify(NOTIFICATION_NEW_VIDEO_SUMMARY, summaryNotification)
    }

    // ========== UPDATE NOTIFICATIONS ==========

    /**
     * Show notification for new app update
     */
    suspend fun showUpdateNotification(
        context: Context,
        release: AppRelease,
    ) {
        if (!hasNotificationPermission(context)) return
        if (!PlayerPreferences(context).notifUpdatesEnabled.first()) return

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_UPDATE, true)
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.notification_update_available, release.version))
                .setContentText(context.getString(R.string.notification_tap_to_update))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()

        NotificationManagerCompat.from(context).notify(9999, notification)
    }

    /**
     * Cancel a specific notification
     */
    fun cancelNotification(
        context: Context,
        notificationId: Int,
    ) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * Show reminder notification (Bedtime, Take a break)
     */
    suspend fun showReminderNotification(
        context: Context,
        title: String,
        message: String,
    ) {
        if (!hasNotificationPermission(context)) return
        if (!PlayerPreferences(context).notifRemindersEnabled.first()) return

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0,
            )

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_REMINDER, builder.build())
            }
        } catch (e: SecurityException) {
            // Should be covered by hasNotificationPermission check, but safety first
            e.printStackTrace()
        }
    }

    suspend fun showUpcomingVideoLiveNotification(
        context: Context,
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
    ) {
        if (!hasNotificationPermission(context)) return
        if (!PlayerPreferences(context).notifRemindersEnabled.first()) return

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_video_id", videoId)
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                videoId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val liveText = context.getString(R.string.notification_channel_live, channelName, title)
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.notification_video_live))
                .setContentText(liveText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(liveText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        thumbnailUrl?.let { getBitmapFromUrl(context, it) }?.let(builder::setLargeIcon)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_REMINDER + videoId.hashCode(), builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    /**
     * Load bitmap from URL for notification large icon/picture.
     *
     * Uses the app's shared Coil ImageLoader so notification artwork reuses the memory/disk
     * cache the feed already populated instead of refetching through a second image stack.
     * Hardware bitmaps are disabled because notification bitmaps must be parcelable to
     * SystemUI, and INEXACT precision keeps the "never upscale" behaviour of the previous
     * centerInside/onlyScaleDown request.
     */
    suspend fun getBitmapFromUrl(
        context: Context,
        url: String,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (url.isEmpty()) return@withContext null
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(NOTIFICATION_BITMAP_MAX_PX)
                        .scale(Scale.FIT)
                        .precision(Precision.INEXACT)
                        .allowHardware(false)
                        .build()
                (SingletonImageLoader.get(context).execute(request) as? SuccessResult)
                    ?.image
                    ?.toBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}
