package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Scrims laid over artwork and video stills. Artwork is arbitrary imagery rather than a themed surface, so
 * these stay fixed in both themes exactly like [PlayerScrim] — the point is legible text over an
 * unknown photograph, not a tonal relationship with the page.
 */
val ArtworkScrim = Color.Black

/** Text and icons resting on [ArtworkScrim]. */
val ArtworkScrimContent = Color.White

/** Secondary text on [ArtworkScrim]. */
val ArtworkScrimContentMuted = ArtworkScrimContent.copy(alpha = 0.78f)

/** Now-playing overlay on a list thumbnail. */
val ArtworkScrimNowPlaying = ArtworkScrim.copy(alpha = 0.46f)

/** Active and selected states on a thumbnail. */
val ArtworkScrimActive = ArtworkScrim.copy(alpha = 0.5f)

/** Album-index badge drawn over a thumbnail. */
val ArtworkScrimIndex = ArtworkScrim.copy(alpha = 0.6f)

/** Translucent affordance resting directly on artwork. */
val ArtworkScrimAffordance = ArtworkScrimContent.copy(alpha = 0.1f)

/** Stops for the bottom-up gradient that keeps a card's caption legible over its artwork. */
fun artworkScrim(alpha: Float): Color = ArtworkScrim.copy(alpha = alpha)

/** Muted content on artwork, at a caller-chosen strength. */
fun artworkScrimContent(alpha: Float): Color = ArtworkScrimContent.copy(alpha = alpha)
