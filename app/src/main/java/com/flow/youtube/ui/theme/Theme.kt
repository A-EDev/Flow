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
    LIGHT, DARK, OLED, LAVENDER_MIST, OCEAN_BLUE, FOREST_GREEN, SUNSET_ORANGE, PURPLE_NEBULA, MIDNIGHT_BLACK,
    ROSE_GOLD, ARCTIC_ICE, CRIMSON_RED, MINTY_FRESH, COSMIC_VOID, SOLAR_FLARE, CYBERPUNK,
    ROYAL_GOLD, NORDIC_HORIZON, ESPRESSO, GUNMETAL,
    MINT_LIGHT, ROSE_LIGHT, SKY_LIGHT, CREAM_LIGHT
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

// --- Color Schemes ---

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

private val LavenderMistColorScheme = darkColorScheme(
    primary = Color(0xFFB39DDB),
    onPrimary = Color.Black,
    secondary = Color(0xFF9575CD),
    onSecondary = Color.Black,
    background = Color(0xFF120F1A),
    onBackground = Color(0xFFEDE7F6),
    surface = Color(0xFF1F1A2E),
    onSurface = Color(0xFFEDE7F6),
    error = Color(0xFFEF5350),
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

private val MintyFreshColorScheme = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color.Black,
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color.Black,
    background = Color(0xFF0F1A18),
    onBackground = Color(0xFFE0F2F1),
    surface = Color(0xFF1A2E2B),
    onSurface = Color(0xFFE0F2F1),
    error = Color(0xFFEF5350),
    onError = Color.White
)

private val CosmicVoidColorScheme = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    secondary = Color(0xFF651FFF),
    onSecondary = Color.White,
    background = Color(0xFF050505),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E0E0),
    error = Color(0xFFFF5252),
    onError = Color.White
)

private val SolarFlareColorScheme = darkColorScheme(
    primary = Color(0xFFFFD740),
    onPrimary = Color.Black,
    secondary = Color(0xFFFFAB00),
    onSecondary = Color.Black,
    background = Color(0xFF1A1500),
    onBackground = Color(0xFFFFFDE7),
    surface = Color(0xFF2E2600),
    onSurface = Color(0xFFFFFDE7),
    error = Color(0xFFFF5252),
    onError = Color.White
)

private val CyberpunkColorScheme = darkColorScheme(
    primary = Color(0xFFFF00FF),
    onPrimary = Color.White,
    secondary = Color(0xFF00FFFF),
    onSecondary = Color.Black,
    background = Color(0xFF0D001A),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1F0033),
    onSurface = Color(0xFFE0E0E0),
    error = Color(0xFFFF1744),
    onError = Color.White
)

private val RoyalGoldColorScheme = darkColorScheme(
    primary = RoyalGoldThemeColors.Primary,
    onPrimary = RoyalGoldThemeColors.OnPrimary,
    secondary = RoyalGoldThemeColors.Secondary,
    onSecondary = RoyalGoldThemeColors.OnSecondary,
    background = RoyalGoldThemeColors.Background,
    onBackground = RoyalGoldThemeColors.Text,
    surface = RoyalGoldThemeColors.Surface,
    onSurface = RoyalGoldThemeColors.Text,
    error = RoyalGoldThemeColors.Error,
    onError = Color.Black
)

private val NordicHorizonColorScheme = darkColorScheme(
    primary = NordicHorizonThemeColors.Primary,
    onPrimary = NordicHorizonThemeColors.OnPrimary,
    secondary = NordicHorizonThemeColors.Secondary,
    onSecondary = NordicHorizonThemeColors.OnSecondary,
    background = NordicHorizonThemeColors.Background,
    onBackground = NordicHorizonThemeColors.Text,
    surface = NordicHorizonThemeColors.Surface,
    onSurface = NordicHorizonThemeColors.Text,
    error = NordicHorizonThemeColors.Error,
    onError = Color.Black
)

private val EspressoColorScheme = darkColorScheme(
    primary = EspressoThemeColors.Primary,
    onPrimary = EspressoThemeColors.OnPrimary,
    secondary = EspressoThemeColors.Secondary,
    onSecondary = EspressoThemeColors.OnSecondary,
    background = EspressoThemeColors.Background,
    onBackground = EspressoThemeColors.Text,
    surface = EspressoThemeColors.Surface,
    onSurface = EspressoThemeColors.Text,
    error = EspressoThemeColors.Error,
    onError = Color.White
)

private val GunmetalColorScheme = darkColorScheme(
    primary = GunmetalThemeColors.Primary,
    onPrimary = GunmetalThemeColors.OnPrimary,
    secondary = GunmetalThemeColors.Secondary,
    onSecondary = GunmetalThemeColors.OnSecondary,
    background = GunmetalThemeColors.Background,
    onBackground = GunmetalThemeColors.Text,
    surface = GunmetalThemeColors.Surface,
    onSurface = GunmetalThemeColors.Text,
    error = GunmetalThemeColors.Error,
    onError = Color.White
)

private val MintLightColorScheme = lightColorScheme(
    primary = MintLightThemeColors.Primary,
    onPrimary = MintLightThemeColors.OnPrimary,
    secondary = MintLightThemeColors.Secondary,
    onSecondary = MintLightThemeColors.OnSecondary,
    background = MintLightThemeColors.Background,
    onBackground = MintLightThemeColors.Text,
    surface = MintLightThemeColors.Surface,
    onSurface = MintLightThemeColors.Text,
    error = ErrorColor
)

