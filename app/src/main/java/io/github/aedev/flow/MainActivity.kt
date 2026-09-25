package io.github.aedev.flow

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.aedev.flow.data.local.AppUiModePreferences
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.data.playlist.PlaylistImport
import io.github.aedev.flow.data.playlist.PlaylistTransfer
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.discord.DiscordPresenceRuntime
import io.github.aedev.flow.notification.NotificationHelper
import io.github.aedev.flow.platform.AppIconController
import io.github.aedev.flow.platform.AppUiMode
import io.github.aedev.flow.platform.AppUiRoot
import io.github.aedev.flow.platform.DeviceFormFactorDetector
import io.github.aedev.flow.player.BackgroundPlaybackPolicy
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.LifecyclePlaybackPreferences
import io.github.aedev.flow.player.MemoryPressurePolicy
import io.github.aedev.flow.player.PictureInPictureHelper
import io.github.aedev.flow.ui.FlowApp
import io.github.aedev.flow.ui.components.library.message
import io.github.aedev.flow.ui.components.shared.ProvideChannelGroupLabels
import io.github.aedev.flow.ui.components.shared.ProvideDateDisplaySettings
import io.github.aedev.flow.ui.components.shared.card.ProvideVideoCardState
import io.github.aedev.flow.ui.screens.crash.CrashReportScreen
import io.github.aedev.flow.ui.screens.update.UPDATE_ROUTE
import io.github.aedev.flow.ui.startup.FlowTheme
import io.github.aedev.flow.ui.startup.SplashController
import io.github.aedev.flow.ui.startup.ThemeSettings
import io.github.aedev.flow.ui.startup.splashTone
import io.github.aedev.flow.ui.startup.themeSettings
import io.github.aedev.flow.ui.theme.FlowTheme
import io.github.aedev.flow.ui.tv.FlowTvApp
import io.github.aedev.flow.ui.utils.ProvideWindowSizeClass
import io.github.aedev.flow.ui.youtubeChannelDeepLinkRoute
import io.github.aedev.flow.utils.AppLanguageManager
import io.github.aedev.flow.utils.FlowCrashHandler
import io.github.aedev.flow.utils.PLAYLIST_FILE_MIME_TYPE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PORTRAIT_REEL_ASPECT_RATIO = 9f / 16f

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val _deeplinkVideoId = mutableStateOf<String?>(null)
    val deeplinkVideoId: State<String?> = _deeplinkVideoId

    private val _isDeeplinkShort = mutableStateOf(false)
    val isDeeplinkShort: State<Boolean> = _isDeeplinkShort

    private val _openMusicPlayerRequest = mutableIntStateOf(0)
    val openMusicPlayerRequest: State<Int> = _openMusicPlayerRequest

    private val _pendingRoute = mutableStateOf<String?>(null)
    val pendingRoute: State<String?> = _pendingRoute

    @Inject
    lateinit var lifecyclePlaybackPreferences: LifecyclePlaybackPreferences

    @Inject
    lateinit var appIconController: AppIconController

    @Inject
    lateinit var playlistTransfer: dagger.Lazy<PlaylistTransfer>

    // A recreated activity gets its launch intent again; a playlist file in it was already imported.
    private var isRestoringState = false

    private val splashController = SplashController(this)

    private var pipDismissCheckJob: Job? = null
    private var pendingAutoPip = false
    private var cachedAppUiRoot = AppUiRoot.MOBILE

    private fun videoPlaybackStateName(state: Int?): String =
        when (state) {
            androidx.media3.common.Player.STATE_IDLE -> "IDLE"
            androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
            androidx.media3.common.Player.STATE_READY -> "READY"
            androidx.media3.common.Player.STATE_ENDED -> "ENDED"
            null -> "NO_PLAYER"
            else -> "UNKNOWN($state)"
        }

    private fun lifecyclePlaybackSnapshot(): String {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val playerManager =
            io.github.aedev.flow.player.EnhancedPlayerManager
                .getInstance()
        val playerState = playerManager.playerState.value
        val player = playerManager.getPlayer()
        return "interactive=${powerManager?.isInteractive} lifecycle=${lifecycle.currentState} " +
            "pip=$isInPictureInPictureMode pendingAutoPip=$pendingAutoPip " +
            "bgPref=${lifecyclePlaybackPreferences.settings.backgroundPlayEnabled} " +
            "shortsBgPref=${lifecyclePlaybackPreferences.settings.shortsBackgroundPlay} " +
            "explicitBg=${GlobalPlayerState.isExplicitBackgroundPlaybackActive.value} " +
            "video=${playerState.currentVideoId} exo=${videoPlaybackStateName(player?.playbackState)} " +
            "pwr=${player?.playWhenReady} playing=${player?.isPlaying} buffering=${playerState.isBuffering} " +
            "pos=${player?.currentPosition}/${player?.duration} idx=${player?.currentMediaItemIndex} count=${player?.mediaItemCount}"
    }

    private fun videoLifecycleLog(message: String) {
        Log.w("FlowVideoLifecycle", "$message | ${lifecyclePlaybackSnapshot()}")
    }

    override fun attachBaseContext(newBase: Context) {
        val selectedLanguage = AppLanguageManager.loadSelectedLanguageTag(newBase)
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase, selectedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        splashController.install(installSplashScreen())

        super.onCreate(savedInstanceState)
        DiscordPresenceRuntime.attachActivity(this)

        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
            navigationBarStyle =
                SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        // Player setup reads DataStore and opens the media cache index, so it runs off the main
        // thread and settles after the first frame instead of blocking onCreate.
        lifecycleScope.launch { GlobalPlayerState.initializeAsync(applicationContext) }

        // Snapshot lives in the process, not the activity: onUserLeaveHint/onStop read it
        // synchronously and must still see it after a recreation they run inside of (#817).
        lifecyclePlaybackPreferences.observeIn(lifecycleScope)

        // Initialize Neuro Engine (Recommendation System)
        lifecycleScope.launch(Dispatchers.IO) {
            FlowNeuroEngine.initialize(applicationContext)
        }

        val dataManager = LocalDataManager(applicationContext)

        lifecycleScope.launch {
            io.github.aedev.flow.widget.core.FlowWidgets
                .observeThemeChanges(applicationContext)
        }

        isRestoringState = savedInstanceState != null
        handleIntent(intent)
        isRestoringState = false

        // Read now, alongside the rest of startup, so the theme is usually known by the first composition.
        val storedTheme = MutableStateFlow<ThemeSettings?>(null)
        lifecycleScope.launch { dataManager.themeSettings().collect { storedTheme.value = it } }

        setContent {
            // Nothing is composed until the theme is known: the splash covers the wait, and the app
            // composes once in the right theme instead of twice.
            val theme = storedTheme.collectAsState().value ?: return@setContent

            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val uiPreferences = remember { AppUiModePreferences(applicationContext) }
            val appUiMode by uiPreferences.mode.collectAsState(initial = AppUiMode.AUTOMATIC)
            val deviceFormFactor =
                remember(configuration.uiMode, context) {
                    DeviceFormFactorDetector.detect(context)
                }
            val appUiRoot = appUiMode.resolve(deviceFormFactor)
            SideEffect { cachedAppUiRoot = appUiRoot }

            // Check for a crash that happened last session.
            // If found, show the CrashReportScreen instead of the normal UI.
            var pendingCrashLog by remember {
                mutableStateOf(FlowCrashHandler.getLastCrash(applicationContext))
            }

            if (pendingCrashLog != null) {
                SideEffect { splashController.contentReady = true }
                FlowTheme(theme) {
                    CrashReportScreen(
                        report = pendingCrashLog!!,
                        onContinue = {
                            FlowCrashHandler.clearLastCrash(applicationContext)
                            pendingCrashLog = null
                        },
                    )
                }
                return@setContent
            }

            // Initialize Flow Neuro Engine
            LaunchedEffect(Unit) {
                io.github.aedev.flow.data.recommendation.FlowNeuroEngine
                    .initialize(applicationContext)
            }

            FlowTheme(theme) {
                val splashTone = splashTone(MaterialTheme.colorScheme.background)
                LaunchedEffect(splashTone) { splashController.rememberTheme(splashTone, appIconController.activeSuffix()) }

                // Date preferences: five DataStore flows used to be opened per video card,
                // metadata line, info section, description sheet and info dialog.
                ProvideWindowSizeClass {
                    ProvideDateDisplaySettings {
                        // Card preferences and watch progress are collected once here. Cards used to
                        // collect them individually, so a feed of ten opened ten Room observers and
                        // fifty DataStore collectors.
                        ProvideVideoCardState {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .semantics { testTagsAsResourceId = true },
                            ) {
                                // 1. MAIN APP (Home/NavHost)
                                // This loads *behind* the splash screen immediately.
                                // By the time splash fades, this is ready.
                                val deeplinkVideoId by this@MainActivity.deeplinkVideoId
                                val isDeeplinkShort by this@MainActivity.isDeeplinkShort
                                val openMusicPlayerRequest by this@MainActivity.openMusicPlayerRequest
                                val pendingRoute by this@MainActivity.pendingRoute

                                if (appUiRoot == AppUiRoot.TV) {
                                    SideEffect { splashController.contentReady = true }
                                    FlowTvApp(
                                        deeplinkVideoId = deeplinkVideoId,
                                        isShort = isDeeplinkShort,
                                        onDeeplinkConsumed = { consumeDeeplink() },
                                    )
                                } else {
                                    ProvideChannelGroupLabels {
                                        FlowApp(
                                            currentTheme = theme.themeMode,
                                            themeVariant = theme.themeVariant,
                                            systemLightThemeMode = theme.systemLightThemeMode,
                                            systemDarkThemeMode = theme.systemDarkThemeMode,
                                            deeplinkVideoId = deeplinkVideoId,
                                            isShort = isDeeplinkShort,
                                            openMusicPlayerRequest = openMusicPlayerRequest,
                                            onDeeplinkConsumed = {
                                                consumeDeeplink()
                                            },
                                            pendingRoute = pendingRoute,
                                            onPendingRouteConsumed = {
                                                _pendingRoute.value = null
                                            },
                                            onStartDestinationKnown = { splashController.contentReady = true },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        DiscordPresenceRuntime.setAppForeground(true)
    }

    override fun onDestroy() {
        videoLifecycleLog("onDestroy")
        DiscordPresenceRuntime.detachActivity(this)
        val playerManager =
            io.github.aedev.flow.player.EnhancedPlayerManager
                .getInstance()
        val playerState = playerManager.playerState.value
        val hasActiveVideo =
            playerState.currentVideoId != null &&
                (playerState.playWhenReady || playerState.isPlaying || playerState.isBuffering)
        val shouldKeepBackgroundPlayback =
            BackgroundPlaybackPolicy.shouldKeepPlaybackInBackground(
                backgroundPlaybackPreferenceEnabled = lifecyclePlaybackPreferences.settings.backgroundPlayEnabled,
                explicitBackgroundPlaybackActive = GlobalPlayerState.isExplicitBackgroundPlaybackActive.value,
                hasActiveVideo = hasActiveVideo,
            )

        if (shouldKeepBackgroundPlayback) {
            handOffVideoPlaybackToBackground()
        } else if (!isChangingConfigurations) {
            GlobalPlayerState.release()
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun playlistFileIn(intent: Intent): Uri? {
        val type = intent.type ?: intent.data?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }?.let(contentResolver::getType)
        if (type != PLAYLIST_FILE_MIME_TYPE) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
    }

    private fun importPlaylistFile(file: Uri) {
        lifecycleScope.launch {
            val result = playlistTransfer.get().import(file, getString(R.string.imported_playlist_default_name))
            Toast.makeText(this@MainActivity, result.message(this@MainActivity), Toast.LENGTH_LONG).show()
            if (result is PlaylistImport.Imported) _pendingRoute.value = "playlist/${result.playlistId}"
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_UPDATE, false)) {
            intent.removeExtra(NotificationHelper.EXTRA_OPEN_UPDATE)
            _pendingRoute.value = UPDATE_ROUTE
            return
        }
        val playlistFile = playlistFileIn(intent)
        if (playlistFile != null) {
            if (!isRestoringState) importPlaylistFile(playlistFile)
            return
        }
        val data = intent.data
        val notificationVideoId = intent.getStringExtra("notification_video_id") ?: intent.getStringExtra("video_id")

        val widgetRoute =
            intent.getStringExtra(
                io.github.aedev.flow.widget.core.WidgetDeepLink.EXTRA_WIDGET_ROUTE,
            )
        if (widgetRoute != null) {
            intent.removeExtra(io.github.aedev.flow.widget.core.WidgetDeepLink.EXTRA_WIDGET_ROUTE)
            _pendingRoute.value = widgetRoute
            return
        }

        val linkedText =
            when {
                data != null && intent.action == Intent.ACTION_VIEW -> data.toString()
                intent.action == Intent.ACTION_SEND && intent.type == "text/plain" -> intent.getStringExtra(Intent.EXTRA_TEXT)
                else -> null
            }
        val channelRoute = linkedText?.let(::youtubeChannelDeepLinkRoute)
        if (channelRoute != null) {
            _pendingRoute.value = channelRoute
            return
        }

        if (intent.getBooleanExtra("open_music_player", false)) {
            _deeplinkVideoId.value = null
            _isDeeplinkShort.value = false
            _openMusicPlayerRequest.intValue += 1
            intent.removeExtra("notification_video_id")
            intent.removeExtra("video_id")
            intent.removeExtra("deeplink_video_id")
            return
        }

        if (intent.getBooleanExtra("open_video_player", false)) {
            intent.removeExtra("open_video_player")
            val currentVideoId = GlobalPlayerState.currentVideo.value?.id
            if (currentVideoId != null) {
                _isDeeplinkShort.value = false
                _deeplinkVideoId.value = currentVideoId
            }
            return
        }

        // Reset shorts flag
        _isDeeplinkShort.value = false

        val videoId =
            if (data != null && intent.action == Intent.ACTION_VIEW) {
                val urlString = data.toString()
                if (urlString.contains("shorts/")) {
                    _isDeeplinkShort.value = true
                }
                extractVideoId(urlString)
            } else if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (sharedText != null) {
                    if (sharedText.contains("shorts/")) {
                        _isDeeplinkShort.value = true
                    }
                    extractVideoId(sharedText)
                } else {
                    null
                }
            } else {
                notificationVideoId
            }
        // Check extra
        if (intent.getBooleanExtra("is_short", false) || intent.getBooleanExtra("is_shorts", false)) {
            _isDeeplinkShort.value = true
        }

        if (videoId != null) {
            _deeplinkVideoId.value = videoId
            intent.putExtra("deeplink_video_id", videoId)
        }
    }

    fun consumeDeeplink() {
        _deeplinkVideoId.value = null
        _isDeeplinkShort.value = false
    }

    private fun extractVideoId(url: String): String? {
        val patterns =
            listOf(
                Regex("v=([^&]+)"),
                Regex("shorts/([^/?]+)"),
                Regex("youtu.be/([^/?]+)"),
                Regex("embed/([^/?]+)"),
                Regex("v/([^/?]+)"),
            )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return url.substringAfterLast("/").substringBefore("?").ifEmpty { null }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        videoLifecycleLog("onPictureInPictureModeChanged pip=$isInPictureInPictureMode")
        GlobalPlayerState.setPipMode(isInPictureInPictureMode)
        pendingAutoPip = false

        clearWindowBrightnessOverride()

        pipDismissCheckJob?.cancel()
        if (!isInPictureInPictureMode) {
            pipDismissCheckJob =
                lifecycleScope.launch {
                    delay(350L)
                    val stillBackgrounded = !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    if (stillBackgrounded && !isInPictureInPictureMode) {
                        GlobalPlayerState.requestDismiss()
                        io.github.aedev.flow.player.EnhancedPlayerManager
                            .getInstance()
                            .stop()
                        io.github.aedev.flow.player.EnhancedPlayerManager
                            .getInstance()
                            .stopBackgroundService()
                    }
                }
        }
    }

    private fun releaseOrientationLock() {
        if (requestedOrientation != android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun clearWindowBrightnessOverride() {
        val layoutParams = window.attributes
        if (layoutParams.screenBrightness != android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            layoutParams.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = layoutParams
        }
    }

    override fun onResume() {
        super.onResume()
        FlowCrashHandler.recordPhase("activity", "onResume pip=$isInPictureInPictureMode")
        videoLifecycleLog("onResume")
        // GlobalPlayerState outlives this Activity, so an instance destroyed straight out of PiP
        // without an onPictureInPictureModeChanged(false) would leave the flag latched true for
        // the rest of the process. Re-reading the real value here is the only reset path.
        GlobalPlayerState.setPipMode(isInPictureInPictureMode)
        pendingAutoPip = false
        pipDismissCheckJob?.cancel()
        PictureInPictureHelper.dismissPopup(this)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            io.github.aedev.flow.player.PlayerHardwareController.fullscreenVideoActive.value
        ) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val direction =
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        AudioManager.ADJUST_RAISE
                    } else {
                        AudioManager.ADJUST_LOWER
                    }
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    direction,
                    if (io.github.aedev.flow.player.PlayerHardwareController.inAppVolumeOverlayEnabled.value) {
                        0
                    } else {
                        AudioManager.FLAG_SHOW_UI
                    },
                )
                if (io.github.aedev.flow.player.PlayerHardwareController.inAppVolumeOverlayEnabled.value) {
                    io.github.aedev.flow.player.PlayerHardwareController
                        .notifyVolumeKey()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            io.github.aedev.flow.player.PlayerHardwareController.fullscreenVideoActive.value
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onStop() {
        DiscordPresenceRuntime.setAppForeground(false)
        super.onStop()
        FlowCrashHandler.recordPhase(
            "activity",
            "onStop pip=$isInPictureInPictureMode " +
                "backgroundPlay=${lifecyclePlaybackPreferences.settings.backgroundPlayEnabled} " +
                "shortsBackground=${lifecyclePlaybackPreferences.settings.shortsBackgroundPlay}",
        )
        videoLifecycleLog("onStop")
        if (!isInPictureInPictureMode && !PictureInPictureHelper.isPopupActive) {
            if (cachedAppUiRoot == AppUiRoot.MOBILE) {
                releaseOrientationLock()
            }
            if (!lifecyclePlaybackPreferences.settings.shortsBackgroundPlay) {
                io.github.aedev.flow.player.shorts.ShortsPlayerPool
                    .getInstance()
                    .pauseAll()
            }

            if (pendingAutoPip) {
                lifecycleScope.launch {
                    delay(800L)
                    if (
                        pendingAutoPip &&
                        !isInPictureInPictureMode &&
                        !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    ) {
                        pendingAutoPip = false
                        handleBackgroundPlaybackOnStop()
                    }
                }
            } else {
                handleBackgroundPlaybackOnStop()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (cachedAppUiRoot == AppUiRoot.TV) return
        val explicitBackgroundPlaybackActive =
            GlobalPlayerState.isExplicitBackgroundPlaybackActive.value
        FlowCrashHandler.recordPhase(
            "activity",
            "onUserLeaveHint autoPip=${lifecyclePlaybackPreferences.settings.autoPipEnabled} " +
                "explicitBackground=$explicitBackgroundPlaybackActive",
        )
        videoLifecycleLog("onUserLeaveHint")
        // Only enter PiP mode if video is playing and has progressed
        // We use the EnhancedPlayerManager directly to get the immediate state
        val playerManager =
            io.github.aedev.flow.player.EnhancedPlayerManager
                .getInstance()
        val musicManager = io.github.aedev.flow.player.EnhancedMusicPlayerManager

        val isVideoPlaying =
            playerManager.playerState.value.isPlaying &&
                playerManager.playerState.value.currentVideoId != null &&
                playerManager.getCurrentPosition() > 500 // At least 0.5s in
        val isMusicPlaying = musicManager.playerState.value.isPlaying

        // Only enter PiP for video, not for music (which uses background service)
        val shouldEnterAutoPip =
            BackgroundPlaybackPolicy.shouldEnterAutoPip(
                autoPipEnabled = lifecyclePlaybackPreferences.settings.autoPipEnabled,
                isVideoPlaying = isVideoPlaying,
                explicitBackgroundPlaybackActive = explicitBackgroundPlaybackActive,
            )
        if (shouldEnterAutoPip && !isMusicPlaying) {
            enterPlayerPictureInPictureMode(
                aspectRatio = PictureInPictureHelper.currentVideoAspectRatio,
                isPlaying = true,
            )
            return
        }

        if (!lifecyclePlaybackPreferences.settings.shortsPipEnabled || isMusicPlaying) return
        val shortsPool =
            io.github.aedev.flow.player.shorts.ShortsPlayerPool
                .getInstance()
        if (!shortsPool.isPlaying()) return
        enterPlayerPictureInPictureMode(
            aspectRatio = shortsPool.activeVideoAspectRatio() ?: PORTRAIT_REEL_ASPECT_RATIO,
            isPlaying = true,
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        FlowCrashHandler.recordPhase("memory", "MainActivity.onTrimMemory level=$level")
        if (MemoryPressurePolicy.shouldReleaseVideoPlayback(level)) {
            io.github.aedev.flow.player.EnhancedPlayerManager
                .getInstance()
                .handleCriticalMemoryPressure()
        }
    }

    fun enterPlayerPictureInPictureMode(
        aspectRatio: Float = PictureInPictureHelper.currentVideoAspectRatio,
        isPlaying: Boolean = true,
        openSettingsOnDenied: Boolean = false,
    ): Boolean {
        if (cachedAppUiRoot == AppUiRoot.TV) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!PictureInPictureHelper.isPipAllowed(this)) {
            if (openSettingsOnDenied) {
                PictureInPictureHelper.openPipSettings(this)
            }
            return false
        }

        pendingAutoPip = true
        val entered =
            PictureInPictureHelper.enterPipMode(
                activity = this,
                aspectRatio = aspectRatio,
                isPlaying = isPlaying,
                autoEnterEnabled = false,
            )
        if (!entered) {
            pendingAutoPip = false
        }
        return entered
    }

    private fun handOffVideoPlaybackToBackground() {
        FlowCrashHandler.recordPhase("background-handoff", "handOffVideoPlaybackToBackground")
        videoLifecycleLog("handOffVideoPlaybackToBackground")
        val playerManager =
            io.github.aedev.flow.player.EnhancedPlayerManager
                .getInstance()
        val playerState = playerManager.playerState.value
        if (
            playerState.currentVideoId != null &&
            (playerState.playWhenReady || playerState.isPlaying || playerState.isBuffering)
        ) {
            val video = GlobalPlayerState.currentVideo.value
            playerManager.startBackgroundService(
                videoId = video?.id ?: playerState.currentVideoId,
                title = video?.title?.ifEmpty { "Playing..." } ?: "Playing...",
                channel = video?.channelName ?: "",
                thumbnail = video?.thumbnailUrl ?: "",
            )
            playerManager.continueVideoPlaybackInBackground()
        }
    }

    private fun handleBackgroundPlaybackOnStop() {
        FlowCrashHandler.recordPhase("background-handoff", "handleBackgroundPlaybackOnStop")
        videoLifecycleLog("handleBackgroundPlaybackOnStop")
        val playerManager =
            io.github.aedev.flow.player.EnhancedPlayerManager
                .getInstance()
        val playerState = playerManager.playerState.value
        val hasActiveVideo =
            playerState.currentVideoId != null &&
                (playerState.playWhenReady || playerState.isPlaying || playerState.isBuffering)

        if (!hasActiveVideo) return

        val shouldKeepBackgroundPlayback =
            BackgroundPlaybackPolicy.shouldKeepPlaybackInBackground(
                backgroundPlaybackPreferenceEnabled = lifecyclePlaybackPreferences.settings.backgroundPlayEnabled,
                explicitBackgroundPlaybackActive = GlobalPlayerState.isExplicitBackgroundPlaybackActive.value,
                hasActiveVideo = hasActiveVideo,
            )

        if (shouldKeepBackgroundPlayback) {
            videoLifecycleLog("handleBackgroundPlaybackOnStop handoff")
            handOffVideoPlaybackToBackground()
        } else {
            videoLifecycleLog("handleBackgroundPlaybackOnStop pause")
            playerManager.pause()
            playerManager.stopBackgroundService()
        }
    }

    companion object {
        const val EXTRA_BENCHMARK_BYPASS_ONBOARDING = "io.github.aedev.flow.extra.BENCHMARK_BYPASS_ONBOARDING"
    }
}
