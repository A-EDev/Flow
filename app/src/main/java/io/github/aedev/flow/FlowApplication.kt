package io.github.aedev.flow

import android.app.Application
import android.util.Log
import io.github.aedev.flow.notification.SubscriptionCheckWorker
import io.github.aedev.flow.data.repository.NewPipeDownloader
import io.github.aedev.flow.notification.NotificationHelper
import io.github.aedev.flow.utils.FlowCrashHandler
import io.github.aedev.flow.utils.PerformanceDispatcher
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory
import javax.inject.Inject
import java.security.Security
import org.conscrypt.Conscrypt

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
        
        // Injects modern TLS/SSL certificates so OkHttp and Ktor don't crash
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.N_MR1) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        // Install crash handler for real-time monitoring
        FlowCrashHandler.install(this)
        
        try {
            val country = ContentCountry("US")
            val localization = Localization("en", "US")
            NewPipe.init(NewPipeDownloader.getInstance(this), localization, country)
            Log.d(TAG, "NewPipe initialized successfully with en-US settings")
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
        SubscriptionCheckWorker.schedulePeriodicCheck(this, intervalMinutes = 360)
        
        // Schedule periodic update checks (every 12 hours) — github flavor only
        if (BuildConfig.UPDATER_ENABLED) {
            io.github.aedev.flow.notification.UpdateCheckWorker.schedulePeriodicCheck(this)
        }
        
        Log.d(TAG, "Workers scheduled successfully")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Clean up performance dispatcher resources
        PerformanceDispatcher.shutdown()
    }
}
