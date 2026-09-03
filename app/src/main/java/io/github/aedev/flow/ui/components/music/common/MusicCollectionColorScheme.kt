/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.common

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.aedev.flow.ui.components.musicplayer.MusicPaletteColors
import io.github.aedev.flow.ui.components.musicplayer.rememberMusicPalette
import io.github.aedev.flow.ui.theme.ensureContrastOn
import io.github.aedev.flow.ui.theme.tone
import io.github.aedev.flow.ui.theme.withTone

private const val SURFACE_TINT = 0.45f
private const val CONTAINER_TINT = 0.55f
private const val TEXT_CONTRAST = 4.5f
private const val GRAPHIC_CONTRAST = 3f

/**
 * The app scheme re-seeded from a collection's artwork: the cover's hue and chroma, the theme's
 * tones. Light themes stay light and dark themes stay dark, so a detail page reads as the same
 * app with the record's colour in it rather than as a second theme.
 */
@Composable
fun rememberMusicCollectionColorScheme(thumbnailUrl: String?): ColorScheme {
    val palette = rememberMusicPalette(thumbnailUrl, animated = false)
    val appScheme = MaterialTheme.colorScheme
    return remember(palette, appScheme) { collectionColorScheme(palette, appScheme) }
}

internal fun collectionColorScheme(
    palette: MusicPaletteColors,
    app: ColorScheme,
): ColorScheme {
    val base = palette.base
    val accent = palette.accent
    val surface = app.surface.tintedBy(base, SURFACE_TINT)
    val primary = ensureContrastOn(accent.withTone(app.primary.tone()), surface, TEXT_CONTRAST)
    return app.copy(
        primary = primary,
        onPrimary = accent.withTone(app.onPrimary.tone()),
        primaryContainer = accent.withTone(app.primaryContainer.tone()),
        onPrimaryContainer = accent.withTone(app.onPrimaryContainer.tone()),
        inversePrimary = accent.withTone(app.inversePrimary.tone()),
        secondary = app.secondary.tintedBy(base, CONTAINER_TINT),
        onSecondary = app.onSecondary.tintedBy(base, CONTAINER_TINT),
        secondaryContainer = app.secondaryContainer.tintedBy(base, CONTAINER_TINT),
        onSecondaryContainer = app.onSecondaryContainer.tintedBy(base, CONTAINER_TINT),
        tertiary = ensureContrastOn(accent.withTone(app.tertiary.tone()), surface, GRAPHIC_CONTRAST),
        onTertiary = accent.withTone(app.onTertiary.tone()),
        tertiaryContainer = accent.withTone(app.tertiaryContainer.tone()),
        onTertiaryContainer = accent.withTone(app.onTertiaryContainer.tone()),
        background = surface,
        surface = surface,
        surfaceVariant = app.surfaceVariant.tintedBy(base, SURFACE_TINT),
        surfaceContainerLowest = app.surfaceContainerLowest.tintedBy(base, SURFACE_TINT),
        surfaceContainerLow = app.surfaceContainerLow.tintedBy(base, SURFACE_TINT),
        surfaceContainer = app.surfaceContainer.tintedBy(base, SURFACE_TINT),
        surfaceContainerHigh = app.surfaceContainerHigh.tintedBy(base, SURFACE_TINT),
        surfaceContainerHighest = app.surfaceContainerHighest.tintedBy(base, SURFACE_TINT),
        surfaceTint = primary,
    )
}

private fun Color.tintedBy(
    seed: Color,
    amount: Float,
): Color = lerp(this, seed.withTone(tone()), amount)
