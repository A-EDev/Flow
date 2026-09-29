package io.github.aedev.flow.utils

import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.SponsorBlockCategories

/** The display name for a SponsorBlock category id, or null for one this build does not name. */
fun sponsorCategoryLabelRes(category: String): Int? =
    when (category) {
        SponsorBlockCategories.SPONSOR -> R.string.sb_category_sponsor
        SponsorBlockCategories.SELF_PROMO -> R.string.sb_category_selfpromo
        SponsorBlockCategories.INTERACTION -> R.string.sb_category_interaction
        SponsorBlockCategories.INTRO -> R.string.sb_category_intro
        SponsorBlockCategories.OUTRO -> R.string.sb_category_outro
        SponsorBlockCategories.MUSIC_OFF_TOPIC -> R.string.sb_category_music_offtopic
        SponsorBlockCategories.FILLER -> R.string.sb_category_filler
        SponsorBlockCategories.PREVIEW -> R.string.sb_category_preview
        SponsorBlockCategories.EXCLUSIVE_ACCESS -> R.string.sb_category_exclusive_access
        else -> null
    }
