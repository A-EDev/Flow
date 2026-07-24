package io.github.aedev.flow.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils

enum class ThemeMode {
    LIGHT, DARK, OLED, SYSTEM, LAVENDER_MIST, OCEAN_BLUE, FOREST_GREEN, SUNSET_ORANGE, PURPLE_NEBULA, MIDNIGHT_BLACK,
    ROSE_GOLD, ARCTIC_ICE, CRIMSON_RED, MINTY_FRESH, COSMIC_VOID, SOLAR_FLARE, CYBERPUNK,
    ROYAL_GOLD, NORDIC_HORIZON, ESPRESSO, GUNMETAL,
    MINT_LIGHT, ROSE_LIGHT, SKY_LIGHT, CREAM_LIGHT,
    MONOCHROME, CUSTOM, MATERIAL_YOU
}

fun ThemeMode.defaultVariant(): ThemeVariant = when (this) {
    ThemeMode.LIGHT,
    ThemeMode.MINT_LIGHT,
    ThemeMode.ROSE_LIGHT,
    ThemeMode.SKY_LIGHT,
    ThemeMode.CREAM_LIGHT -> ThemeVariant.LIGHT

    ThemeMode.OLED,
    ThemeMode.MIDNIGHT_BLACK,
    ThemeMode.MONOCHROME -> ThemeVariant.AMOLED

    else -> ThemeVariant.DARK
}

fun ThemeMode.canonicalFamily(): ThemeMode = when (this) {
    ThemeMode.LIGHT,
    ThemeMode.OLED -> ThemeMode.DARK

    else -> this
}

fun ThemeMode.resolveSystemDefault(
    isSystemDark: Boolean,
    systemLightThemeMode: ThemeMode = ThemeMode.DARK,
    systemDarkThemeMode: ThemeMode = ThemeMode.DARK
): ThemeMode {
    if (this != ThemeMode.SYSTEM) return canonicalFamily()

    val selectedMode = if (isSystemDark) systemDarkThemeMode else systemLightThemeMode
    return if (selectedMode == ThemeMode.SYSTEM) {
        if (isSystemDark) ThemeMode.DARK else ThemeMode.LIGHT
    } else {
        selectedMode.canonicalFamily()
    }
}

fun ThemeMode.isEffectivelyDark(
    isSystemDark: Boolean,
    systemLightThemeMode: ThemeMode = ThemeMode.DARK,
    systemDarkThemeMode: ThemeMode = ThemeMode.DARK,
    themeVariant: ThemeVariant? = null
): Boolean {
    if (this == ThemeMode.SYSTEM) return isSystemDark
    themeVariant?.let { return it != ThemeVariant.LIGHT }
    return when (resolveSystemDefault(isSystemDark, systemLightThemeMode, systemDarkThemeMode)) {
        ThemeMode.LIGHT,
        ThemeMode.MINT_LIGHT,
        ThemeMode.ROSE_LIGHT,
        ThemeMode.SKY_LIGHT,
        ThemeMode.CREAM_LIGHT -> false

        ThemeMode.SYSTEM,
        ThemeMode.MATERIAL_YOU -> isSystemDark

        else -> true
    }
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

private fun Color.adjust(
    saturationFactor: Float = 1.0f,
    lightnessFactor: Float = 1.0f,
    lightnessOverride: Float? = null
): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[1] = (hsl[1] * saturationFactor).coerceIn(0.0f, 1.0f)
    hsl[2] = lightnessOverride ?: (hsl[2] * lightnessFactor).coerceIn(0.0f, 1.0f)
    return Color(ColorUtils.HSLToColor(hsl))
}

