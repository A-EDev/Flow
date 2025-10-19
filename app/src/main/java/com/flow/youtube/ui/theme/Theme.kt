package com.flow.youtube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    LIGHT, DARK, OLED, OCEAN_BLUE, FOREST_GREEN, SUNSET_ORANGE, PURPLE_NEBULA, MIDNIGHT_BLACK,
    ROSE_GOLD, ARCTIC_ICE, CRIMSON_RED
}

data class ExtendedColors(
    val textSecondary: Color,
    val border: Color,
    val success: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        textSecondary = Color.Unspecified,
        border = Color.Unspecified,
        success = Color.Unspecified
    )
}

private val LightColorScheme = lightColorScheme(
    primary = LightThemeColors.Primary,
    onPrimary = LightThemeColors.OnPrimary,
    secondary = LightThemeColors.Secondary,
    onSecondary = LightThemeColors.OnSecondary,
    background = LightThemeColors.Background,
    onBackground = LightThemeColors.Text,
    surface = LightThemeColors.Surface,
    onSurface = LightThemeColors.Text,
    error = LightThemeColors.Error,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkThemeColors.Primary,
    onPrimary = DarkThemeColors.OnPrimary,
    secondary = DarkThemeColors.Secondary,
    onSecondary = DarkThemeColors.OnSecondary,
    background = DarkThemeColors.Background,
    onBackground = DarkThemeColors.Text,
    surface = DarkThemeColors.Surface,
    onSurface = DarkThemeColors.Text,
    error = DarkThemeColors.Error,
    onError = Color.Black
)

private val OLEDColorScheme = darkColorScheme(
    primary = OLEDThemeColors.Primary,
    onPrimary = OLEDThemeColors.OnPrimary,
    secondary = OLEDThemeColors.Secondary,
    onSecondary = OLEDThemeColors.OnSecondary,
    background = OLEDThemeColors.Background,
    onBackground = OLEDThemeColors.Text,
    surface = OLEDThemeColors.Surface,
    onSurface = OLEDThemeColors.Text,
    error = OLEDThemeColors.Error,
    onError = Color.White
)

private val OceanBlueColorScheme = darkColorScheme(
    primary = OceanBlueThemeColors.Primary,
    onPrimary = OceanBlueThemeColors.OnPrimary,
    secondary = OceanBlueThemeColors.Secondary,
    onSecondary = OceanBlueThemeColors.OnSecondary,
    background = OceanBlueThemeColors.Background,
    onBackground = OceanBlueThemeColors.Text,
    surface = OceanBlueThemeColors.Surface,
    onSurface = OceanBlueThemeColors.Text,
    error = OceanBlueThemeColors.Error,
    onError = Color.White
)

private val ForestGreenColorScheme = darkColorScheme(
    primary = ForestGreenThemeColors.Primary,
    onPrimary = ForestGreenThemeColors.OnPrimary,
    secondary = ForestGreenThemeColors.Secondary,
    onSecondary = ForestGreenThemeColors.OnSecondary,
    background = ForestGreenThemeColors.Background,
    onBackground = ForestGreenThemeColors.Text,
    surface = ForestGreenThemeColors.Surface,
    onSurface = ForestGreenThemeColors.Text,
    error = ForestGreenThemeColors.Error,
    onError = Color.White
)

private val SunsetOrangeColorScheme = darkColorScheme(
    primary = SunsetOrangeThemeColors.Primary,
    onPrimary = SunsetOrangeThemeColors.OnPrimary,
    secondary = SunsetOrangeThemeColors.Secondary,
    onSecondary = SunsetOrangeThemeColors.OnSecondary,
    background = SunsetOrangeThemeColors.Background,
    onBackground = SunsetOrangeThemeColors.Text,
    surface = SunsetOrangeThemeColors.Surface,
    onSurface = SunsetOrangeThemeColors.Text,
    error = SunsetOrangeThemeColors.Error,
    onError = Color.White
)

private val PurpleNebulaColorScheme = darkColorScheme(
    primary = PurpleNebulaThemeColors.Primary,
    onPrimary = PurpleNebulaThemeColors.OnPrimary,
    secondary = PurpleNebulaThemeColors.Secondary,
    onSecondary = PurpleNebulaThemeColors.OnSecondary,
    background = PurpleNebulaThemeColors.Background,
    onBackground = PurpleNebulaThemeColors.Text,
    surface = PurpleNebulaThemeColors.Surface,
    onSurface = PurpleNebulaThemeColors.Text,
    error = PurpleNebulaThemeColors.Error,
    onError = Color.White
)

private val MidnightBlackColorScheme = darkColorScheme(
    primary = MidnightBlackThemeColors.Primary,
    onPrimary = MidnightBlackThemeColors.OnPrimary,
    secondary = MidnightBlackThemeColors.Secondary,
    onSecondary = MidnightBlackThemeColors.OnSecondary,
    background = MidnightBlackThemeColors.Background,
    onBackground = MidnightBlackThemeColors.Text,
    surface = MidnightBlackThemeColors.Surface,
    onSurface = MidnightBlackThemeColors.Text,
    error = MidnightBlackThemeColors.Error,
    onError = Color.White
)

