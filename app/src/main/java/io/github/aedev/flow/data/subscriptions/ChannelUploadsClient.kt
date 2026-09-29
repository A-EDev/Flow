package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.channel.ChannelPage
import io.github.aedev.flow.innertube.pages.channel.ChannelTabContent
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** A channel's recent uploads, tab by tab, as its own Videos, Shorts and Live tabs list them. */
data class ChannelUploads(
    val owner: FeedItemOwner,
    val videos: List<Video> = emptyList(),
    val shorts: List<Video> = emptyList(),
    val live: List<Video> = emptyList(),
)

/**
 * Reads a channel's upload tabs through the native InnerTube browse, for the subscription feed's
 * fallback when RSS cannot answer for a channel.
 *
 * A tab whose first page fails fails the whole channel: an empty tab and an unreachable one must
 * not look alike, or the feed drops the channel's rows as if it had stopped uploading (#1094).
 */
@Singleton
class ChannelUploadsClient internal constructor(
    private val landing: suspend (channelId: String) -> Result<ChannelPage>,
    private val tab: suspend (browseId: String, params: String, owner: FeedItemOwner, kind: ChannelTabKind) -> Result<ChannelTabContent>,
    private val continuation: suspend (token: String, owner: FeedItemOwner, kind: ChannelTabKind) -> Result<ChannelTabContent>,
) {
    @Inject
    constructor() : this(
        landing = { YouTube.channelLanding(it) },
        tab = { browseId, params, owner, kind -> YouTube.channelTab(browseId, params, owner, kind) },
        continuation = { token, owner, kind -> YouTube.channelTabContinuation(token, owner, kind) },
    )

    /**
     * Pages the Videos and Live tabs only while they are still inside [notBeforeMillis]: both list
     * newest first, so the first undated or older row is where the window ends. Shorts rows carry
     * no date, so that tab stops after its first page.
     */
    suspend fun fetch(
        channelId: String,
        notBeforeMillis: Long,
        limits: ChannelUploadLimits = ChannelUploadLimits(),
    ): Result<ChannelUploads> =
        runCatching {
            val page = landing(channelId).getOrThrow()
            val owner =
                FeedItemOwner(
                    id = page.header.id.ifBlank { channelId },
                    name = page.header.title,
                    avatarUrl = page.header.avatarUrl,
                )
            // No tabs at all is a landing the parser could not read; tabs without an upload tab is
            // a channel that simply has nothing to list.
            if (page.tabs.isEmpty()) error("Channel landing has no tabs")
            val tabs = page.tabs.filter { it.kind in UPLOAD_TABS }.associateBy { it.kind }

            coroutineScope {
                val videos =
                    tabs[ChannelTabKind.Videos]?.let { descriptor ->
                        async { readTab(channelId, descriptor.params, owner, ChannelTabKind.Videos, limits.videos, notBeforeMillis) }
                    }
                val shorts =
                    tabs[ChannelTabKind.Shorts]?.let { descriptor ->
                        async { readTab(channelId, descriptor.params, owner, ChannelTabKind.Shorts, limits.shorts, notBeforeMillis = null) }
                    }
                val live =
                    tabs[ChannelTabKind.Live]?.let { descriptor ->
                        async { readTab(channelId, descriptor.params, owner, ChannelTabKind.Live, limits.live, notBeforeMillis) }
                    }
                ChannelUploads(
                    owner = owner,
                    videos = videos?.await().orEmpty(),
                    shorts = shorts?.await().orEmpty(),
                    live = live?.await().orEmpty(),
                )
            }
        }

    private suspend fun readTab(
        channelId: String,
        params: String?,
        owner: FeedItemOwner,
        kind: ChannelTabKind,
        limit: Int,
        notBeforeMillis: Long?,
    ): List<Video> {
        val tabParams = params ?: kind.defaultParams ?: error("No params for the $kind tab")
        var content = tab(channelId, tabParams, owner, kind).getOrThrow()
        val items = content.items.uploads().toMutableList()
        while (items.size < limit && notBeforeMillis != null && items.reachesInto(notBeforeMillis)) {
            val token = content.continuation ?: break
            // A later page failing still leaves the newest uploads, which are the ones the feed shows.
            content = continuation(token, owner, kind).getOrNull() ?: break
            val more = content.items.uploads()
            if (more.isEmpty()) break
            items += more
        }
        return items.distinctBy { it.id }.take(limit)
    }

    private fun List<FeedItem>.uploads(): List<Video> =
        mapNotNull { item ->
            when (item) {
                is FeedItem.VideoItem -> item.video
                is FeedItem.ShortItem -> item.video.copy(isShort = true)
                else -> null
            }
        }.filter { it.id.isNotBlank() }

    private fun List<Video>.reachesInto(notBeforeMillis: Long): Boolean {
        val oldest = lastOrNull { it.timestamp > 0L } ?: return false
        return oldest.timestamp > notBeforeMillis
    }

    private companion object {
        val UPLOAD_TABS = setOf(ChannelTabKind.Videos, ChannelTabKind.Shorts, ChannelTabKind.Live)
    }
}

data class ChannelUploadLimits(
    val videos: Int = 60,
    val shorts: Int = 20,
    val live: Int = 20,
)