fun ColorScheme.complete(isDark: Boolean, isOled: Boolean = false): ColorScheme {
    val primaryContainerColor = if (isDark) {
        if (isOled) Color.Black else primary.adjust(saturationFactor = 0.45f, lightnessOverride = 0.15f)
    } else {
        primary.adjust(saturationFactor = 0.35f, lightnessOverride = 0.94f)
    }
    val onPrimaryContainerColor = if (isDark) {
        primary.adjust(saturationFactor = 0.30f, lightnessOverride = 0.88f)
    } else {
        primary.adjust(saturationFactor = 0.90f, lightnessOverride = 0.15f)
    }

    val secondaryContainerColor = if (isDark) {
        if (isOled) Color(0xFF161616) else secondary.adjust(saturationFactor = 0.40f, lightnessOverride = 0.14f)
    } else {
        secondary.adjust(saturationFactor = 0.30f, lightnessOverride = 0.94f)
    }
    val onSecondaryContainerColor = if (isDark) {
        secondary.adjust(saturationFactor = 0.30f, lightnessOverride = 0.88f)
    } else {
        secondary.adjust(saturationFactor = 0.90f, lightnessOverride = 0.15f)
    }

    val tertiaryColor = if (tertiary == Color.Unspecified || tertiary == primary) {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(primary.toArgb(), hsl)
        hsl[0] = (hsl[0] + 60f) % 360f // Shift by 60 degrees for analogous/complementary balance
        Color(ColorUtils.HSLToColor(hsl))
    } else {
        tertiary
    }
    
    val tertiaryContainerColor = if (isDark) {
        if (isOled) Color.Black else tertiaryColor.adjust(saturationFactor = 0.40f, lightnessOverride = 0.14f)
    } else {
        tertiaryColor.adjust(saturationFactor = 0.30f, lightnessOverride = 0.94f)
    }
    val onTertiaryContainerColor = if (isDark) {
        tertiaryColor.adjust(saturationFactor = 0.30f, lightnessOverride = 0.88f)
    } else {
        tertiaryColor.adjust(saturationFactor = 0.90f, lightnessOverride = 0.15f)
    }

    val errorContainerColor = if (isDark) {
        error.adjust(saturationFactor = 0.40f, lightnessOverride = 0.15f)
    } else {
        error.adjust(saturationFactor = 0.30f, lightnessOverride = 0.94f)
    }
    val onErrorContainerColor = if (isDark) {
        error.adjust(saturationFactor = 0.30f, lightnessOverride = 0.88f)
    } else {
        error.adjust(saturationFactor = 0.90f, lightnessOverride = 0.15f)
    }

    val surfaceVariantColor = if (isDark) {
        if (isOled) Color(0xFF0C0C0C) else surface.adjust(lightnessOverride = 0.12f)
    } else {
        surface.adjust(saturationFactor = 0.10f, lightnessOverride = 0.92f)
    }
    val onSurfaceVariantColor = if (isDark) {
        onSurface.adjust(lightnessOverride = 0.75f)
    } else {
        onSurface.adjust(lightnessOverride = 0.35f)
    }
    
    val outlineColor = if (isDark) {
        surface.adjust(lightnessOverride = 0.38f)
    } else {
        surface.adjust(lightnessOverride = 0.50f)
    }
    val outlineVariantColor = if (isDark) {
        surface.adjust(lightnessOverride = 0.22f)
    } else {
        surface.adjust(lightnessOverride = 0.85f)
    }

    val inverseSurfaceColor = if (isDark) {
        Color.White
    } else {
        Color(0xFF1E1E1E)
    }
    val inverseOnSurfaceColor = if (isDark) {
        Color(0xFF121212)
    } else {
        Color.White
    }

    val surfaceContainerLowestColor = if (isDark) Color.Black else Color.White
    val surfaceContainerLowColor = if (isDark) {
        if (isOled) Color(0xFF0A0A0A) else surface.adjust(lightnessOverride = 0.06f)
    } else {
        surface.adjust(lightnessOverride = 0.96f)
    }
    val surfaceContainerColor = if (isDark) {
        if (isOled) Color(0xFF0F0F0F) else surface.adjust(lightnessOverride = 0.08f)
    } else {
        surface.adjust(lightnessOverride = 0.94f)
    }
    val surfaceContainerHighColor = if (isDark) {
        if (isOled) Color(0xFF161616) else surface.adjust(lightnessOverride = 0.10f)
    } else {
        surface.adjust(lightnessOverride = 0.92f)
    }
    val surfaceContainerHighestColor = if (isDark) {
        if (isOled) Color(0xFF202020) else surface.adjust(lightnessOverride = 0.14f)
    } else {
        surface.adjust(lightnessOverride = 0.90f)
    }

    return this.copy(
        primaryContainer = primaryContainerColor,
        onPrimaryContainer = onPrimaryContainerColor,
        secondaryContainer = secondaryContainerColor,
        onSecondaryContainer = onSecondaryContainerColor,
        tertiary = tertiaryColor,
        onTertiary = if (isDark) Color.Black else Color.White,
        tertiaryContainer = tertiaryContainerColor,
        onTertiaryContainer = onTertiaryContainerColor,
        errorContainer = errorContainerColor,
        onErrorContainer = onErrorContainerColor,
        surfaceVariant = surfaceVariantColor,
        onSurfaceVariant = onSurfaceVariantColor,
        outline = outlineColor,
        outlineVariant = outlineVariantColor,
        inverseSurface = inverseSurfaceColor,
        inverseOnSurface = inverseOnSurfaceColor,
        inversePrimary = primary.adjust(lightnessOverride = if (isDark) 0.4f else 0.8f),
        surfaceTint = primary,
        scrim = Color.Black.copy(alpha = 0.32f),
        surfaceContainerLowest = surfaceContainerLowestColor,
        surfaceContainerLow = surfaceContainerLowColor,
        surfaceContainer = surfaceContainerColor,
        surfaceContainerHigh = surfaceContainerHighColor,
        surfaceContainerHighest = surfaceContainerHighestColor
    )
}

