package io.github.aedev.flow

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.app.AlertDialog
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.player.BackgroundPlaybackPolicy
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.ui.FlowApp
import io.github.aedev.flow.ui.theme.FlowTheme
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.CustomThemePalettes
import io.github.aedev.flow.ui.theme.ThemeVariant
import io.github.aedev.flow.updater.ApkUpdateHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import com.google.gson.JsonParser
import io.github.aedev.flow.ui.screens.CrashReporterScreen
import io.github.aedev.flow.utils.FlowCrashHandler
import io.github.aedev.flow.utils.UpdateManager
import io.github.aedev.flow.utils.UpdateInfo
import io.github.aedev.flow.network.AppProxyManager
import io.github.aedev.flow.player.PictureInPictureHelper
import io.github.aedev.flow.ui.components.UpdateDialog
import io.github.aedev.flow.BuildConfig
import androidx.activity.SystemBarStyle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.aedev.flow.utils.AppLanguageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val _deeplinkVideoId = mutableStateOf<String?>(null)
    val deeplinkVideoId: State<String?> = _deeplinkVideoId
    
    private val _isDeeplinkShort = mutableStateOf(false)
    val isDeeplinkShort: State<Boolean> = _isDeeplinkShort

    private val _pendingUpdateInfo = mutableStateOf<UpdateInfo?>(null)
    val pendingUpdateInfo: State<UpdateInfo?> = _pendingUpdateInfo

    private val _openMusicPlayerRequest = mutableIntStateOf(0)
    val openMusicPlayerRequest: State<Int> = _openMusicPlayerRequest

    // Cached auto-PiP preference
    private var cachedAutoPipEnabled = false

    // Cached background-play preference
    private var cachedBackgroundPlayEnabled = false

    // Cached shorts background-play preference (default OFF — pause on background)
    private var cachedShortsBackgroundPlay = false

    private var pipDismissCheckJob: Job? = null
    private var pendingAutoPip = false

    private fun videoPlaybackStateName(state: Int?): String = when (state) {
        androidx.media3.common.Player.STATE_IDLE -> "IDLE"
        androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
        androidx.media3.common.Player.STATE_READY -> "READY"
        androidx.media3.common.Player.STATE_ENDED -> "ENDED"
        null -> "NO_PLAYER"
        else -> "UNKNOWN($state)"
    }

    private fun lifecyclePlaybackSnapshot(): String {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val playerManager = io.github.aedev.flow.player.EnhancedPlayerManager.getInstance()
        val playerState = playerManager.playerState.value
        val player = playerManager.getPlayer()
        return "interactive=${powerManager?.isInteractive} lifecycle=${lifecycle.currentState} " +
            "pip=$isInPictureInPictureMode pendingAutoPip=$pendingAutoPip " +
            "bgPref=$cachedBackgroundPlayEnabled shortsBgPref=$cachedShortsBackgroundPlay " +
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
        // the OS-level splash screen (camouflaged to match Compose splash background)
        installSplashScreen()

        super.onCreate(savedInstanceState)
        
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        
        // Initialize global player state
        GlobalPlayerState.initialize(applicationContext)

        // Keep auto-PiP preference cached so onUserLeaveHint can read it synchronously
        lifecycleScope.launch {
            io.github.aedev.flow.data.local.PlayerPreferences(applicationContext)
                .autoPipEnabled
                .collect { enabled -> cachedAutoPipEnabled = enabled }
        }

        // Keep background-play preference cached so lifecycle callbacks can read it synchronously
        lifecycleScope.launch {
            io.github.aedev.flow.data.local.PlayerPreferences(applicationContext)
                .backgroundPlayEnabled
                .collect { enabled -> cachedBackgroundPlayEnabled = enabled }
        }

        // Keep shorts background-play preference cached so onStop can read it synchronously
        lifecycleScope.launch {
            io.github.aedev.flow.data.local.PlayerPreferences(applicationContext)
                .shortsBackgroundPlay
                .collect { enabled -> cachedShortsBackgroundPlay = enabled }
        }
        
        // Initialize Neuro Engine (Recommendation System)
        lifecycleScope.launch(Dispatchers.IO) {
            FlowNeuroEngine.initialize(applicationContext)
        }

        val dataManager = LocalDataManager(applicationContext)

        handleIntent(intent)

        
        // Check for updates (only in release builds, only in github flavor)
        if (!BuildConfig.DEBUG && BuildConfig.UPDATER_ENABLED) {
            checkForUpdates(dataManager)
        }

        setContent {
            val scope = rememberCoroutineScope()
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            var themeVariant by remember { mutableStateOf(ThemeVariant.DARK) }
            var customThemePalettes by remember { mutableStateOf(CustomThemePalettes()) }
            var systemLightThemeMode by remember { mutableStateOf(ThemeMode.DARK) }
            var systemDarkThemeMode by remember { mutableStateOf(ThemeMode.DARK) }
            var systemDarkThemeVariant by remember { mutableStateOf(ThemeVariant.DARK) }
            // State to control splash visibility
            var showSplash by remember { mutableStateOf(true) }

            val context = LocalContext.current

            // Check for a crash that happened last session.
            // If found, show the CrashReporterScreen instead of the normal UI.
            var pendingCrashLog by remember {
                mutableStateOf(FlowCrashHandler.getLastCrash(applicationContext))
            }

            if (pendingCrashLog != null) {
                FlowTheme(
                    themeMode = themeMode,
                    themeVariant = themeVariant,
                    customThemePalettes = customThemePalettes,
                    systemLightThemeMode = systemLightThemeMode,
                    systemDarkThemeMode = systemDarkThemeMode,
                    systemDarkThemeVariant = systemDarkThemeVariant
                ) {
                    CrashReporterScreen(
                        crashLog = pendingCrashLog!!,
                        onClearAndRestart = {
                            FlowCrashHandler.clearLastCrash(applicationContext)
                            pendingCrashLog = null
                        }
                    )
                }
                return@setContent
            }

            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            
            // Check for updates ONCE on launch — skip debug/foss builds, enforce 24h cooldown
            LaunchedEffect(Unit) {
                if (BuildConfig.DEBUG || !BuildConfig.UPDATER_ENABLED) return@LaunchedEffect
                val lastCheck = dataManager.lastUpdateCheck.first()
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastCheck < 24 * 60 * 60 * 1000L) return@LaunchedEffect

                val info = UpdateManager.checkForUpdate(BuildConfig.VERSION_NAME)
                dataManager.setLastUpdateCheck(currentTime)
                if (info != null && info.isNewer) {
                    updateInfo = info
                }
            }

            // Load theme preference and keep it reactive
            LaunchedEffect(Unit) {
                dataManager.themeMode.collect { mode ->
                    themeMode = mode
                }
            }

            LaunchedEffect(Unit) {
                dataManager.themeVariant.collect { variant ->
                    themeVariant = variant
                }
            }

            LaunchedEffect(Unit) {
                dataManager.customThemePalettes.collect { palettes ->
                    customThemePalettes = palettes
                }
            }

            LaunchedEffect(Unit) {
                dataManager.systemLightThemeMode.collect { mode ->
                    systemLightThemeMode = mode
                }
            }

            LaunchedEffect(Unit) {
                dataManager.systemDarkThemeMode.collect { mode ->
                    systemDarkThemeMode = mode
                }
            }

            LaunchedEffect(Unit) {
                dataManager.systemDarkThemeVariant.collect { variant ->
                    systemDarkThemeVariant = variant
                }
            }
            
            // Initialize Flow Neuro Engine
            LaunchedEffect(Unit) {
                io.github.aedev.flow.data.recommendation.FlowNeuroEngine.initialize(applicationContext)
            }

            FlowTheme(
                themeMode = themeMode,
                themeVariant = themeVariant,
                customThemePalettes = customThemePalettes,
                systemLightThemeMode = systemLightThemeMode,
                systemDarkThemeMode = systemDarkThemeMode,
                systemDarkThemeVariant = systemDarkThemeVariant
            ) {
                // Show Dialog Overlay if update exists (github flavor only)
                if (BuildConfig.UPDATER_ENABLED && updateInfo != null) {
                    UpdateDialog(
                        updateInfo = updateInfo!!,
                        onDismiss = { updateInfo = null },
                        onUpdate = {
                            UpdateManager.triggerDownload(context, updateInfo!!.downloadUrl)
                            updateInfo = null
                        }
                    )
                }

                // Handle update from notification (github flavor only)
                if (BuildConfig.UPDATER_ENABLED) {
                    val pendingUpdate by this@MainActivity.pendingUpdateInfo
                    LaunchedEffect(pendingUpdate) {
                        if (pendingUpdate != null) {
                            updateInfo = pendingUpdate
                        }
                    }
                }

                // Request notification permission for Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted) {
                            android.util.Log.d("MainActivity", "Notification permission granted")
                        } else {
                            android.util.Log.w("MainActivity", "Notification permission denied")
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. YOUR MAIN APP (Home/NavHost)
                    // This loads *behind* the splash screen immediately.
                    // By the time splash fades, this is ready.
                    val deeplinkVideoId by this@MainActivity.deeplinkVideoId
                    val isDeeplinkShort by this@MainActivity.isDeeplinkShort
                    val openMusicPlayerRequest by this@MainActivity.openMusicPlayerRequest

                    FlowApp(
                        currentTheme = themeMode,
                        themeVariant = themeVariant,
                        customThemePalettes = customThemePalettes,
                        systemLightThemeMode = systemLightThemeMode,
                        systemDarkThemeMode = systemDarkThemeMode,
                        systemDarkThemeVariant = systemDarkThemeVariant,
                        onThemeChange = { newTheme ->
                            themeMode = newTheme
                            scope.launch {
                                dataManager.setThemeMode(newTheme)
                            }
                        },
                        onThemeVariantChange = { variant ->
                            themeVariant = variant
                            scope.launch {
                                dataManager.setThemeVariant(variant)
                            }
                        },
                        onCustomThemePalettesChange = { palettes ->
                            customThemePalettes = palettes
                            scope.launch {
                                dataManager.setCustomThemePalettes(palettes)
                            }
                        },
                        onSystemLightThemeChange = { newTheme ->
                            systemLightThemeMode = newTheme
                            scope.launch {
                                dataManager.setSystemLightThemeMode(newTheme)
                            }
                        },
                        onSystemDarkThemeChange = { newTheme ->
                            systemDarkThemeMode = newTheme
                            scope.launch {
                                dataManager.setSystemDarkThemeMode(newTheme)
                            }
                        },
                        onSystemDarkThemeVariantChange = { variant ->
                            systemDarkThemeVariant = variant
                            scope.launch {
                                dataManager.setSystemDarkThemeVariant(variant)
                            }
                        },
                        deeplinkVideoId = deeplinkVideoId,
                        isShort = isDeeplinkShort,
                        openMusicPlayerRequest = openMusicPlayerRequest,
                        onDeeplinkConsumed = {
                            consumeDeeplink()
                        }
                    )

                    // 2. THE SPLASH SCREEN (Z-Index Top)
                    if (showSplash) {
                        io.github.aedev.flow.ui.components.FlowSplashScreen(
                            onAnimationFinished = {
                                showSplash = false
                            }
                        )
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        videoLifecycleLog("onDestroy")
        val playerManager = io.github.aedev.flow.player.EnhancedPlayerManager.getInstance()
        val playerState = playerManager.playerState.value
        val hasActiveVideo =
            playerState.currentVideoId != null &&
                (playerState.playWhenReady || playerState.isPlaying || playerState.isBuffering)
        val shouldKeepBackgroundPlayback = BackgroundPlaybackPolicy.shouldKeepPlaybackInBackground(
            backgroundPlaybackPreferenceEnabled = cachedBackgroundPlayEnabled,
            explicitBackgroundPlaybackActive = GlobalPlayerState.isExplicitBackgroundPlaybackActive.value,
            hasActiveVideo = hasActiveVideo
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

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        val notificationVideoId = intent.getStringExtra("notification_video_id") ?: intent.getStringExtra("video_id")

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

        val videoId = if (data != null && intent.action == Intent.ACTION_VIEW) {
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
            } else null
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

        // Check for Update Notification extras
        if (intent.hasExtra("EXTRA_UPDATE_VERSION")) {
            val version = intent.getStringExtra("EXTRA_UPDATE_VERSION") ?: ""
            val changelog = intent.getStringExtra("EXTRA_UPDATE_CHANGELOG") ?: ""
            val url = intent.getStringExtra("EXTRA_UPDATE_URL") ?: ""
            _pendingUpdateInfo.value = UpdateInfo(version, changelog, url, true)
        }
    }

    fun consumeDeeplink() {
        _deeplinkVideoId.value = null
        _isDeeplinkShort.value = false
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("v=([^&]+)"),
            Regex("shorts/([^/?]+)"),
            Regex("youtu.be/([^/?]+)"),
            Regex("embed/([^/?]+)"),
            Regex("v/([^/?]+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return url.substringAfterLast("/").substringBefore("?").ifEmpty { null }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        videoLifecycleLog("onPictureInPictureModeChanged pip=$isInPictureInPictureMode")
        GlobalPlayerState.setPipMode(isInPictureInPictureMode)
        pendingAutoPip = false

        clearWindowBrightnessOverride()

        pipDismissCheckJob?.cancel()
        if (!isInPictureInPictureMode) {
            pipDismissCheckJob = lifecycleScope.launch {
                delay(350L)
                val stillBackgrounded = !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (stillBackgrounded && !isInPictureInPictureMode) {
                    GlobalPlayerState.requestDismiss()
                    io.github.aedev.flow.player.EnhancedPlayerManager.getInstance().stop()
                    io.github.aedev.flow.player.EnhancedPlayerManager.getInstance().stopBackgroundService()
                }
            }
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
        pendingAutoPip = false
        pipDismissCheckJob?.cancel()
        PictureInPictureHelper.dismissPopup(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            io.github.aedev.flow.player.PlayerHardwareController.fullscreenVideoActive.value
        ) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
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
                    }
                )
                if (io.github.aedev.flow.player.PlayerHardwareController.inAppVolumeOverlayEnabled.value) {
                    io.github.aedev.flow.player.PlayerHardwareController.notifyVolumeKey()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            io.github.aedev.flow.player.PlayerHardwareController.fullscreenVideoActive.value
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        FlowCrashHandler.recordPhase(
            "activity",
            "onStop pip=$isInPictureInPictureMode backgroundPlay=$cachedBackgroundPlayEnabled shortsBackground=$cachedShortsBackgroundPlay"
        )
        videoLifecycleLog("onStop")
        if (!isInPictureInPictureMode && !PictureInPictureHelper.isPopupActive) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (!cachedShortsBackgroundPlay) {
                io.github.aedev.flow.player.shorts.ShortsPlayerPool.getInstance().pauseAll()
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
        val explicitBackgroundPlaybackActive =
            GlobalPlayerState.isExplicitBackgroundPlaybackActive.value
        FlowCrashHandler.recordPhase(
            "activity",
            "onUserLeaveHint autoPip=$cachedAutoPipEnabled explicitBackground=$explicitBackgroundPlaybackActive"
        )
        videoLifecycleLog("onUserLeaveHint")
        // Only enter PiP mode if video is playing and has progressed
        // We use the EnhancedPlayerManager directly to get the immediate state
        val playerManager = io.github.aedev.flow.player.EnhancedPlayerManager.getInstance()
        val musicManager = io.github.aedev.flow.player.EnhancedMusicPlayerManager
        
        val isVideoPlaying = playerManager.playerState.value.isPlaying && 
                           playerManager.playerState.value.currentVideoId != null &&
                           playerManager.getCurrentPosition() > 500 // At least 0.5s in
        
        val isMusicPlaying = musicManager.playerState.value.isPlaying
        
        // Only enter PiP for video, not for music (which uses background service)
        val shouldEnterAutoPip = BackgroundPlaybackPolicy.shouldEnterAutoPip(
            autoPipEnabled = cachedAutoPipEnabled,
            isVideoPlaying = isVideoPlaying,
            explicitBackgroundPlaybackActive = explicitBackgroundPlaybackActive
        )
        if (shouldEnterAutoPip && !isMusicPlaying) {
            enterPlayerPictureInPictureMode(
                aspectRatioWidth = 16,
                aspectRatioHeight = 9,
                isPlaying = true
            )
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        FlowCrashHandler.recordPhase("memory", "MainActivity.onTrimMemory level=$level")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            io.github.aedev.flow.player.EnhancedPlayerManager.getInstance()
                .handleCriticalMemoryPressure()
        }
    }

    fun enterPlayerPictureInPictureMode(
        aspectRatioWidth: Int = 16,
        aspectRatioHeight: Int = 9,
        isPlaying: Boolean = true,
        openSettingsOnDenied: Boolean = false
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!PictureInPictureHelper.isPipAllowed(this)) {
            if (openSettingsOnDenied) {
                PictureInPictureHelper.openPipSettings(this)
            }
            return false
        }

        pendingAutoPip = true
        val entered = PictureInPictureHelper.enterPipMode(
            activity = this,
            aspectRatioWidth = aspectRatioWidth,
            aspectRatioHeight = aspectRatioHeight,
            isPlaying = isPlaying,
            autoEnterEnabled = false
        )
        if (!entered) {
            pendingAutoPip = false
        }
        return entered
    }

    private fun handOffVideoPlaybackToBackground() {
        FlowCrashHandler.recordPhase("background-handoff", "handOffVideoPlaybackToBackground")
        videoLifecycleLog("handOffVideoPlaybackToBackground")
        val playerManager = io.github.aedev.flow.player.EnhancedPlayerManager.getInstance()
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
                thumbnail = video?.thumbnailUrl ?: ""
            )
            playerManager.continueVideoPlaybackInBackground()
        }
    }

    private fun handleBackgroundPlaybackOnStop() {
        FlowCrashHandler.recordPhase("background-handoff", "handleBackgroundPlaybackOnStop")
        videoLifecycleLog("handleBackgroundPlaybackOnStop")
        val playerManager = io.github.aedev.flow.player.EnhancedPlayerManager.getInstance()
        val playerState = playerManager.playerState.value
        val hasActiveVideo =
            playerState.currentVideoId != null &&
                (playerState.playWhenReady || playerState.isPlaying || playerState.isBuffering)

        if (!hasActiveVideo) return

        val shouldKeepBackgroundPlayback = BackgroundPlaybackPolicy.shouldKeepPlaybackInBackground(
            backgroundPlaybackPreferenceEnabled = cachedBackgroundPlayEnabled,
            explicitBackgroundPlaybackActive = GlobalPlayerState.isExplicitBackgroundPlaybackActive.value,
            hasActiveVideo = hasActiveVideo
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

    private fun checkForUpdates(dataManager: LocalDataManager) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Check cooldown (24 hours)
                val lastCheck = dataManager.lastUpdateCheck.first()
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastCheck < 24 * 60 * 60 * 1000) {
                    Log.d("MainActivity", "Skipping update check (cooldown)")
                    return@launch
                }

                val client = AppProxyManager.applyTo(OkHttpClient.Builder()).build()
                val request = Request.Builder()
                    .url("https://api.github.com/repos/A-EDev/Flow/releases/latest")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JsonParser.parseString(body).asJsonObject
                        val latestTag = json.get("tag_name").asString
                        val currentVersion = BuildConfig.VERSION_NAME
                        
                        val cleanLatest = latestTag.removePrefix("v").split("-").first()
                        val cleanCurrent = currentVersion.removePrefix("v").split("-").first()
                        
                        Log.d("MainActivity", "Latest tag: $latestTag, Current: $currentVersion, Comparing: $cleanLatest vs $cleanCurrent")
                        
                        if (isNewerVersion(cleanLatest, cleanCurrent)) {
                            withContext(Dispatchers.Main) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Update Available")
                                    .setMessage("A new version of Flow is available ($latestTag). Download the latest APK?")
                                    .setPositiveButton("Download") { _, _ ->
                                        ApkUpdateHelper.requestDownload(this@MainActivity, "https://github.com/A-EDev/Flow/releases/latest")
                                    }
                                    .setNegativeButton("Later", null)
                                    .show()
                            }
                        }
                    }
                }
                
                // Update last check time
                dataManager.setLastUpdateCheck(currentTime)
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to check for updates", e)
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val cleanLatest = latest.split("-").first()
        val cleanCurrent = current.split("-").first()
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        
        val size = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until size) {
            val l = latestParts.getOrNull(i) ?: 0
            val c = currentParts.getOrNull(i) ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    /**
     * Ask Android to whitelist this app from battery optimization / Doze mode.
     *
     * Without this, on aggressive OEM ROMs (Xiaomi MIUI, Samsung OneUI DeX, CRDroid, Huawei)
     * the OS can throttle network access or kill the background playback service after a few
     * minutes of screen-off. 
     *
     * The system shows a standard dialog asking the user to confirm.  We only request this once
     * per install (if the app is not already exempt).  No spammy repeat prompts.
     */
    private fun requestBatteryOptimizationExemptionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return // already exempt
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w("MainActivity", "Could not request battery optimization exemption: ${e.message}")
        }
    }
}
