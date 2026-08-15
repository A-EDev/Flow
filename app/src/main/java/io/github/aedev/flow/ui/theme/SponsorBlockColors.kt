package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Default segment colours for SponsorBlock categories.
 *
 * These are fixed convention colours rather than theme-scheme roles — they match what SponsorBlock
 * uses everywhere else, so a segment stays recognisable to users who already know the palette. They
 * live in the theme layer rather than inline at the draw site because the user can override any of
 * them per category in settings, and because there is more than one place that paints a segment.
 */
val SponsorBlockSponsor = Color(0xFF00D100)
val SponsorBlockSelfPromo = Color(0xFFFFFF00)
val SponsorBlockInteraction = Color(0xFFFF00FF)
val SponsorBlockIntroOutro = Color(0xFF00FFFF)
val SponsorBlockMusicOffTopic = Color(0xFFFF8000)

/** Opacity segments are drawn at so the progress track stays readable underneath them. */
const val SPONSOR_BLOCK_SEGMENT_ALPHA = 0.78f

/**
 * Default colour for [category], used when the user has not chosen a custom one.
 * Unknown categories fall back to the sponsor colour, matching the previous behaviour.
 */
fun defaultSponsorBlockColor(category: String): Color =
    when (category) {
        "sponsor" -> SponsorBlockSponsor
        "selfpromo" -> SponsorBlockSelfPromo
        "interaction" -> SponsorBlockInteraction
        "intro", "outro" -> SponsorBlockIntroOutro
        "music_offtopic" -> SponsorBlockMusicOffTopic
        else -> SponsorBlockSponsor
    }
