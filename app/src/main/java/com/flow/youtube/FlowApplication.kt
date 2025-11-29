package com.flow.youtube

import android.app.Application
import android.util.Log
import com.flow.youtube.data.repository.NewPipeDownloader
import com.flow.youtube.notification.NotificationHelper
import com.flow.youtube.notification.SubscriptionCheckWorker
import org.schabi.newpipe.extractor.NewPipe

class FlowApplication : Application() {
    
    companion object {
        private const val TAG = "FlowApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize NewPipeExtractor with error handling
            NewPipe.init(NewPipeDownloader.getInstance())
            Log.d(TAG, "NewPipe initialized successfully")
        } catch (e: Exception) {
            // Log error but don't crash the app
            Log.e(TAG, "Failed to initialize NewPipe", e)
        }
        
        // Initialize notification channels
        NotificationHelper.createNotificationChannels(this)
        Log.d(TAG, "Notification channels created")
        
        // Schedule periodic subscription checks for new videos
        // This will check subscribed channels every 30 minutes when conditions are met
        SubscriptionCheckWorker.schedulePeriodicCheck(this, intervalMinutes = 30)
        Log.d(TAG, "Subscription check worker scheduled")
    }
}

