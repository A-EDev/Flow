package io.github.aedev.flow.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.model.DistinctKeyTracker
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedShelf
import io.github.aedev.flow.innertube.pages.renderer.FeedShelfStyle
import io.github.aedev.flow.innertube.pages.search.SearchHeader
import io.github.aedev.flow.innertube.pages.search.SearchResultsPage
import io.github.aedev.flow.innertube.pages.search.SearchSection

/**
 * One InnerTube request per page, and nothing after it.
 *
 * Every filter and sort is a `params` token the server applies, so a page arrives already narrowed
 * and already ordered; the avatars, badges and verification the old path fetched per video are read
 * out of the same response.
 */
class SearchPagingSource(
    private val query: String,
    private val filter: SearchFilter = SearchFilter.DEFAULT,
    private val shortsEnabled: Boolean = true,
    private val onHeader: (SearchHeader) -> Unit = {},
    private val loadPage: SearchPageLoader = DefaultSearchPageLoader,
) : PagingSource<String, SearchResultItem>() {
    override fun getRefreshKey(state: PagingState<String, SearchResultItem>): String? = null

    private val loadedItemKeys = DistinctKeyTracker()

    override suspend fun load(params: LoadParams<String>): LoadResult<String, SearchResultItem> {
        val continuation = params.key
        return try {
            val page = loadPage(query, filter.toSearchParams(), continuation)
            if (continuation == null) onHeader(page.header)
            LoadResult.Page(
                data = loadedItemKeys.filter(page.toResultItems(shortsEnabled)) { it.identityKey() },
                prevKey = null,
                nextKey = page.continuation,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

fun interface SearchPageLoader {
    suspend operator fun invoke(
        query: String,
        params: String?,
        continuation: String?,
    ): SearchResultsPage
}

private val DefaultSearchPageLoader =
    SearchPageLoader { query, params, continuation ->
        YouTube.videoSearch(query, params, continuation).getOrThrow()
    }

internal fun SearchResultsPage.toResultItems(shortsEnabled: Boolean): List<SearchResultItem> =
    sections.mapNotNull { section ->
        when (section) {
            is SearchSection.Result -> section.item.toResultItem(shortsEnabled)
            is SearchSection.Strip -> section.shelf.toShelfItem(shortsEnabled)
        }
    }

private fun FeedItem.toResultItem(shortsEnabled: Boolean): SearchResultItem? =
    when (this) {
        is FeedItem.VideoItem -> SearchResultItem.VideoResult(video)
        is FeedItem.ShortItem -> SearchResultItem.VideoResult(video).takeIf { shortsEnabled }
        is FeedItem.PlaylistItem -> SearchResultItem.PlaylistResult(playlist)
        is FeedItem.RelatedChannelItem -> SearchResultItem.ChannelResult(channel)
        is FeedItem.PostItem -> null
    }

private fun FeedShelf.toShelfItem(shortsEnabled: Boolean): SearchResultItem? {
    val posts = items.filterIsInstance<FeedItem.PostItem>().map { it.post }
    if (posts.isNotEmpty()) {
        return SearchResultItem.ShelfResult(id, title, SearchShelfKind.POSTS, posts = posts)
    }
    val videos = items.mapNotNull { it.shelfVideo() }
    if (videos.isEmpty()) return null
    val kind = if (style == FeedShelfStyle.Grid) SearchShelfKind.SHORTS else SearchShelfKind.VIDEOS
    if (kind == SearchShelfKind.SHORTS && !shortsEnabled) return null
    return SearchResultItem.ShelfResult(
        id = id,
        title = title,
        kind = kind,
        videos = videos,
        collapsedItemCount = collapsedItemCount,
    )
}

private fun FeedItem.shelfVideo(): Video? =
    when (this) {
        is FeedItem.VideoItem -> video
        is FeedItem.ShortItem -> video
        else -> null
    }

private fun SearchResultItem.identityKey(): String =
    when (this) {
        is SearchResultItem.VideoResult -> video.id.prefixed("video")
        is SearchResultItem.ChannelResult -> channel.id.prefixed("channel")
        is SearchResultItem.PlaylistResult -> playlist.id.prefixed("playlist")
        is SearchResultItem.ShelfResult -> "shelf:$id"
    }

private fun String.prefixed(type: String): String = takeIf(String::isNotBlank)?.let { "$type:$it" }.orEmpty()
