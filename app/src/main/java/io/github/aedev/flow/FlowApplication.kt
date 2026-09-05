package io.github.aedev.flow

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import io.github.aedev.flow.data.local.CONTENT_LANGUAGE_FOLLOW_APP
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.repository.NewPipeDownloader
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.discord.DiscordPresenceRuntime
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeLocale
import io.github.aedev.flow.innertube.models.normalizeYouTubeHostLanguage
import io.github.aedev.flow.innertube.pages.NewPipeExtractor
import io.github.aedev.flow.network.AppProxyManager
import io.github.aedev.flow.notification.NotificationHelper
import io.github.aedev.flow.notification.SubscriptionCheckWorker
import io.github.aedev.flow.utils.AppLanguageManager
import io.github.aedev.flow.utils.FlowCrashHandler
import io.github.aedev.flow.utils.PerformanceDispatcher
import io.github.aedev.flow.utils.cipher.PipePipeNsigDecoder
import io.github.aedev.flow.utils.newPipeContentCountry
import io.github.aedev.flow.utils.newPipeLocalization
import io.github.aedev.flow.utils.normalizeYouTubeCountry
import io.github.aedev.flow.utils.potoken.NewPipePoTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import java.security.Security
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class FlowApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var channelReelIndex: io.github.aedev.flow.data.shorts.ChannelReelIndex

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    companion object {
        private const val TAG = "FlowApplication"
        private const val VISITOR_DATA_KEY = "visitor_data"
        private const val VISITOR_DATA_FETCHED_AT_KEY = "visitor_data_fetched_at"
        private const val VISITOR_DATA_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1_000L
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
        YouTube.cacheDirectory = cacheDir.resolve("innertube_http_cache")

        DiscordPresenceRuntime.initialize(this, okHttpClient)

        val playerPreferences = PlayerPreferences(this)

        // Injects modern TLS/SSL certificates so OkHttp and Ktor don't crash
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.N_MR1) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        // Install crash handler for real-time monitoring
        FlowCrashHandler.install(this)

        try {
            // Seeded from the device locale so the very first extraction is already localized; the
            // stored app-language/region preference takes over as soon as it loads below.
            val localization = newPipeLocalization(Locale.getDefault().toLanguageTag())
            NewPipe.init(
                NewPipeDownloader.getInstance(this),
                localization,
                newPipeContentCountry(Locale.getDefault().country),
            )
            YoutubeStreamExtractor.setPoTokenProvider(NewPipePoTokenProvider)
            Log.d(TAG, "NewPipe initialized with ${localization.localizationCode}")
        } catch (e: Exception) {
            // Log error but don't crash the app
            Log.e(TAG, "Failed to initialize NewPipe", e)
        }

        try {
            io.github.aedev.flow.utils.cipher.CipherDeobfuscator
                .initialize(this)
            Log.d(TAG, "CipherDeobfuscator initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CipherDeobfuscator", e)
        }

        PipePipeNsigDecoder.initialize(this)

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
                intervalMinutes = savedIntervalMinutes.toLong(),
            )

            // Schedule periodic update checks (every 12 hours) — github flavor only
            if (BuildConfig.UPDATER_ENABLED) {
                io.github.aedev.flow.notification.UpdateCheckWorker
                    .schedulePeriodicCheck(this@FlowApplication)
            }
        }

        Log.d(TAG, "Workers scheduled successfully")

        // Fetch and cache visitor data for the lifetime of the install.
        // The X-Goog-Visitor-Id header prevents YouTube from returning empty
        // search results on tablets and fresh Android 16 installs (Issue #223).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var nsigWarmed = false
            playerPreferences.proxyConfig.collectLatest { proxyConfig ->
                applyProxyConfig(proxyConfig)
                // Ordered after the first proxy application so the warm-up honours it. Resolving
                // the remote n-decoder player id is a round trip that the first video of a session
                // would otherwise pay on its path to first frame; it is persisted for 24h, so on
                // most launches this is only a disk read.
                if (!nsigWarmed) {
                    nsigWarmed = true
                    PipePipeNsigDecoder.warmUp()
                }
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("flow_prefs", MODE_PRIVATE)
                val cached = prefs.getString(VISITOR_DATA_KEY, null)
                val cachedAt = prefs.getLong(VISITOR_DATA_FETCHED_AT_KEY, 0L)
                val cacheIsFresh =
                    cachedAt > 0L &&
                        System.currentTimeMillis() - cachedAt < VISITOR_DATA_MAX_AGE_MS
                if (!cached.isNullOrEmpty() && cacheIsFresh) {
                    YouTube.visitorData = cached
                    Log.d(TAG, "visitorData restored from prefs")
                } else {
                    YouTube
                        .visitorData()
                        .onSuccess { data ->
                            if (!data.isNullOrEmpty()) {
                                prefs
                                    .edit()
                                    .putString(VISITOR_DATA_KEY, data)
                                    .putLong(VISITOR_DATA_FETCHED_AT_KEY, System.currentTimeMillis())
                                    .apply()
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
            try {
                io.github.aedev.flow.utils.potoken.WebPoTokenSession
                    .prewarm()
            } catch (e: Exception) {
                Log.w(TAG, "WebPoTokenSession prewarm failed: ${e.message}")
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            combine(
                playerPreferences.appLanguage,
                playerPreferences.contentLanguage,
                playerPreferences.trendingRegion,
            ) { appLanguage, contentLanguage, region ->
                val language = if (contentLanguage == CONTENT_LANGUAGE_FOLLOW_APP) appLanguage else contentLanguage
                YouTubeLocale(gl = normalizeYouTubeCountry(region), hl = normalizeYouTubeHostLanguage(language))
            }.collectLatest { newLocale ->
                YouTube.locale = newLocale
                NewPipe.setupLocalization(
                    newPipeLocalization(newLocale.hl),
                    ContentCountry(newLocale.gl),
                )
                Log.d(TAG, "Dynamic YouTube Locale updated: gl=${newLocale.gl}, hl=${newLocale.hl}")
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var lastRegion: String? = null
            playerPreferences.trendingRegion.collectLatest { region ->
                if (lastRegion != null && lastRegion != region) {
                    Log.d(TAG, "Trending region changed from $lastRegion to $region. Invalidate visitor data.")
                    val prefs = getSharedPreferences("flow_prefs", MODE_PRIVATE)
                    prefs
                        .edit()
                        .remove(VISITOR_DATA_KEY)
                        .remove(VISITOR_DATA_FETCHED_AT_KEY)
                        .apply()
                    YouTube.visitorData = null

                    YouTube
                        .visitorData()
                        .onSuccess { data ->
                            if (!data.isNullOrEmpty()) {
                                prefs
                                    .edit()
                                    .putString(VISITOR_DATA_KEY, data)
                                    .putLong(VISITOR_DATA_FETCHED_AT_KEY, System.currentTimeMillis())
                                    .apply()
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
                val youtubeRepository = YouTubeRepository.getInstance(playerPreferences, channelReelIndex)
                val repaired =
                    repository.repairVideoThumbnailSubscriptions { channelId ->
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
        DiscordPresenceRuntime.shutdown()
        super.onTerminate()
        // Clean up performance dispatcher resources
        PerformanceDispatcher.shutdown()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        FlowCrashHandler.recordPhase("memory", "FlowApplication.onLowMemory")
        releaseVolatileMemory()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        FlowCrashHandler.recordPhase("memory", "FlowApplication.onTrimMemory level=$level")
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
