package io.github.aedev.flow.ui.components.musicplayer

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle

/**
 * DEFAULT background style follows the app theme; the artwork-based styles hand the whole player
 * over to a palette-derived dark scheme so every component keeps using MaterialTheme tokens.
 */
@Composable
fun rememberMusicPlayerColorScheme(
    palette: MusicPaletteColors,
    style: MusicPlayerBackgroundStyle,
): ColorScheme {
    val appScheme = MaterialTheme.colorScheme
    return remember(palette, style, appScheme) {
        if (style == MusicPlayerBackgroundStyle.DEFAULT) {
            appScheme
        } else {
            paletteColorScheme(palette, appScheme.error)
        }
    }
}

/**
 * Maps the raw artwork swatches onto M3 roles through CIELAB tone (≈ HCT tone): hue and chroma
 * come from the cover, but every role gets a fixed tone re-clamped against the surface, so
 * readability no longer depends on how light or dark the extracted swatches happen to be.
 */
private fun paletteColorScheme(
    palette: MusicPaletteColors,
    appError: Color,
): ColorScheme {
    val surface = palette.base.let { if (it.tone() > 30.0) it.withTone(26.0) else it }
    val ink = Color.White
    val accentSeed = palette.accent
    val accent = ensureContrastOn(accentSeed.withTone(80.0), surface, minRatio = 4.5f)
    val onAccent = accentSeed.withTone(20.0)
    // Tone 75 keeps the destructive tint clearly red yet darker than the app scheme's error,
    // and never dimmer than the white-ish body text sitting next to it.
    val error = ensureContrastOn(appError.withTone(75.0), surface, minRatio = 4.5f)
    return darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSeed.withTone(30.0),
        onPrimaryContainer = accentSeed.withTone(90.0),
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = lerp(surface, ink, 0.16f),
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accentSeed.withTone(35.0),
        onTertiaryContainer = accentSeed.withTone(92.0),
        surface = surface,
        onSurface = ink,
        background = surface,
        onBackground = ink,
        surfaceVariant = lerp(surface, ink, 0.22f),
        onSurfaceVariant = ink.copy(alpha = 0.75f),
        surfaceContainerLowest = lerp(surface, Color.Black, 0.30f),
        surfaceContainerLow = lerp(surface, ink, 0.05f),
        surfaceContainer = lerp(surface, ink, 0.08f),
        surfaceContainerHigh = lerp(surface, ink, 0.11f),
        surfaceContainerHighest = lerp(surface, ink, 0.15f),
        outline = ink.copy(alpha = 0.35f),
        outlineVariant = ink.copy(alpha = 0.18f),
        surfaceTint = accent,
        error = error,
        onError = appError.withTone(15.0),
        errorContainer = appError.withTone(30.0),
        onErrorContainer = appError.withTone(90.0),
        inverseSurface = ink,
        inverseOnSurface = surface,
        inversePrimary = onAccent,
    )
}

private fun Color.tone(): Double {
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(toArgb(), lab)
    return lab[0]
}

private fun Color.withTone(tone: Double): Color {
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(toArgb(), lab)
    return Color(ColorUtils.LABToColor(tone.coerceIn(0.0, 100.0), lab[1], lab[2]))
}

/** Lightens [color] until it clears [minRatio] against the dark [surface]. */
private fun ensureContrastOn(
    color: Color,
    surface: Color,
    minRatio: Float,
): Color {
    var candidate = color
    var tone = candidate.tone()
    while (contrastRatio(candidate, surface) < minRatio && tone < 98.0) {
        tone += 4.0
        candidate = candidate.withTone(tone)
    }
    return candidate
}

/** WCAG contrast ratio between two opaque colors. */
internal fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

/**
 * The accent only if it clears WCAG 3:1 against [container]; otherwise a guaranteed-contrast ink.
 */
internal fun readableAccentOn(
    container: Color,
    accent: Color,
): Color =
    when {
        contrastRatio(accent, container) >= 3f -> accent
        container.luminance() > 0.5f -> PaletteInkDark
        else -> Color.White
    }
