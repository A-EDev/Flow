package io.github.aedev.flow.data.model

import io.github.aedev.flow.data.local.SponsorBlockAction

/**
 * The SponsorBlock categories Flow fetches, configures and submits, in the order settings list them.
 * Chapters and highlights (`chapter`, `poi_highlight`) are left out: they are not ranges to act on.
 */
object SponsorBlockCategories {
    const val SPONSOR = "sponsor"
    const val INTRO = "intro"
    const val OUTRO = "outro"
    const val SELF_PROMO = "selfpromo"
    const val INTERACTION = "interaction"
    const val MUSIC_OFF_TOPIC = "music_offtopic"
    const val FILLER = "filler"
    const val PREVIEW = "preview"
    const val EXCLUSIVE_ACCESS = "exclusive_access"

    val all: List<String> =
        listOf(SPONSOR, INTRO, OUTRO, SELF_PROMO, INTERACTION, MUSIC_OFF_TOPIC, FILLER, PREVIEW, EXCLUSIVE_ACCESS)

    /** The server only accepts [EXCLUSIVE_ACCESS] as a whole-video label, never as a time range. */
    val submittable: List<String> = all - EXCLUSIVE_ACCESS

    /** `full` is what returns whole-video labels, which arrive as a zero-length `[0, 0]` segment. */
    val actionTypes: List<String> = listOf("skip", "mute", "full")

    /** Filler and previews are often wanted, so they show on the seek bar without being skipped. */
    fun defaultAction(category: String): SponsorBlockAction =
        when (category) {
            FILLER, PREVIEW -> SponsorBlockAction.IGNORE
            else -> SponsorBlockAction.SKIP
        }
}
