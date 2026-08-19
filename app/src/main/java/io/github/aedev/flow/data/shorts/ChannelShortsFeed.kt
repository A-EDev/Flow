package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.ChannelShortsPage
import io.github.aedev.flow.innertube.pages.ChannelSortOption
import io.github.aedev.flow.utils.ThumbnailUrlResolver

/**
 * Who the Shorts belong to. Only the first browse carries the channel header, so the caller threads
 * this back into later pages — otherwise every Short past the first page loses its byline.
 */
data class ChannelShortsOwner(
    val id: String = "",
    val name: String = "",
    val avatarUrl: String = "",
)

/**
 * One page of a channel's Shorts tab, mapped onto app models.
 *
 * [sorts] is echoed on every page because a sort switch is itself a continuation: the response that
 * comes back re-sorted also carries the bar, with the newly chosen chip marked selected.
 */
data class ChannelShortsFeedPage(
    val videos: List<Video>,
    val sorts: List<ChannelSortOption>,
    val continuation: String?,
    val owner: ChannelShortsOwner,
)

/**
 * The channel Shorts tab, sorted. Shared by the tab's grid and by the Shorts queue that opens from
 * it, so both walk the same list in the same order — which is the point of #547: the sort the user
 * picked has to decide swipe order too, not just what the grid shows.
 *
 * Stateless; the caller holds the continuation token and the owner.
 */
object ChannelShortsFeed {
    suspend fun initial(
        channelId: String,
        sortToken: String? = null,
    ): ChannelShortsFeedPage? {
        // A chip token already encodes the channel, so with a sort chosen it *is* the first page.
        val page =
            if (sortToken.isNullOrBlank()) {
                YouTube.channelShorts(channelId).getOrNull()
            } else {
                YouTube.channelShortsContinuation(sortToken).getOrNull()
            } ?: return null
        return page.toFeedPage(ChannelShortsOwner(id = channelId))
    }

    suspend fun more(
        continuation: String,
        owner: ChannelShortsOwner,
    ): ChannelShortsFeedPage? = YouTube.channelShortsContinuation(continuation).getOrNull()?.toFeedPage(owner)

    private fun ChannelShortsPage.toFeedPage(fallbackOwner: ChannelShortsOwner): ChannelShortsFeedPage {
        val owner =
            ChannelShortsOwner(
                id = channelId.ifBlank { fallbackOwner.id },
                name = channelName.ifBlank { fallbackOwner.name },
                avatarUrl = channelAvatarUrl.ifBlank { fallbackOwner.avatarUrl },
            )
        return ChannelShortsFeedPage(
            videos =
                shorts.map { item ->
                    Video(
                        id = item.id,
                        title = item.title,
                        thumbnailUrl = ThumbnailUrlResolver.normalizeVideoThumbnail(item.id, item.thumbnailUrl),
                        channelName = owner.name,
                        channelId = owner.id,
                        channelThumbnailUrl = owner.avatarUrl,
                        viewCount = item.viewCount,
                        duration = 0,
                        uploadDate = "",
                        description = "",
                        isShort = true,
                    )
                },
            sorts = sorts,
            continuation = continuation,
            owner = owner,
        )
    }
}
