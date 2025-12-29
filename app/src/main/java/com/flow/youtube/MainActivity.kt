package com.flow.youtube

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize global player state
        GlobalPlayerState.initialize(applicationContext)

        val dataManager = LocalDataManager(applicationContext)

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
