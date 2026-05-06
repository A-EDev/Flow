package io.github.aedev.flow

import android.app.Application
import android.content.Context
import android.util.Log
import io.github.aedev.flow.notification.SubscriptionCheckWorker
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import io.github.aedev.flow.data.repository.NewPipeDownloader
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.notification.NotificationHelper
import io.github.aedev.flow.network.AppProxyManager
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
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.NewPipeExtractor
import io.github.aedev.flow.utils.AppLanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltAndroidApp
class FlowApplication : Application(), ImageLoaderFactory {
    
    @Inject
    lateinit var imageLoader: ImageLoader

    override fun newImageLoader(): ImageLoader = imageLoader
    
    companion object {
        private const val TAG = "FlowApplication"
    }

    override fun attachBaseContext(base: Context) {
        val selectedLanguage = AppLanguageManager.loadSelectedLanguageTag(base)
        super.attachBaseContext(AppLanguageManager.wrapContext(base, selectedLanguage))
    }
    
    override fun onCreate() {
        super.onCreate()

        val playerPreferences = PlayerPreferences(this)
        val selectedLanguage = runBlocking { playerPreferences.appLanguage.first() }
        AppLanguageManager.wrapContext(this, selectedLanguage)
        runBlocking {
            applyProxyConfig(playerPreferences.getProxyConfig())
        }
        
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

        try {
            io.github.aedev.flow.utils.cipher.CipherDeobfuscator.initialize(this)
            Log.d(TAG, "CipherDeobfuscator initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CipherDeobfuscator", e)
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
        val savedIntervalMinutes = runBlocking { playerPreferences.subscriptionCheckIntervalMinutes.first() }
        SubscriptionCheckWorker.schedulePeriodicCheck(this, intervalMinutes = savedIntervalMinutes.toLong())
        
        // Schedule periodic update checks (every 12 hours) — github flavor only
        if (BuildConfig.UPDATER_ENABLED) {
            io.github.aedev.flow.notification.UpdateCheckWorker.schedulePeriodicCheck(this)
        }
        
        Log.d(TAG, "Workers scheduled successfully")

        // Fetch and cache visitor data for the lifetime of the install.
        // The X-Goog-Visitor-Id header prevents YouTube from returning empty
        // search results on tablets and fresh Android 16 installs (Issue #223).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            playerPreferences.proxyConfig.collectLatest { proxyConfig ->
                applyProxyConfig(proxyConfig)
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("flow_prefs", MODE_PRIVATE)
                val cached = prefs.getString("visitor_data", null)
                if (!cached.isNullOrEmpty()) {
                    YouTube.visitorData = cached
                    Log.d(TAG, "visitorData restored from prefs")
                } else {
                    YouTube.visitorData().onSuccess { data ->
                        if (!data.isNullOrEmpty()) {
                            prefs.edit().putString("visitor_data", data).apply()
                            YouTube.visitorData = data
                            Log.d(TAG, "visitorData fetched and cached")
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "visitorData fetch failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "visitorData init error: ${e.message}")
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = SubscriptionRepository.getInstance(this@FlowApplication)
                val youtubeRepository = YouTubeRepository.getInstance(playerPreferences)
                val repaired = repository.repairVideoThumbnailSubscriptions { channelId ->
                    withTimeoutOrNull(6_000L) {
                        youtubeRepository.fetchChannelAvatarById(channelId)
                    }.orEmpty()
                }
                if (repaired > 0) {
                    Log.i(TAG, "Repaired $repaired subscription thumbnails")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Subscription thumbnail repair failed: ${e.message}")
            }
        }
    }

    private fun applyProxyConfig(config: io.github.aedev.flow.network.AppProxyConfig) {
        AppProxyManager.update(config)
        YouTube.proxy = AppProxyManager.currentProxy()
        YouTube.proxyAuth = AppProxyManager.currentHttpProxyAuthorizationHeader()
        NewPipeExtractor.invalidateClient()
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Clean up performance dispatcher resources
        PerformanceDispatcher.shutdown()
    }
}
