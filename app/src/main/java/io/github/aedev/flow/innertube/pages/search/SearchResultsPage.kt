package io.github.aedev.flow.innertube.pages.search

import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedShelf

/**
 * One page of `/youtubei/v1/search`.
 *
 * The feed is not a flat list of videos: a creator query returns a channel card, a "Latest from"
 * shelf, inline Shorts strips and a community-posts shelf interleaved with the results, in the
 * order YouTube arranged them. [sections] preserves that order.
 */
data class SearchResultsPage(
    val header: SearchHeader = SearchHeader(),
    val sections: List<SearchSection> = emptyList(),
    val estimatedResults: Long? = null,
    val continuation: String? = null,
)

/** Present on the first page only; a continuation carries the chips but no filter dialog. */
data class SearchHeader(
    val chips: List<SearchChip> = emptyList(),
    val filterGroups: List<SearchFilterGroup> = emptyList(),
)

/** A chip re-runs the search through a continuation token rather than through `params`. */
data class SearchChip(
    val label: String,
    val selected: Boolean = false,
    val continuation: String? = null,
)

data class SearchFilterGroup(
    val title: String,
    val options: List<SearchFilterOption> = emptyList(),
)

data class SearchFilterOption(
    val label: String,
    val selected: Boolean = false,
    val params: String? = null,
)

sealed interface SearchSection {
    data class Result(
        val item: FeedItem,
    ) : SearchSection

    data class Strip(
        val shelf: FeedShelf,
    ) : SearchSection
}
