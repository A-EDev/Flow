package com.flow.youtube.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for handling notification action clicks
 */
class NotificationActionReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "com.flow.youtube.action.CANCEL_DOWNLOAD"
        const val ACTION_RETRY_DOWNLOAD = "com.flow.youtube.action.RETRY_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.flow.youtube.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.flow.youtube.action.RESUME_DOWNLOAD"
        const val ACTION_DISMISS_NOTIFICATION = "com.flow.youtube.action.DISMISS_NOTIFICATION"
        
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NotificationAction", "Received action: ${intent.action}")
        
        when (intent.action) {
            ACTION_CANCEL_DOWNLOAD -> handleCancelDownload(context, intent)
            ACTION_RETRY_DOWNLOAD -> handleRetryDownload(context, intent)
            ACTION_PAUSE_DOWNLOAD -> handlePauseDownload(context, intent)
            ACTION_RESUME_DOWNLOAD -> handleResumeDownload(context, intent)
            ACTION_DISMISS_NOTIFICATION -> handleDismissNotification(context, intent)
        }
    }
    
    private fun handleCancelDownload(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
        
        // Cancel the notification
        if (notificationId != -1) {
            NotificationHelper.cancelNotification(context, notificationId)
        }
        
        // Cancel the actual download task in system DownloadManager
        if (downloadId != -1L) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            downloadManager.remove(downloadId)
            Log.d("NotificationAction", "Download cancelled in system DownloadManager: $downloadId")
        }
    }
    
    private fun handleRetryDownload(context: Context, intent: Intent) {
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE)
        
        // TODO: Retry the download
        // This would integrate with your download manager
        Log.d("NotificationAction", "Retrying download for: $videoTitle (ID: $videoId)")
    }
    
    private fun handlePauseDownload(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
        
        // TODO: Pause the download
        Log.d("NotificationAction", "Pausing download: $downloadId")
    }
    
    private fun handleResumeDownload(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
        
        // TODO: Resume the download
        Log.d("NotificationAction", "Resuming download: $downloadId")
    }
    
    private fun handleDismissNotification(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        
        if (notificationId != -1) {
            NotificationHelper.cancelNotification(context, notificationId)
        }
    }
}
