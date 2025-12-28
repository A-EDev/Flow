package com.flow.youtube.ui.theme

import androidx.compose.ui.graphics.Color

// YouTube Brand Colors
val YouTubeRed = Color(0xFFFF0000)
val YouTubeDark = Color(0xFF0F0F0F)
val YouTubeGray = Color(0xFF282828)

// Dark Theme Colors
val Black = Color(0xFF000000)
val DarkBackground = Color(0xFF0F0F0F)
val DarkSurface = Color(0xFF212121)
val DarkSurfaceVariant = Color(0xFF3A3A3A)

// Light Theme Colors
val White = Color(0xFFFFFFFF)
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF9F9F9)
val LightSurfaceVariant = Color(0xFFEEEEEE)

// Text Colors
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFAAAAAA)
val TextTertiary = Color(0xFF717171)

// Accent Colors
val SuccessColor = Color(0xFF4CAF50)
val ErrorColor = Color(0xFFF44336)
val Warning = Color(0xFFFF9800)
val Info = Color(0xFF2196F3)

// Shimmer Colors
val ShimmerColorShades = listOf(
    Color(0xFF3A3A3A),
    Color(0xFF4A4A4A),
    Color(0xFF3A3A3A)
)

// Light Theme Color Scheme
object LightThemeColors {
    val Primary: Color = YouTubeRed
    val OnPrimary: Color = White
    val Secondary: Color = Color(0xFF606060)
    val OnSecondary: Color = White
    val Background: Color = LightBackground
    val Surface: Color = LightSurface
    val Text: Color = Color(0xFF0F0F0F)
    val TextSecondary: Color = Color(0xFF606060)
    val Border: Color = Color(0xFFE0E0E0)
    val Success: Color = SuccessColor
    val Error: Color = ErrorColor
}

// Dark Theme Color Scheme
object DarkThemeColors {
    val Primary: Color = YouTubeRed
    val OnPrimary: Color = White
    val Secondary: Color = Color(0xFFAAAAAA)
    val OnSecondary: Color = Black
    val Background: Color = DarkBackground
    val Surface: Color = DarkSurface
    val Text: Color = TextPrimary
    val TextSecondary: Color = Color(0xFFAAAAAA)
    val Border: Color = Color(0xFF3A3A3A)
    val Success: Color = SuccessColor
    val Error: Color = ErrorColor
}

// OLED Theme Color Scheme
object OLEDThemeColors {
    val Primary: Color = YouTubeRed
    val OnPrimary: Color = White
    val Secondary: Color = Color(0xFFAAAAAA)
    val OnSecondary: Color = Black
    val Background: Color = Black
    val Surface: Color = Color(0xFF121212)
    val Text: Color = TextPrimary
    val TextSecondary: Color = Color(0xFFAAAAAA)
    val Border: Color = Color(0xFF2A2A2A)
    val Success: Color = SuccessColor
    val Error: Color = ErrorColor
}

// Ocean Blue Theme Color Scheme
object OceanBlueThemeColors {
    val Primary: Color = Color(0xFF006994) // Deep ocean blue
    val OnPrimary: Color = White
    val Secondary: Color = Color(0xFF4FC3F7) // Light blue
    val OnSecondary: Color = Black
    val Background: Color = Color(0xFF0A1929) // Deep sea blue
    val Surface: Color = Color(0xFF1A2332) // Ocean surface
    val Text: Color = Color(0xFFE3F2FD) // Light blue text
    val TextSecondary: Color = Color(0xFF90CAF9) // Lighter blue
    val Border: Color = Color(0xFF2A3F5F)
    val Success: Color = Color(0xFF26C6DA)
    val Error: Color = Color(0xFFEF5350)
}

