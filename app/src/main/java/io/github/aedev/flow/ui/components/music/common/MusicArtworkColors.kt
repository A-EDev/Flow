/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.common

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.aedev.flow.ui.components.musicplayer.MusicPaletteColors
import io.github.aedev.flow.ui.components.musicplayer.rememberMusicPalette
import io.github.aedev.flow.ui.theme.ensureContrastOn
import io.github.aedev.flow.ui.theme.tone
import io.github.aedev.flow.ui.theme.withTone

private const val TEXT_CONTRAST = 4.5f
private const val GRAPHIC_CONTRAST = 3f
private const val TONAL_BLEND = 0.14f

/**
 * Container and content colours for one item, keyed to its own artwork: the cover's vibrant hue at
 * the theme's container tones, so a highlighted row or a hero card reads as that record without
 * leaving the light or dark scheme it sits in.
 */
@Immutable
data class MusicArtworkColors(
    val container: Color,
    val onContainer: Color,
    val tonalContainer: Color,
    val accent: Color,
    val onAccent: Color,
)

@Composable
fun rememberMusicArtworkColors(thumbnailUrl: String?): MusicArtworkColors {
    val palette = rememberMusicPalette(thumbnailUrl, animated = false)
    val scheme = MaterialTheme.colorScheme
    return remember(palette, scheme) { artworkColors(palette, scheme) }
}

internal fun artworkColors(
    palette: MusicPaletteColors,
    scheme: ColorScheme,
): MusicArtworkColors {
    val seed = palette.accent
    val container = seed.withTone(scheme.primaryContainer.tone())
    val onContainer = ensureContrastOn(seed.withTone(scheme.onPrimaryContainer.tone()), container, TEXT_CONTRAST)
    return MusicArtworkColors(
        container = container,
        onContainer = onContainer,
        tonalContainer = lerp(container, onContainer, TONAL_BLEND),
        accent = ensureContrastOn(seed.withTone(scheme.primary.tone()), container, GRAPHIC_CONTRAST),
        onAccent = seed.withTone(scheme.onPrimary.tone()),
    )
}