// --- Color Schemes ---

// internal (not private): reused by FlowGlanceTheme as the pre-Android-12 widget fallback palette
internal val LightColorScheme = lightColorScheme(
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

// internal (not private): reused by FlowGlanceTheme as the pre-Android-12 widget fallback palette
internal val DarkColorScheme = darkColorScheme(
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

private val MonochromeColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF777777),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = Color(0xFFE0E0E0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF888888),
    scrim = Color(0xCC000000)
)

private fun customThemeColorScheme(colors: CustomThemeColors): ColorScheme {
    return darkColorScheme(
        primary = Color(colors.primary),
        onPrimary = Color(colors.onPrimary),
        secondary = Color(colors.secondary),
        onSecondary = Color(colors.onSecondary),
        tertiary = Color(colors.tertiary),
        onTertiary = Color(colors.onTertiary),
        background = Color(colors.background),
        onBackground = Color(colors.onBackground),
        surface = Color(colors.surface),
        onSurface = Color(colors.onSurface),
        surfaceVariant = Color(colors.surfaceVariant),
        onSurfaceVariant = Color(colors.onSurfaceVariant),
        error = Color(colors.error),
        onError = Color(colors.onError),
        outline = Color(colors.outline),
        scrim = Color(colors.scrim)
    ).copy(
        primaryContainer = Color(colors.colorOf(CustomColorRole.PRIMARY_CONTAINER)),
        onPrimaryContainer = Color(colors.colorOf(CustomColorRole.ON_PRIMARY_CONTAINER)),
        inversePrimary = Color(colors.colorOf(CustomColorRole.INVERSE_PRIMARY)),
        secondaryContainer = Color(colors.colorOf(CustomColorRole.SECONDARY_CONTAINER)),
        onSecondaryContainer = Color(colors.colorOf(CustomColorRole.ON_SECONDARY_CONTAINER)),
        tertiaryContainer = Color(colors.colorOf(CustomColorRole.TERTIARY_CONTAINER)),
        onTertiaryContainer = Color(colors.colorOf(CustomColorRole.ON_TERTIARY_CONTAINER)),
        surfaceTint = Color(colors.colorOf(CustomColorRole.SURFACE_TINT)),
        inverseSurface = Color(colors.colorOf(CustomColorRole.INVERSE_SURFACE)),
        inverseOnSurface = Color(colors.colorOf(CustomColorRole.INVERSE_ON_SURFACE)),
        errorContainer = Color(colors.colorOf(CustomColorRole.ERROR_CONTAINER)),
        onErrorContainer = Color(colors.colorOf(CustomColorRole.ON_ERROR_CONTAINER)),
        outlineVariant = Color(colors.colorOf(CustomColorRole.OUTLINE_VARIANT)),
        surfaceBright = Color(colors.colorOf(CustomColorRole.SURFACE_BRIGHT)),
        surfaceDim = Color(colors.colorOf(CustomColorRole.SURFACE_DIM)),
        surfaceContainerLowest = Color(colors.colorOf(CustomColorRole.SURFACE_CONTAINER_LOWEST)),
        surfaceContainerLow = Color(colors.colorOf(CustomColorRole.SURFACE_CONTAINER_LOW)),
        surfaceContainer = Color(colors.colorOf(CustomColorRole.SURFACE_CONTAINER)),
        surfaceContainerHigh = Color(colors.colorOf(CustomColorRole.SURFACE_CONTAINER_HIGH)),
        surfaceContainerHighest = Color(colors.colorOf(CustomColorRole.SURFACE_CONTAINER_HIGHEST))
    )
}

