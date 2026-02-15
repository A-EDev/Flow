package com.flow.youtube

import android.app.Application
import android.util.Log
import com.flow.youtube.notification.SubscriptionCheckWorker
import com.flow.youtube.data.repository.NewPipeDownloader
import com.flow.youtube.notification.NotificationHelper
import com.flow.youtube.utils.FlowCrashHandler
import com.flow.youtube.utils.PerformanceDispatcher
import org.schabi.newpipe.extractor.NewPipe
// Import Localization and ContentCountry
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import kotlinx.coroutines.launch

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
        
        // Install crash handler for real-time monitoring
        FlowCrashHandler.install(this)
        
        try {
            // Initialize NewPipeExtractor with proper Localization and Country
            val country = ContentCountry("US")
            val localization = Localization.fromLocale(Locale.getDefault())
            NewPipe.init(NewPipeDownloader.getInstance(this), localization, country)
            Log.d(TAG, "NewPipe initialized successfully with US/Local settings")
        } catch (e: Exception) {
            // Log error but don't crash the app
            Log.e(TAG, "Failed to initialize NewPipe", e)
        }
        
        // Initialize notification channels
        NotificationHelper.createNotificationChannels(this)
        Log.d(TAG, "Notification channels created")
        
        // PERFORMANCE: Warm up connection pools in background
        // This pre-establishes connections to reduce first-load latency
        warmUpNetworkConnections()
        
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
        
        // Schedule periodic update checks (every 12 hours)
        com.flow.youtube.notification.UpdateCheckWorker.schedulePeriodicCheck(this)
        
        Log.d(TAG, "Workers scheduled successfully")
    }
    
    /**
     * PERFORMANCE: Pre-warm network connections
     * Establishes HTTP connections early so they're ready when the user needs content
     */
    private fun warmUpNetworkConnections() {
        PerformanceDispatcher.backgroundScope.launch {
            try {
                // Simple HEAD request to establish connection pools
                // This warms up DNS resolution, TLS handshake, and connection pooling
                okhttp3.OkHttpClient.Builder()
                    .build()
                    .newCall(
                        okhttp3.Request.Builder()
                            .url("https://www.youtube.com")
                            .head()
                            .build()
                    ).execute().close()
                    
                Log.d(TAG, "Network connections warmed up successfully")
            } catch (e: Exception) {
                // Non-critical, just log
                Log.d(TAG, "Network warmup skipped: ${e.message}")
            }
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Clean up performance dispatcher resources
        PerformanceDispatcher.shutdown()
    }
}
