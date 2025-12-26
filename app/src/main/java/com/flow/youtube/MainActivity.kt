package com.flow.youtube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
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

            // Load theme preference
            LaunchedEffect(Unit) {
                themeMode = dataManager.themeMode.first()
            }

            FlowTheme(themeMode = themeMode) {
                FlowApp(
                    currentTheme = themeMode,
                    onThemeChange = { newTheme ->
                        themeMode = newTheme
                        scope.launch {
                            dataManager.setThemeMode(newTheme)
                        }
                    }
                )
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
