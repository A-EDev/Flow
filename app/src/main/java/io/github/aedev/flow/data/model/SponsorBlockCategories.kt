package io.github.aedev.flow.data.model

/**
 * Canonical SponsorBlock category ids and API action types.
 *
 * Settings, fetch, and the player must share this list. Fetching a subset of what the
 * settings screen exposes makes those extra toggles no-ops.
 */
object SponsorBlockCategories {
    const val SPONSOR = "sponsor"
    const val INTRO = "intro"
    const val OUTRO = "outro"
    const val SELFPROMO = "selfpromo"
    const val INTERACTION = "interaction"
    const val MUSIC_OFFTOPIC = "music_offtopic"
    const val FILLER = "filler"
    const val PREVIEW = "preview"
    const val EXCLUSIVE_ACCESS = "exclusive_access"

    val ALL: List<String> =
        listOf(
            SPONSOR,
            INTRO,
            OUTRO,
            SELFPROMO,
            INTERACTION,
            MUSIC_OFFTOPIC,
            FILLER,
            PREVIEW,
            EXCLUSIVE_ACCESS,
        )

    /**
     * Action types requested from the skipSegments API.
     *
     * The API defaults to `skip` only, which drops mute-only segments and `exclusive_access`
     * (served as `full`). `poi` / `chapter` are not requested; they are not skippable ranges.
     */
    val FETCH_ACTION_TYPES: List<String> = listOf("skip", "mute", "full")

    fun isWholeVideoAction(actionType: String): Boolean {
        val type = actionType.lowercase()
        return type == "full" || type == "poi" || type == "chapter"
    }

    fun isMuteAction(actionType: String): Boolean = actionType.equals("mute", ignoreCase = true)
}
