package io.github.aedev.flow.ui.components.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import io.github.aedev.flow.ui.theme.ensureContrastOn
import io.github.aedev.flow.ui.theme.tone
import io.github.aedev.flow.ui.theme.withTone

private const val CONTAINER_TONE_DARK = 16.0
private const val CONTAINER_TONE_LIGHT = 92.0
private const val CONTENT_TONE_DARK = 92.0
private const val CONTENT_TONE_LIGHT = 20.0
private const val ACCENT_TONE_DARK = 80.0
private const val ACCENT_TONE_LIGHT = 40.0
private const val MID_TONE = 50.0
private const val BODY_CONTRAST = 4.5f

/** A container tinted by a piece of artwork, and the ink that stays readable on it. */
@Immutable
data class MediaArtworkTint(
    val container: Color,
    val onContainer: Color,
    val accent: Color,
)

/**
 * Tints a surface with the colour of [thumbnailUrl].
 *
 * Hue and chroma come from the artwork, but the tones are fixed against the app's own surface and
 * then re-clamped for contrast, so a washed-out or blinding thumbnail cannot decide how readable
 * the text on it is. Falls back to the theme's own container colours while the image loads, and
 * for anything with no artwork at all.
 */
@Composable
fun rememberMediaArtworkTint(thumbnailUrl: String?): MediaArtworkTint {
    val palette = rememberMediaPalette(thumbnailUrl, animated = false)
    val scheme = MaterialTheme.colorScheme
    val hasArtwork = !thumbnailUrl.isNullOrBlank() && palette.accent.isSpecified
    return remember(palette, scheme, hasArtwork) {
        if (!hasArtwork) {
            return@remember MediaArtworkTint(
                container = scheme.surfaceContainerHigh,
                onContainer = scheme.onSurface,
                accent = scheme.primary,
            )
        }
        val dark = scheme.surface.tone() < MID_TONE
        val seed = palette.accent
        val container = seed.withTone(if (dark) CONTAINER_TONE_DARK else CONTAINER_TONE_LIGHT)
        val onContainer =
            ensureContrastOn(
                color = seed.withTone(if (dark) CONTENT_TONE_DARK else CONTENT_TONE_LIGHT),
                surface = container,
                minRatio = BODY_CONTRAST,
            )
        val accent =
            ensureContrastOn(
                color = seed.withTone(if (dark) ACCENT_TONE_DARK else ACCENT_TONE_LIGHT),
                surface = container,
                minRatio = BODY_CONTRAST,
            )
        MediaArtworkTint(container = container, onContainer = onContainer, accent = accent)
    }
}