private val RoseLightColorScheme = lightColorScheme(
    primary = RoseLightThemeColors.Primary,
    onPrimary = RoseLightThemeColors.OnPrimary,
    secondary = RoseLightThemeColors.Secondary,
    onSecondary = RoseLightThemeColors.OnSecondary,
    background = RoseLightThemeColors.Background,
    onBackground = RoseLightThemeColors.Text,
    surface = RoseLightThemeColors.Surface,
    onSurface = RoseLightThemeColors.Text,
    error = ErrorColor
)

private val SkyLightColorScheme = lightColorScheme(
    primary = SkyLightThemeColors.Primary,
    onPrimary = SkyLightThemeColors.OnPrimary,
    secondary = SkyLightThemeColors.Secondary,
    onSecondary = SkyLightThemeColors.OnSecondary,
    background = SkyLightThemeColors.Background,
    onBackground = SkyLightThemeColors.Text,
    surface = SkyLightThemeColors.Surface,
    onSurface = SkyLightThemeColors.Text,
    error = ErrorColor
)

private val CreamLightColorScheme = lightColorScheme(
    primary = CreamLightThemeColors.Primary,
    onPrimary = CreamLightThemeColors.OnPrimary,
    secondary = CreamLightThemeColors.Secondary,
    onSecondary = CreamLightThemeColors.OnSecondary,
    background = CreamLightThemeColors.Background,
    onBackground = CreamLightThemeColors.Text,
    surface = CreamLightThemeColors.Surface,
    onSurface = CreamLightThemeColors.Text,
    error = ErrorColor
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
        ThemeMode.LAVENDER_MIST -> LavenderMistColorScheme
        ThemeMode.OCEAN_BLUE -> OceanBlueColorScheme
        ThemeMode.FOREST_GREEN -> ForestGreenColorScheme
        ThemeMode.SUNSET_ORANGE -> SunsetOrangeColorScheme
        ThemeMode.PURPLE_NEBULA -> PurpleNebulaColorScheme
        ThemeMode.MIDNIGHT_BLACK -> MidnightBlackColorScheme
        ThemeMode.ROSE_GOLD -> RoseGoldColorScheme
        ThemeMode.ARCTIC_ICE -> ArcticIceColorScheme
        ThemeMode.CRIMSON_RED -> CrimsonRedColorScheme
        ThemeMode.MINTY_FRESH -> MintyFreshColorScheme
        ThemeMode.COSMIC_VOID -> CosmicVoidColorScheme
        ThemeMode.SOLAR_FLARE -> SolarFlareColorScheme
        ThemeMode.CYBERPUNK -> CyberpunkColorScheme
        ThemeMode.ROYAL_GOLD -> RoyalGoldColorScheme
        ThemeMode.NORDIC_HORIZON -> NordicHorizonColorScheme
        ThemeMode.ESPRESSO -> EspressoColorScheme
        ThemeMode.GUNMETAL -> GunmetalColorScheme
        ThemeMode.MINT_LIGHT -> MintLightColorScheme
        ThemeMode.ROSE_LIGHT -> RoseLightColorScheme
        ThemeMode.SKY_LIGHT -> SkyLightColorScheme
        ThemeMode.CREAM_LIGHT -> CreamLightColorScheme
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
        ThemeMode.ROYAL_GOLD -> ExtendedColors(
            textSecondary = RoyalGoldThemeColors.TextSecondary,
            border = RoyalGoldThemeColors.Border,
            success = RoyalGoldThemeColors.Success
        )
        ThemeMode.NORDIC_HORIZON -> ExtendedColors(
            textSecondary = NordicHorizonThemeColors.TextSecondary,
            border = NordicHorizonThemeColors.Border,
            success = NordicHorizonThemeColors.Success
        )
        ThemeMode.ESPRESSO -> ExtendedColors(
            textSecondary = EspressoThemeColors.TextSecondary,
            border = EspressoThemeColors.Border,
            success = EspressoThemeColors.Success
        )
        ThemeMode.GUNMETAL -> ExtendedColors(
            textSecondary = GunmetalThemeColors.TextSecondary,
            border = GunmetalThemeColors.Border,
            success = GunmetalThemeColors.Success
        )
        ThemeMode.MINT_LIGHT -> ExtendedColors(
            textSecondary = MintLightThemeColors.TextSecondary,
            border = MintLightThemeColors.Border,
            success = MintLightThemeColors.Success
        )
        ThemeMode.ROSE_LIGHT -> ExtendedColors(
            textSecondary = RoseLightThemeColors.TextSecondary,
            border = RoseLightThemeColors.Border,
            success = RoseLightThemeColors.Success
        )
        ThemeMode.SKY_LIGHT -> ExtendedColors(
            textSecondary = SkyLightThemeColors.TextSecondary,
            border = SkyLightThemeColors.Border,
            success = SkyLightThemeColors.Success
        )
        ThemeMode.CREAM_LIGHT -> ExtendedColors(
            textSecondary = CreamLightThemeColors.TextSecondary,
            border = CreamLightThemeColors.Border,
            success = CreamLightThemeColors.Success
        )
        else -> ExtendedColors(
            textSecondary = DarkThemeColors.TextSecondary,
            border = DarkThemeColors.Border,
            success = DarkThemeColors.Success
        )
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            // typography = Typography, // Uncomment this if you have Typography defined in another file
            content = content
        )
    }
}

// THIS IS THE PART THAT WAS MISSING CAUSING THE ERRORS
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current