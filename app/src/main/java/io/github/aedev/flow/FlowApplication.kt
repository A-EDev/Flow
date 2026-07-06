package io.github.aedev.flow

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import io.github.aedev.flow.notification.SubscriptionCheckWorker
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import kotlinx.coroutines.flow.first
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
import okhttp3.OkHttpClient
import javax.inject.Inject
import java.security.Security
import org.conscrypt.Conscrypt
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.NewPipeExtractor
import io.github.aedev.flow.utils.AppLanguageManager
import io.github.aedev.flow.utils.potoken.NewPipePoTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import io.github.aedev.flow.innertube.models.YouTubeLocale
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

@HiltAndroidApp
class FlowApplication : Application(), ImageLoaderFactory {
    
    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(): ImageLoader = imageLoader
    
    companion object {
        private const val TAG = "FlowApplication"
        lateinit var appContext: Context
            private set
    }

    override fun attachBaseContext(base: Context) {
        val selectedLanguage = AppLanguageManager.loadSelectedLanguageTag(base)
        super.attachBaseContext(AppLanguageManager.wrapContext(base, selectedLanguage))
    }
    
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        val playerPreferences = PlayerPreferences(this)
        
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
            YoutubeStreamExtractor.setPoTokenProvider(NewPipePoTokenProvider)
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
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val savedIntervalMinutes = playerPreferences.subscriptionCheckIntervalMinutes.first()
            SubscriptionCheckWorker.schedulePeriodicCheck(
                this@FlowApplication,
                intervalMinutes = savedIntervalMinutes.toLong()
            )
        }
        
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
                io.github.aedev.flow.utils.potoken.WebPoTokenSession.prewarm()
            } catch (e: Exception) {
                Log.w(TAG, "WebPoTokenSession prewarm failed: ${e.message}")
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            combine(
                playerPreferences.appLanguage,
                playerPreferences.trendingRegion
            ) { lang, region ->
                val glCode = normalizeYouTubeCountry(region)
                val hlCode = normalizeYouTubeLanguage(lang)
                YouTubeLocale(gl = glCode, hl = hlCode)
            }.collectLatest { newLocale ->
                YouTube.locale = newLocale
                Log.d(TAG, "Dynamic YouTube Locale updated: gl=${newLocale.gl}, hl=${newLocale.hl}")
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var lastRegion: String? = null
            playerPreferences.trendingRegion.collectLatest { region ->
                if (lastRegion != null && lastRegion != region) {
                    Log.d(TAG, "Trending region changed from $lastRegion to $region. Invalidate visitor data.")
                    val prefs = getSharedPreferences("flow_prefs", MODE_PRIVATE)
                    prefs.edit().remove("visitor_data").apply()
                    YouTube.visitorData = null
                    
                    YouTube.visitorData().onSuccess { data ->
                        if (!data.isNullOrEmpty()) {
                            prefs.edit().putString("visitor_data", data).apply()
                            YouTube.visitorData = data
                            Log.d(TAG, "Fresh visitorData fetched for region: $region")
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to fetch fresh visitorData: ${e.message}")
                    }
                }
                lastRegion = region
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

    private fun normalizeYouTubeCountry(region: String): String {
        val normalized = region.trim().uppercase(Locale.US)
        return if (normalized.matches(Regex("[A-Z]{2}"))) {
            normalized
        } else {
            Locale.getDefault().country
                .trim()
                .uppercase(Locale.US)
                .takeIf { it.matches(Regex("[A-Z]{2}")) }
                ?: "US"
        }
    }

    private fun normalizeYouTubeLanguage(languageTag: String): String {
        val candidate = languageTag.trim()
            .takeUnless { it.isBlank() || it.equals(AppLanguageManager.SYSTEM_DEFAULT, ignoreCase = true) }
            ?: Locale.getDefault().toLanguageTag()
        val tag = Locale.forLanguageTag(candidate.replace('_', '-')).toLanguageTag()
        return tag.takeUnless { it.isBlank() || it.equals("und", ignoreCase = true) } ?: "en"
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Clean up performance dispatcher resources
        PerformanceDispatcher.shutdown()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        releaseVolatileMemory()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            releaseVolatileMemory()
        }
    }

    private fun releaseVolatileMemory() {
        if (::imageLoader.isInitialized) {
            imageLoader.memoryCache?.clear()
        }
        if (::okHttpClient.isInitialized) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                okHttpClient.connectionPool.evictAll()
            }
        }
    }
}
