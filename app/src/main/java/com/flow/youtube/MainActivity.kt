package com.flow.youtube

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.flow.youtube.data.local.LocalDataManager
import com.flow.youtube.player.GlobalPlayerState
import com.flow.youtube.ui.FlowApp
import com.flow.youtube.ui.theme.FlowTheme
import com.flow.youtube.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val _deeplinkVideoId = mutableStateOf<String?>(null)
    val deeplinkVideoId: State<String?> = _deeplinkVideoId

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize global player state
        GlobalPlayerState.initialize(applicationContext)

        val dataManager = LocalDataManager(applicationContext)

        handleIntent(intent)

        setContent {
            val scope = rememberCoroutineScope()
            var themeMode by remember { mutableStateOf(ThemeMode.LIGHT) }
            // State to control splash visibility
            var showSplash by remember { mutableStateOf(true) }

            // Load theme preference
            LaunchedEffect(Unit) {
                themeMode = dataManager.themeMode.first()
            }

            FlowTheme(themeMode = themeMode) {
                // Request notification permission for Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val context = androidx.compose.ui.platform.LocalContext.current
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
                    FlowApp(
                        currentTheme = themeMode,
                        onThemeChange = { newTheme ->
                            themeMode = newTheme
                            scope.launch {
                                dataManager.setThemeMode(newTheme)
                            }
                        }
                    )

                    // 2. THE SPLASH SCREEN (Z-Index Top)
                    if (showSplash) {
                        com.flow.youtube.ui.components.FlowSplashScreen(
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
        super.onDestroy()
        // Release player when app is destroyed
        GlobalPlayerState.release()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        val notificationVideoId = intent.getStringExtra("notification_video_id") ?: intent.getStringExtra("video_id")
        
        val videoId = if (data != null && intent.action == Intent.ACTION_VIEW) {
            extractVideoId(data.toString())
        } else {
            notificationVideoId
        }
        
        if (videoId != null) {
            _deeplinkVideoId.value = videoId
            intent.putExtra("deeplink_video_id", videoId)
        }
    }

    fun consumeDeeplink() {
        _deeplinkVideoId.value = null
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
        GlobalPlayerState.setPipMode(isInPictureInPictureMode)
        
        // If we are exiting PiP mode, ensure we are in portrait if not in fullscreen
        if (!isInPictureInPictureMode) {
            // We don't force portrait here because the user might have been in landscape fullscreen
            // and they want to return to it. 
            // But if they were in PiP and just closed it, the activity is destroyed anyway.
        }
    }

    override fun onStop() {
        super.onStop()
        // If the app is going to background and NOT in PiP, reset orientation to portrait
        // This fixes the issue where exiting the app from landscape keeps the system in landscape
        if (!isInPictureInPictureMode) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Only enter PiP mode if video is playing
        // We use the EnhancedPlayerManager directly to get the immediate state
        val playerManager = com.flow.youtube.player.EnhancedPlayerManager.getInstance()
        if (playerManager.playerState.value.isPlaying && playerManager.playerState.value.currentVideoId != null) {
            enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
            )
        }
    }
}
