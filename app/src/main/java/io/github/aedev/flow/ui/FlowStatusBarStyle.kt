package io.github.aedev.flow.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
import io.github.aedev.flow.ui.theme.isEffectivelyDark

@Composable
internal fun ApplyStatusBarStyle(
    themeMode: ThemeMode,
    themeVariant: ThemeVariant,
    systemLightThemeMode: ThemeMode,
    systemDarkThemeMode: ThemeMode,
    isFullscreen: Boolean,
    isMusicPlayerImmersive: Boolean = false,
    musicPlayerFollowsTheme: Boolean = false,
    isShortsPlayer: Boolean = false,
) {
    val activity = LocalContext.current as? Activity ?: return
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme =
        themeMode.isEffectivelyDark(
            isSystemDark = isSystemDark,
            systemLightThemeMode = systemLightThemeMode,
            systemDarkThemeMode = systemDarkThemeMode,
            themeVariant = themeVariant,
        )

    SideEffect {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)
        val shouldDrawBehindStatusBar = isFullscreen || isMusicPlayerImmersive || isShortsPlayer

        window.statusBarColor =
            if (shouldDrawBehindStatusBar) {
                android.graphics.Color.TRANSPARENT
            } else {
                colorScheme.background.toArgb()
            }

        val musicImmersiveOnLightSurface =
            isMusicPlayerImmersive && musicPlayerFollowsTheme && !isDarkTheme && !isFullscreen && !isShortsPlayer
        insetsController.isAppearanceLightStatusBars =
            (!isDarkTheme && !shouldDrawBehindStatusBar) || musicImmersiveOnLightSurface
    }
}
