package io.github.aedev.flow.data.paging

import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.renderer.CommunityPost

/** One row of the search feed: a result, or one of the strips YouTube interleaves between them. */
sealed interface SearchResultItem {
    data class VideoResult(
        val video: Video,
    ) : SearchResultItem

    /**
     * [latestVideos] is the creator's "Latest from" strip, which YouTube returns as its own shelf
     * directly after the card and renders attached to it.
     */
    data class ChannelResult(
        val channel: Channel,
        val latestTitle: String? = null,
        val latestVideos: List<Video> = emptyList(),
    ) : SearchResultItem

    data class PlaylistResult(
        val playlist: Playlist,
    ) : SearchResultItem

    /**
     * [id] is position-qualified by the parser: one response carries three strips all titled
     * "Shorts", and a repeated key both collapses them in the de-duplicator and crashes the grid.
     */
    data class ShelfResult(
        val id: String,
        val title: String?,
        val kind: SearchShelfKind,
        val videos: List<Video> = emptyList(),
        val posts: List<CommunityPost> = emptyList(),
        val collapsedItemCount: Int? = null,
    ) : SearchResultItem
}

enum class SearchShelfKind {
    SHORTS,
    VIDEOS,
    POSTS,
}
