package com.flow.youtube

import android.app.Application
import android.util.Log
import com.flow.youtube.notification.SubscriptionCheckWorker
import com.flow.youtube.data.repository.NewPipeDownloader
import com.flow.youtube.notification.NotificationHelper
import org.schabi.newpipe.extractor.NewPipe

import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory
import javax.inject.Inject

@HiltAndroidApp
class FlowApplication : Application(), ImageLoaderFactory {
    
    @Inject
    lateinit var imageLoader: ImageLoader

    override fun newImageLoader(): ImageLoader = imageLoader
    
    companion object {
        private const val TAG = "FlowApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize NewPipeExtractor with error handling
            NewPipe.init(NewPipeDownloader.getInstance(this))
            Log.d(TAG, "NewPipe initialized successfully")
        } catch (e: Exception) {
            // Log error but don't crash the app
            Log.e(TAG, "Failed to initialize NewPipe", e)
        }
        
        // Initialize notification channels
        NotificationHelper.createNotificationChannels(this)
        Log.d(TAG, "Notification channels created")
        
        /*
        try {
            // Initialize YoutubeDL
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this)
            Log.d(TAG, "YoutubeDL initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YoutubeDL", e)
        }
        */
        
        // Schedule periodic subscription checks for new videos
        // This will check subscribed channels every 30 minutes
        SubscriptionCheckWorker.schedulePeriodicCheck(this, intervalMinutes = 30)
        Log.d(TAG, "Subscription check worker scheduled")
    }
}