private val RoseGoldColorScheme = darkColorScheme(
    primary = RoseGoldThemeColors.Primary,
    onPrimary = RoseGoldThemeColors.OnPrimary,
    secondary = RoseGoldThemeColors.Secondary,
    onSecondary = RoseGoldThemeColors.OnSecondary,
    background = RoseGoldThemeColors.Background,
    onBackground = RoseGoldThemeColors.Text,
    surface = RoseGoldThemeColors.Surface,
    onSurface = RoseGoldThemeColors.Text,
    error = RoseGoldThemeColors.Error,
    onError = Color.White
)

private val ArcticIceColorScheme = darkColorScheme(
    primary = ArcticIceThemeColors.Primary,
    onPrimary = ArcticIceThemeColors.OnPrimary,
    secondary = ArcticIceThemeColors.Secondary,
    onSecondary = ArcticIceThemeColors.OnSecondary,
    background = ArcticIceThemeColors.Background,
    onBackground = ArcticIceThemeColors.Text,
    surface = ArcticIceThemeColors.Surface,
    onSurface = ArcticIceThemeColors.Text,
    error = ArcticIceThemeColors.Error,
    onError = Color.White
)

private val CrimsonRedColorScheme = darkColorScheme(
    primary = CrimsonRedThemeColors.Primary,
    onPrimary = CrimsonRedThemeColors.OnPrimary,
    secondary = CrimsonRedThemeColors.Secondary,
    onSecondary = CrimsonRedThemeColors.OnSecondary,
    background = CrimsonRedThemeColors.Background,
    onBackground = CrimsonRedThemeColors.Text,
    surface = CrimsonRedThemeColors.Surface,
    onSurface = CrimsonRedThemeColors.Text,
    error = CrimsonRedThemeColors.Error,
    onError = Color.White
)

@Composable
fun FlowTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.OLED -> OLEDColorScheme
        ThemeMode.OCEAN_BLUE -> OceanBlueColorScheme
        ThemeMode.FOREST_GREEN -> ForestGreenColorScheme
        ThemeMode.SUNSET_ORANGE -> SunsetOrangeColorScheme
        ThemeMode.PURPLE_NEBULA -> PurpleNebulaColorScheme
        ThemeMode.MIDNIGHT_BLACK -> MidnightBlackColorScheme
        ThemeMode.ROSE_GOLD -> RoseGoldColorScheme
        ThemeMode.ARCTIC_ICE -> ArcticIceColorScheme
        ThemeMode.CRIMSON_RED -> CrimsonRedColorScheme
    }

    val extendedColors = when (themeMode) {
        ThemeMode.LIGHT -> ExtendedColors(
            textSecondary = LightThemeColors.TextSecondary,
            border = LightThemeColors.Border,
            success = LightThemeColors.Success
        )
        ThemeMode.DARK -> ExtendedColors(
            textSecondary = DarkThemeColors.TextSecondary,
            border = DarkThemeColors.Border,
            success = DarkThemeColors.Success
        )
        ThemeMode.OLED -> ExtendedColors(
            textSecondary = OLEDThemeColors.TextSecondary,
            border = OLEDThemeColors.Border,
            success = OLEDThemeColors.Success
        )
        ThemeMode.OCEAN_BLUE -> ExtendedColors(
            textSecondary = OceanBlueThemeColors.TextSecondary,
            border = OceanBlueThemeColors.Border,
            success = OceanBlueThemeColors.Success
        )
        ThemeMode.FOREST_GREEN -> ExtendedColors(
            textSecondary = ForestGreenThemeColors.TextSecondary,
            border = ForestGreenThemeColors.Border,
            success = ForestGreenThemeColors.Success
        )
        ThemeMode.SUNSET_ORANGE -> ExtendedColors(
            textSecondary = SunsetOrangeThemeColors.TextSecondary,
            border = SunsetOrangeThemeColors.Border,
            success = SunsetOrangeThemeColors.Success
        )
        ThemeMode.PURPLE_NEBULA -> ExtendedColors(
            textSecondary = PurpleNebulaThemeColors.TextSecondary,
            border = PurpleNebulaThemeColors.Border,
            success = PurpleNebulaThemeColors.Success
        )
        ThemeMode.MIDNIGHT_BLACK -> ExtendedColors(
            textSecondary = MidnightBlackThemeColors.TextSecondary,
            border = MidnightBlackThemeColors.Border,
            success = MidnightBlackThemeColors.Success
        )
        ThemeMode.ROSE_GOLD -> ExtendedColors(
            textSecondary = RoseGoldThemeColors.TextSecondary,
            border = RoseGoldThemeColors.Border,
            success = RoseGoldThemeColors.Success
        )
        ThemeMode.ARCTIC_ICE -> ExtendedColors(
            textSecondary = ArcticIceThemeColors.TextSecondary,
            border = ArcticIceThemeColors.Border,
            success = ArcticIceThemeColors.Success
        )
        ThemeMode.CRIMSON_RED -> ExtendedColors(
            textSecondary = CrimsonRedThemeColors.TextSecondary,
            border = CrimsonRedThemeColors.Border,
            success = CrimsonRedThemeColors.Success
        )
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Extension property to access extended colors
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current

