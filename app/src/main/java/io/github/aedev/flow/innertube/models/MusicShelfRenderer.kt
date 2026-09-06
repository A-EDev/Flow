package io.github.aedev.flow.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicShelfRenderer(
    val title: Runs?,
    val contents: List<Content>?,
    val continuations: List<Continuation>?,
    val bottomEndpoint: NavigationEndpoint?,
    val moreContentButton: Button?,
    val subheaders: List<Subheader>? = null,
) {
    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?,
        val continuationItemRenderer: ContinuationItemRenderer?,
    )

    @Serializable
    data class Subheader(
        val musicSideAlignedItemRenderer: MusicSideAlignedItemRenderer?,
    ) {
        @Serializable
        data class MusicSideAlignedItemRenderer(
            val startItems: List<StartItem>?,
        )

        @Serializable
        data class StartItem(
            val musicSortFilterButtonRenderer: MusicSortFilterButtonRenderer?,
        )

        @Serializable
        data class MusicSortFilterButtonRenderer(
            val menu: SortFilterMenu?,
        )

        @Serializable
        data class SortFilterMenu(
            val musicMultiSelectMenuRenderer: MusicMultiSelectMenuRenderer?,
        )

        @Serializable
        data class MusicMultiSelectMenuRenderer(
            val options: List<Option>?,
        )

        @Serializable
        data class Option(
            val musicMultiSelectMenuItemRenderer: MusicMultiSelectMenuItemRenderer?,
        )

        @Serializable
        data class MusicMultiSelectMenuItemRenderer(
            val formItemEntityKey: String?,
        )
    }
}

fun List<MusicShelfRenderer.Content>.getItems(): List<MusicResponsiveListItemRenderer> = mapNotNull { it.musicResponsiveListItemRenderer }

fun List<MusicShelfRenderer.Content>.getContinuation(): String? =
    firstOrNull { it.continuationItemRenderer != null }
        ?.continuationItemRenderer
        ?.continuationEndpoint
        ?.continuationCommand
        ?.token
