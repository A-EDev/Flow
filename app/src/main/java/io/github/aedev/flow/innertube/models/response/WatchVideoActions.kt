package io.github.aedev.flow.innertube.models.response

import kotlinx.serialization.Serializable

/**
 * The like button on the watch page, which carries the count as text rather than a number.
 *
 * Three forms of the count entity are served for the three states the button can be in; they
 * differ by one or two because the counter moves between requests, and the indifferent one is what
 * a viewer who has not voted sees. Many videos get only an `unset_like_count_entity_key` with no
 * count (6 of 9 popular videos, measured 2026-10-12), while the button itself still says "19M" and
 * "like this video along with 19,485,233 other people".
 */
@Serializable
data class WatchVideoActions(
    val menuRenderer: MenuRenderer? = null,
) {
    @Serializable
    data class MenuRenderer(
        val topLevelButtons: List<TopLevelButton> = emptyList(),
    )

    @Serializable
    data class TopLevelButton(
        val segmentedLikeDislikeButtonViewModel: SegmentedLikeDislike? = null,
    )

    @Serializable
    data class SegmentedLikeDislike(
        val likeCountEntity: LikeCountEntity? = null,
        val likeButtonViewModel: LikeButtonWrap? = null,
    )

    @Serializable
    data class LikeCountEntity(
        val expandedLikeCountIfIndifferent: WatchMetadataResponse.SecondaryInfo.TextContent? = null,
        val likeCountIfIndifferent: WatchMetadataResponse.SecondaryInfo.TextContent? = null,
    )

    @Serializable
    data class LikeButtonWrap(
        val likeButtonViewModel: LikeButton? = null,
    )

    @Serializable
    data class LikeButton(
        val toggleButtonViewModel: ToggleButtonWrap? = null,
    )

    @Serializable
    data class ToggleButtonWrap(
        val toggleButtonViewModel: ToggleButton? = null,
    )

    @Serializable
    data class ToggleButton(
        val defaultButtonViewModel: ButtonWrap? = null,
    )

    @Serializable
    data class ButtonWrap(
        val buttonViewModel: Button? = null,
    )

    @Serializable
    data class Button(
        val title: String? = null,
        val accessibilityText: String? = null,
    )

    /**
     * The exact count from the entity, then its abbreviated form, then the button's spoken label
     * (exact) and its visible title (abbreviated). A button whose likes are hidden carries no number
     * in either, so the parser still finds none.
     */
    fun likeCountText(): String? {
        val button = menuRenderer?.topLevelButtons?.firstNotNullOfOrNull { it.segmentedLikeDislikeButtonViewModel } ?: return null
        val entity = button.likeCountEntity
        val label =
            button.likeButtonViewModel
                ?.likeButtonViewModel
                ?.toggleButtonViewModel
                ?.toggleButtonViewModel
                ?.defaultButtonViewModel
                ?.buttonViewModel
        return listOf(
            entity?.expandedLikeCountIfIndifferent?.content,
            entity?.likeCountIfIndifferent?.content,
            label?.accessibilityText?.takeIf { text -> text.any(Char::isDigit) },
            label?.title?.takeIf { text -> text.any(Char::isDigit) },
        ).firstOrNull { !it.isNullOrBlank() }
    }
}
