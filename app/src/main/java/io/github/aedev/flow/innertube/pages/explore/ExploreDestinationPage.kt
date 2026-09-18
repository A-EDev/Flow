package io.github.aedev.flow.innertube.pages.explore

import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.github.aedev.flow.innertube.pages.renderer.FeedShelf

/**
 * One explore destination's landing page: the shelves it arranged, and the category tabs it offers.
 *
 * A destination carries no continuation of its own — each shelf's `moreParams` opens a paginated
 * grid instead, which [io.github.aedev.flow.data.paging.FeedTabPagingSource] loads.
 */
data class ExploreDestinationPage(
    val title: String? = null,
    val tabs: List<ExploreTab> = emptyList(),
    val shelves: List<FeedShelf> = emptyList(),
    val owner: FeedItemOwner = FeedItemOwner(),
)

/** [params] comes from the response, so a token rotation cannot strand a tab. */
data class ExploreTab(
    val title: String,
    val browseId: String?,
    val params: String?,
    val selected: Boolean = false,
)