private fun ColorScheme.withVariant(
    variant: ThemeVariant,
    preserveLightSurfaces: Boolean = false,
    preserveDarkSurfaces: Boolean = false
): ColorScheme {
    val isDark = variant != ThemeVariant.LIGHT
    val isAmoled = variant == ThemeVariant.AMOLED
    if (variant == ThemeVariant.LIGHT && preserveLightSurfaces) return complete(isDark = false)
    if (variant == ThemeVariant.DARK && preserveDarkSurfaces) return complete(isDark = true)
    val base = if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = if (ColorUtils.calculateLuminance(primary.toArgb()) > 0.45) Color.Black else Color.White,
            secondary = secondary,
            onSecondary = if (ColorUtils.calculateLuminance(secondary.toArgb()) > 0.45) Color.Black else Color.White,
            tertiary = tertiary,
            onTertiary = if (ColorUtils.calculateLuminance(tertiary.toArgb()) > 0.45) Color.Black else Color.White,
            background = if (isAmoled) Color.Black else primary.adjust(saturationFactor = 0.12f, lightnessOverride = 0.055f),
            onBackground = Color(0xFFE6E1E5),
            surface = if (isAmoled) Color.Black else primary.adjust(saturationFactor = 0.10f, lightnessOverride = 0.08f),
            onSurface = Color(0xFFE6E1E5),
            error = error,
            onError = onError
        )
    } else {
        lightColorScheme(
            primary = primary.adjust(lightnessOverride = 0.40f),
            onPrimary = Color.White,
            secondary = secondary.adjust(lightnessOverride = 0.40f),
            onSecondary = Color.White,
            tertiary = tertiary.adjust(lightnessOverride = 0.40f),
            onTertiary = Color.White,
            background = if (preserveLightSurfaces) background else primary.adjust(
                saturationFactor = 0.30f,
                lightnessOverride = 0.96f
            ),
            onBackground = Color(0xFF1D1B20),
            surface = if (preserveLightSurfaces) surface else primary.adjust(
                saturationFactor = 0.24f,
                lightnessOverride = 0.92f
            ),
            onSurface = Color(0xFF1D1B20),
            error = error.adjust(lightnessOverride = 0.40f),
            onError = Color.White
        )
    }
    return base.complete(isDark = isDark, isOled = isAmoled)
}