// Forest Green Theme Color Scheme
object ForestGreenThemeColors {
    val Primary: Color = Color(0xFF2E7D32) // Forest green
    val OnPrimary: Color = White
    val Secondary: Color = Color(0xFF66BB6A) // Light green
    val OnSecondary: Color = Black
    val Background: Color = Color(0xFF0D1F12) // Deep forest
    val Surface: Color = Color(0xFF1B2D1F) // Forest floor
    val Text: Color = Color(0xFFE8F5E9) // Light mint text
    val TextSecondary: Color = Color(0xFFA5D6A7) // Lighter green
    val Border: Color = Color(0xFF2F4C33)
    val Success: Color = Color(0xFF4CAF50)
    val Error: Color = Color(0xFFEF5350)
}

// Sunset Orange Theme Color Scheme
object SunsetOrangeThemeColors {
    val Primary: Color = Color(0xFFFF6F00) // Vibrant orange
    val OnPrimary: Color = White
    val Secondary: Color = Color(0xFFFFAB40) // Light orange
    val OnSecondary: Color = Black
    val Background: Color = Color(0xFF1F0F08) // Deep sunset
    val Surface: Color = Color(0xFF2D1810) // Dusk
    val Text: Color = Color(0xFFFFECB3) // Warm light text
    val TextSecondary: Color = Color(0xFFFFCC80) // Lighter orange
    val Border: Color = Color(0xFF4A2C1A)
    val Success: Color = Color(0xFFFFB74D)
    val Error: Color = Color(0xFFEF5350)
}

// Purple Nebula Theme Color Scheme
object PurpleNebulaThemeColors {
    val Primary: Color = Color(0xFF7B1FA2) // Deep purple
    val OnPrimary: Color = White
    val Secondary: Color = Color(0xFFBA68C8) // Light purple
    val OnSecondary: Color = Black
    val Background: Color = Color(0xFF1A0C26) // Deep space purple
    val Surface: Color = Color(0xFF2A1A3D) // Nebula
    val Text: Color = Color(0xFFF3E5F5) // Light lavender text
    val TextSecondary: Color = Color(0xFFCE93D8) // Lighter purple
    val Border: Color = Color(0xFF3D2957)
    val Success: Color = Color(0xFFAB47BC)
    val Error: Color = Color(0xFFEF5350)
}

// Midnight Black Theme Color Scheme
object MidnightBlackThemeColors {
    val Primary: Color = Color(0xFF00BCD4) // Cyan accent
    val OnPrimary: Color = Black
    val Secondary: Color = Color(0xFF64B5F6) // Light blue
    val OnSecondary: Color = Black
    val Background: Color = Color(0xFF000000) // Pure black
    val Surface: Color = Color(0xFF0A0A0A) // Almost black
    val Text: Color = Color(0xFFFFFFFF) // Pure white
    val TextSecondary: Color = Color(0xFFB0BEC5) // Gray blue
    val Border: Color = Color(0xFF1A1A1A)
    val Success: Color = Color(0xFF00E676)
    val Error: Color = Color(0xFFFF5252)
}

// Rose Gold Theme Color Scheme
object RoseGoldThemeColors {
    val Primary: Color = Color(0xFFE91E63) // Rose pink
    val OnPrimary: Color = White
    val Secondary: Color = Color(0xFFFF6090) // Light rose
    val OnSecondary: Color = Black
    val Background: Color = Color(0xFF1A0D12) // Deep rose dark
    val Surface: Color = Color(0xFF2D1821) // Rose surface
    val Text: Color = Color(0xFFFCE4EC) // Light pink text
    val TextSecondary: Color = Color(0xFFF48FB1) // Rose pink
    val Border: Color = Color(0xFF4A2535)
    val Success: Color = Color(0xFFEC407A)
    val Error: Color = Color(0xFFEF5350)
}

// Arctic Ice Theme Color Scheme
object ArcticIceThemeColors {
    val Primary: Color = Color(0xFF00BCD4) // Ice cyan
    val OnPrimary: Color = Black
    val Secondary: Color = Color(0xFF80DEEA) // Light ice
    val OnSecondary: Color = Black
    val Background: Color = Color(0xFF0E1821) // Deep ice
    val Surface: Color = Color(0xFF1A2830) // Ice surface
    val Text: Color = Color(0xFFE0F7FA) // Ice white
    val TextSecondary: Color = Color(0xFF80DEEA) // Light cyan
    val Border: Color = Color(0xFF2A3F4A)
    val Success: Color = Color(0xFF26C6DA)
    val Error: Color = Color(0xFFEF5350)
}

