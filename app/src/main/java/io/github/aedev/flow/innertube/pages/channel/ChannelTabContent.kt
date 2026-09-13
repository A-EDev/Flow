package io.github.aedev.flow.innertube.pages.channel

import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video

/**
 * One page of one channel tab. Grid tabs fill [items], shelf tabs fill [sections].
 *
 * A sort chip's token is itself a continuation, so switching sort and loading a page are the same
 * request with a different token.
 */
data class ChannelTabContent(
    val kind: ChannelTabKind,
    val items: List<ChannelItem> = emptyList(),
    val sections: List<ChannelSection> = emptyList(),
    val filters: List<ChannelFilterGroup> = emptyList(),
    val continuation: String? = null,
    val owner: ChannelOwner = ChannelOwner(),
)

/** Lockups carry no byline and continuations carry no header, so the caller threads this forward. */
data class ChannelOwner(
    val id: String = "",
    val name: String = "",
    val avatarUrl: String = "",
)

data class ChannelSection(
    val id: String,
    val title: String? = null,
    val subtitle: String? = null,
    val style: ChannelSectionStyle = ChannelSectionStyle.Carousel,
    val items: List<ChannelItem> = emptyList(),
    val moreParams: String? = null,
    val morePlaylistId: String? = null,
)

enum class ChannelSectionStyle {
    Trailer,
    Carousel,
    Grid,
    List,
}

sealed interface ChannelItem {
    data class VideoItem(
        val video: Video,
    ) : ChannelItem

    data class ShortItem(
        val video: Video,
    ) : ChannelItem

    data class PlaylistItem(
        val playlist: Playlist,
    ) : ChannelItem

    data class RelatedChannelItem(
        val channel: Channel,
    ) : ChannelItem

    data class PostItem(
        val post: CommunityPost,
    ) : ChannelItem
}

internal fun ChannelItem.distinctKey(): String =
    when (this) {
        is ChannelItem.VideoItem -> "v:${video.id}"
        is ChannelItem.ShortItem -> "s:${video.id}"
        is ChannelItem.PlaylistItem -> "p:${playlist.id}"
        is ChannelItem.RelatedChannelItem -> "c:${channel.id}"
        is ChannelItem.PostItem -> "b:${post.id}"
    }