@Composable
fun FlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeVariant: ThemeVariant = ThemeVariant.DARK,
    customThemePalettes: CustomThemePalettes = CustomThemePalettes(),
    systemLightThemeMode: ThemeMode = ThemeMode.DARK,
    systemDarkThemeMode: ThemeMode = ThemeMode.DARK,
    systemDarkThemeVariant: ThemeVariant = ThemeVariant.DARK,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val colorScheme = resolveFlowColorScheme(
        context = context,
        isSystemDark = darkTheme,
        themeMode = themeMode,
        themeVariant = themeVariant,
        customThemePalettes = customThemePalettes,
        systemLightThemeMode = systemLightThemeMode,
        systemDarkThemeMode = systemDarkThemeMode,
        systemDarkThemeVariant = systemDarkThemeVariant
    )

    val extendedColors = ExtendedColors(
        textSecondary = colorScheme.onSurfaceVariant,
        border = colorScheme.outlineVariant,
        success = colorScheme.tertiary
    )

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Non-composable resolution of the app's active color scheme. Single source of truth
 * shared by [FlowTheme] and the home-screen widgets, so widgets always render in the
 * exact palette the user selected in-app (including custom and Material You themes).
 */
fun resolveFlowColorScheme(
    context: Context,
    isSystemDark: Boolean,
    themeMode: ThemeMode,
    themeVariant: ThemeVariant,
    customThemePalettes: CustomThemePalettes,
    systemLightThemeMode: ThemeMode,
    systemDarkThemeMode: ThemeMode,
    systemDarkThemeVariant: ThemeVariant
): ColorScheme {
    val effectiveThemeMode = themeMode.resolveSystemDefault(
        isSystemDark = isSystemDark,
        systemLightThemeMode = systemLightThemeMode,
        systemDarkThemeMode = systemDarkThemeMode
    )
    val effectiveVariant = if (themeMode == ThemeMode.SYSTEM) {
        if (isSystemDark) systemDarkThemeVariant else ThemeVariant.LIGHT
    } else {
        themeVariant
    }
    val baseColorScheme = when (effectiveThemeMode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> when (effectiveVariant) {
            ThemeVariant.LIGHT -> LightColorScheme
            ThemeVariant.DARK -> DarkColorScheme
            ThemeVariant.AMOLED -> OLEDColorScheme
        }
        ThemeMode.OLED -> OLEDColorScheme
        ThemeMode.SYSTEM -> if (isSystemDark) DarkColorScheme else LightColorScheme
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
        ThemeMode.MONOCHROME -> MonochromeColorScheme
        ThemeMode.CUSTOM -> customThemeColorScheme(customThemePalettes.forVariant(effectiveVariant))
        ThemeMode.MATERIAL_YOU -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (effectiveVariant == ThemeVariant.LIGHT) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
            } else {
                if (effectiveVariant == ThemeVariant.LIGHT) LightColorScheme else DarkColorScheme
            }
        }
    }
    return if (effectiveThemeMode == ThemeMode.CUSTOM) {
        baseColorScheme
    } else {
        baseColorScheme.withVariant(
            variant = effectiveVariant,
            preserveLightSurfaces = effectiveThemeMode in setOf(
                ThemeMode.DARK,
                ThemeMode.MINT_LIGHT,
                ThemeMode.ROSE_LIGHT,
                ThemeMode.SKY_LIGHT,
                ThemeMode.CREAM_LIGHT
            ),
            preserveDarkSurfaces = effectiveThemeMode !in setOf(
                ThemeMode.LIGHT,
                ThemeMode.MINT_LIGHT,
                ThemeMode.ROSE_LIGHT,
                ThemeMode.SKY_LIGHT,
                ThemeMode.CREAM_LIGHT
            )
        )
    }
}

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current