// Crimson Red Theme Color Scheme
object CrimsonRedThemeColors {
    val Primary: Color = Color(0xFFDC143C) // Crimson red
    val OnPrimary: Color = White
    val Secondary: Color = Color(0xFFFF4757) // Light red
    val OnSecondary: Color = White
    val Background: Color = Color(0xFF1A0A0A) // Deep crimson
    val Surface: Color = Color(0xFF2D1414) // Dark red surface
    val Text: Color = Color(0xFFFFEBEE) // Light red text
    val TextSecondary: Color = Color(0xFFEF9A9A) // Light crimson
    val Border: Color = Color(0xFF4A1F1F)
    val Success: Color = Color(0xFFEF5350)
    val Error: Color = Color(0xFFFF1744)
}

// Royal Gold Theme (Premium/Luxury)
object RoyalGoldThemeColors {
    val Primary: Color = Color(0xFFFFD700) // Gold
    val OnPrimary: Color = Black
    val Secondary: Color = Color(0xFFC5A000) // Darker Gold
    val OnSecondary: Color = Black
    val Background: Color = Color(0xFF050505) // Rich Black
    val Surface: Color = Color(0xFF141414) // Soft Black
    val Text: Color = Color(0xFFFFF8E1) // Off-white gold tint
    val TextSecondary: Color = Color(0xFFBDB76B) // Khaki gold
    val Border: Color = Color(0xFF333333)
    val Success: Color = Color(0xFFCDDC39)
    val Error: Color = Color(0xFFD32F2F)
}

// Nordic Horizon Theme (Cool/Muted)
object NordicHorizonThemeColors {
    val Primary: Color = Color(0xFF88C0D0) // Frost Blue
    val OnPrimary: Color = Black
    val Secondary: Color = Color(0xFF81A1C1) // Ocean Blue
    val OnSecondary: Color = Black
    val Background: Color = Color(0xFF242933) // Dark Snow
    val Surface: Color = Color(0xFF2E3440) // Polar Night
    val Text: Color = Color(0xFFECEFF4) // Snow Storm
    val TextSecondary: Color = Color(0xFFD8DEE9) // Gray White
    val Border: Color = Color(0xFF434C5E)
    val Success: Color = Color(0xFFA3BE8C)
    val Error: Color = Color(0xFFBF616A)
}

// Espresso Theme (Warm/Cozy)
object EspressoThemeColors {
    val Primary: Color = Color(0xFFD7CCC8) // Latte
    val OnPrimary: Color = Black
    val Secondary: Color = Color(0xFFA1887F) // Light Brown
    val OnSecondary: Color = White
    val Background: Color = Color(0xFF181210) // Dark Coffee
    val Surface: Color = Color(0xFF241A17) // Espresso
    val Text: Color = Color(0xFFEFEBE9) // Foam White
    val TextSecondary: Color = Color(0xFFBCAAA4) // Pale Brown
    val Border: Color = Color(0xFF3E2723)
    val Success: Color = Color(0xFF8D6E63)
    val Error: Color = Color(0xFFD84315)
}

// Gunmetal Theme (Industrial/Sleek)
object GunmetalThemeColors {
    val Primary: Color = Color(0xFF78909C) // Blue Grey
    val OnPrimary: Color = Black
    val Secondary: Color = Color(0xFF546E7A) // Slate
    val OnSecondary: Color = White
    val Background: Color = Color(0xFF0F1216) // Deep Metal
    val Surface: Color = Color(0xFF1A1F26) // Gunmetal Surface
    val Text: Color = Color(0xFFECEFF1) // Steel White
    val TextSecondary: Color = Color(0xFFCFD8DC) // Silver
    val Border: Color = Color(0xFF263238)
    val Success: Color = Color(0xFF26A69A)
    val Error: Color = Color(0xFFEF5350)
}