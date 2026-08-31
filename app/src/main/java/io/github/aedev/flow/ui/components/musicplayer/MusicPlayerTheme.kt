package io.github.aedev.flow.ui.components.musicplayer

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
            paletteColorScheme(palette)
        }
    }
}

private fun paletteColorScheme(palette: MusicPaletteColors): ColorScheme {
    val base = palette.base
    val accent = palette.accent
    val onBase = palette.onBase
    val onAccent = if (accent.luminance() < 0.55f) Color.White else PaletteInkDark
    return darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = lerp(base, accent, 0.28f),
        onPrimaryContainer = onBase,
        secondaryContainer = lerp(base, onBase, 0.12f),
        onSecondaryContainer = onBase,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = lerp(base, accent, 0.45f),
        onTertiaryContainer = onBase,
        surface = base,
        onSurface = onBase,
        background = base,
        onBackground = onBase,
        surfaceVariant = lerp(base, onBase, 0.08f),
        onSurfaceVariant = onBase.copy(alpha = 0.72f),
        surfaceContainerLowest = lerp(base, Color.Black, 0.25f),
        surfaceContainerLow = lerp(base, onBase, 0.04f),
        surfaceContainer = lerp(base, onBase, 0.05f),
        surfaceContainerHigh = lerp(base, onBase, 0.07f),
        surfaceContainerHighest = lerp(base, onBase, 0.10f),
        outline = onBase.copy(alpha = 0.32f),
        outlineVariant = onBase.copy(alpha = 0.16f),
        surfaceTint = accent,
    )
}
