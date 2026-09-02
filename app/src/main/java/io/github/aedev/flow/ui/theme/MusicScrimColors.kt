package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Scrims laid over album artwork. Artwork is arbitrary imagery rather than a themed surface, so
 * these stay fixed in both themes exactly like [PlayerScrim] — the point is legible text over an
 * unknown photograph, not a tonal relationship with the page.
 */
val MusicScrim = Color.Black

/** Text and icons resting on [MusicScrim]. */
val MusicScrimContent = Color.White

/** Secondary text on [MusicScrim]. */
val MusicScrimContentMuted = MusicScrimContent.copy(alpha = 0.78f)

/** Now-playing overlay on a list thumbnail. */
val MusicScrimNowPlaying = MusicScrim.copy(alpha = 0.46f)

/** Active and selected states on a thumbnail. */
val MusicScrimThumbnailActive = MusicScrim.copy(alpha = 0.5f)

/** Album-index badge drawn over a thumbnail. */
val MusicScrimThumbnailIndex = MusicScrim.copy(alpha = 0.6f)

/** Translucent affordance resting directly on artwork. */
val MusicScrimAffordance = MusicScrimContent.copy(alpha = 0.1f)

/** Stops for the bottom-up gradient that keeps a card's caption legible over its artwork. */
fun musicScrim(alpha: Float): Color = MusicScrim.copy(alpha = alpha)

/** Muted content on artwork, at a caller-chosen strength. */
fun musicScrimContent(alpha: Float): Color = MusicScrimContent.copy(alpha = alpha)
