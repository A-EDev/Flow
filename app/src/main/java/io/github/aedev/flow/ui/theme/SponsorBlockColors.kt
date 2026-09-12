package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.SponsorBlockCategories

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
val SponsorBlockFiller = Color(0xFF7300FF)
val SponsorBlockPreview = Color(0xFF008FD6)
val SponsorBlockExclusiveAccess = Color(0xFF008A5C)

/** Opacity segments are drawn at so the progress track stays readable underneath them. */
const val SPONSOR_BLOCK_SEGMENT_ALPHA = 0.78f

/**
 * Default colour for [category], used when the user has not chosen a custom one.
 * Unknown categories fall back to the sponsor colour, matching the previous behaviour.
 */
fun defaultSponsorBlockColor(category: String): Color =
    when (category) {
        SponsorBlockCategories.SPONSOR -> SponsorBlockSponsor
        SponsorBlockCategories.SELFPROMO -> SponsorBlockSelfPromo
        SponsorBlockCategories.INTERACTION -> SponsorBlockInteraction
        SponsorBlockCategories.INTRO, SponsorBlockCategories.OUTRO -> SponsorBlockIntroOutro
        SponsorBlockCategories.MUSIC_OFFTOPIC -> SponsorBlockMusicOffTopic
        SponsorBlockCategories.FILLER -> SponsorBlockFiller
        SponsorBlockCategories.PREVIEW -> SponsorBlockPreview
        SponsorBlockCategories.EXCLUSIVE_ACCESS -> SponsorBlockExclusiveAccess
        else -> SponsorBlockSponsor
    }

fun sponsorBlockCategoryLabelRes(category: String): Int? =
    when (category) {
        SponsorBlockCategories.SPONSOR -> R.string.sb_category_sponsor
        SponsorBlockCategories.SELFPROMO -> R.string.sb_category_selfpromo
        SponsorBlockCategories.INTERACTION -> R.string.sb_category_interaction
        SponsorBlockCategories.INTRO -> R.string.sb_category_intro
        SponsorBlockCategories.OUTRO -> R.string.sb_category_outro
        SponsorBlockCategories.MUSIC_OFFTOPIC -> R.string.sb_category_music_offtopic
        SponsorBlockCategories.FILLER -> R.string.sb_category_filler
        SponsorBlockCategories.PREVIEW -> R.string.sb_category_preview
        SponsorBlockCategories.EXCLUSIVE_ACCESS -> R.string.sb_category_exclusive_access
        else -> null
    }

fun sponsorBlockCategoriesAndLabels(): List<Pair<String, Int>> =
    SponsorBlockCategories.ALL.mapNotNull { category ->
        sponsorBlockCategoryLabelRes(category)?.let { category to it }
    }
