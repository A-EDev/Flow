package com.flow.youtube.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.flow.youtube.MainActivity
import com.flow.youtube.R
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
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
    const val CHANNEL_UPDATES = "updates_channel"
    
    // Notification IDs
    const val NOTIFICATION_DOWNLOAD_PROGRESS = 1001
    const val NOTIFICATION_DOWNLOAD_COMPLETE = 1002
    const val NOTIFICATION_DOWNLOAD_FAILED = 1003
    const val NOTIFICATION_NEW_VIDEO = 2000 // Base ID, will be offset by channel
    const val NOTIFICATION_PLAYBACK = 3001
    const val NOTIFICATION_MUSIC_PLAYBACK = 3002
    const val NOTIFICATION_GENERAL = 4000
    
    private var channelsCreated = false
    
    /**
     * Initialize all notification channels
     * Should be called once when the app starts (e.g., in Application.onCreate())
     */
    fun createNotificationChannels(context: Context) {
        if (channelsCreated) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Downloads channel - High importance for active downloads
            val downloadsChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows download progress and completion"
                setShowBadge(true)
                enableLights(true)
                enableVibration(false)
            }
            
            // Subscriptions channel - Default importance for new videos
            val subscriptionsChannel = NotificationChannel(
                CHANNEL_SUBSCRIPTIONS,
                "New Videos",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when subscribed channels upload new videos"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            }
            
            // Video playback channel - Low importance for background playback
            val playbackChannel = NotificationChannel(
                CHANNEL_PLAYBACK,
                "Video Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Video playback controls"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            // Music playback channel - Low importance for background music
            val musicPlaybackChannel = NotificationChannel(
                CHANNEL_MUSIC_PLAYBACK,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            // General notifications channel
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
                setShowBadge(true)
            }
            
            // Updates channel
            val updatesChannel = NotificationChannel(
                CHANNEL_UPDATES,
                "App Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "App update notifications"
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannels(
                listOf(
                    downloadsChannel,
                    subscriptionsChannel,
                    playbackChannel,
                    musicPlaybackChannel,
                    generalChannel,
                    updatesChannel
                )
            )
            
            channelsCreated = true
        }
    }
    
    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    // ========== DOWNLOAD NOTIFICATIONS ==========
    
    /**
     * Show download progress notification
     */
    fun showDownloadProgress(
        context: Context,
        videoTitle: String,
        progress: Int,
        downloadSpeed: String? = null,
        largeIcon: Bitmap? = null,
        downloadId: Long = -1,
        notificationId: Int = NOTIFICATION_DOWNLOAD_PROGRESS
    ) {
        if (!hasNotificationPermission(context)) return
        
        val cancelIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CANCEL_DOWNLOAD
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val contentText = if (downloadSpeed != null) {
            "$progress% • $downloadSpeed"
        } else {
            "$progress%"
        }
        
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("Downloading: $videoTitle")
            .setContentText(contentText)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Cancel",
                cancelPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }
        
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
    
    /**
     * Show download complete notification
     */
    suspend fun showDownloadComplete(
        context: Context,
        videoTitle: String,
        filePath: String? = null,
        thumbnailUrl: String? = null,
        notificationId: Int = NOTIFICATION_DOWNLOAD_COMPLETE
    ) {
        if (!hasNotificationPermission(context)) return
        
        // Intent to open the downloaded file or app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_downloads", true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("Download complete")
            .setContentText(videoTitle)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        
        // Cancel progress notification
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_DOWNLOAD_PROGRESS)

        // Load thumbnail if provided
        if (!thumbnailUrl.isNullOrEmpty()) {
            val bitmap = getBitmapFromUrl(thumbnailUrl)
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }
        }
        
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
    
    /**
     * Show download failed notification
     */
    fun showDownloadFailed(
        context: Context,
        videoTitle: String,
        errorMessage: String? = null,
        notificationId: Int = NOTIFICATION_DOWNLOAD_FAILED
    ) {
        if (!hasNotificationPermission(context)) return
        
        // Intent to retry download
        val retryIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_RETRY_DOWNLOAD
            putExtra(NotificationActionReceiver.EXTRA_VIDEO_TITLE, videoTitle)
        }
        val retryPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("Download failed")
            .setContentText(videoTitle)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$videoTitle\n${errorMessage ?: "An error occurred"}"))
            .addAction(
                android.R.drawable.ic_menu_rotate,
                "Retry",
                retryPendingIntent
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        
        // Cancel progress notification
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_DOWNLOAD_PROGRESS)
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
    
    // ========== SUBSCRIPTION NOTIFICATIONS ==========
    
    /**
     * Show notification for new video from subscribed channel
     */
    suspend fun showNewVideoNotification(
        context: Context,
        channelName: String,
        videoTitle: String,
        videoId: String,
        thumbnailUrl: String? = null,
        channelId: String
    ) {
        if (!hasNotificationPermission(context)) return
        
        // Generate unique notification ID based on video ID
        val notificationId = NOTIFICATION_NEW_VIDEO + videoId.hashCode().and(0xFFFF)
        
        // Intent to open the video
        val watchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_video_id", videoId)
            putExtra("video_id", videoId)
            putExtra("video_title", videoTitle)
        }
        val watchPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            watchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_SUBSCRIPTIONS)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(channelName)
            .setContentText(videoTitle)
            .setContentIntent(watchPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroup("new_videos")
        
        // Try to load thumbnail
        thumbnailUrl?.let { url ->
            val bitmap = getBitmapFromUrl(url)
            bitmap?.let {
                builder.setLargeIcon(it)
                builder.setStyle(NotificationCompat.BigPictureStyle()
                    .bigPicture(it)
                    .bigLargeIcon(null as Bitmap?))
            }
        }
        
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
    
    /**
     * Show grouped notification summary for multiple new videos
     */
    fun showNewVideosSummary(
        context: Context,
        videoCount: Int
    ) {
        if (!hasNotificationPermission(context)) return
        
        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_SUBSCRIPTIONS)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("New videos")
            .setContentText("$videoCount new videos from your subscriptions")
            .setGroup("new_videos")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        NotificationManagerCompat.from(context).notify(NOTIFICATION_NEW_VIDEO, summaryNotification)
    }
    
    // ========== GENERAL NOTIFICATIONS ==========
    
    /**
     * Show a simple notification
     */
    fun showSimpleNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = NOTIFICATION_GENERAL
    ) {
        if (!hasNotificationPermission(context)) return
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
    
    /**
     * Show watch later reminder notification
     */
    fun showWatchLaterReminder(
        context: Context,
        videoTitle: String,
        videoId: String,
        thumbnailUrl: String? = null
    ) {
        if (!hasNotificationPermission(context)) return
        
        val notificationId = NOTIFICATION_GENERAL + videoId.hashCode().and(0xFFF)
        
        val watchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("video_id", videoId)
        }
        val watchPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            watchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("Watch Later Reminder")
            .setContentText(videoTitle)
            .setContentIntent(watchPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
    
    // ========== UTILITY FUNCTIONS ==========
    
    /**
     * Cancel a specific notification
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
    
    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
    
    /**
     * Load bitmap from URL for notification large icon/picture
     * Uses Picasso on IO thread
     */
    suspend fun getBitmapFromUrl(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (url.isEmpty()) return@withContext null
            // Load synchronously on the IO thread
            Picasso.get().load(url).get()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
