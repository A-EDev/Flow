package io.github.aedev.flow.data.subscriptions

import android.util.Log
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.channel.ChannelPage
import io.github.aedev.flow.innertube.pages.channel.ChannelTabContent
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.io.IOException
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
 *
 * A network failure (a DNS miss, a timeout, a dropped connection) gets one spaced retry: the
 * fallback browses several channels at once, and one blip otherwise fails all of them (#1186).
 */
@Singleton
class ChannelUploadsClient internal constructor(
    private val landing: suspend (channelId: String) -> Result<ChannelPage>,
    private val tab: suspend (browseId: String, params: String, owner: FeedItemOwner, kind: ChannelTabKind) -> Result<ChannelTabContent>,
    private val continuation: suspend (token: String, owner: FeedItemOwner, kind: ChannelTabKind) -> Result<ChannelTabContent>,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
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
    ): Result<ChannelUploads> {
        val first = fetchOnce(channelId, notBeforeMillis, limits)
        val error = first.exceptionOrNull() as? IOException ?: return first
        Log.w(TAG, "[$channelId] Channel tabs network error, retrying once: ${error::class.simpleName}: ${error.message}")
        sleep(NETWORK_RETRY_DELAY_MS)
        return fetchOnce(channelId, notBeforeMillis, limits)
    }

    private suspend fun fetchOnce(
        channelId: String,
        notBeforeMillis: Long,
        limits: ChannelUploadLimits,
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

    /**
     * The newest uploads for Home: the first page of the Videos tab. No landing browse, since the
     * subscription already names the channel. Reels are left to RSS, which dates them.
     */
    suspend fun latest(
        owner: FeedItemOwner,
        videos: Int,
    ): Result<List<Video>> =
        runCatching {
            val params = ChannelTabKind.Videos.defaultParams ?: error("No params for the Videos tab")
            tab(owner.id, params, owner, ChannelTabKind.Videos)
                .getOrThrow()
                .items
                .uploads()
                .take(videos)
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

                // A Shorts row carries no date, and the model's default is "now": without this an old
                // reel off the tab would sort as the newest upload (#1175). Unknown is 0.
                is FeedItem.ShortItem -> item.video.copy(isShort = true, timestamp = item.video.datedTimestamp())

                else -> null
            }
        }.filter { it.id.isNotBlank() }

    private fun Video.datedTimestamp(): Long = if (uploadDate.isBlank()) 0L else timestamp

    private fun List<Video>.reachesInto(notBeforeMillis: Long): Boolean {
        val oldest = lastOrNull { it.timestamp > 0L } ?: return false
        return oldest.timestamp > notBeforeMillis
    }

    private companion object {
        const val TAG = "ChannelUploads"
        const val NETWORK_RETRY_DELAY_MS = 1_500L
        val UPLOAD_TABS = setOf(ChannelTabKind.Videos, ChannelTabKind.Shorts, ChannelTabKind.Live)
    }
}

data class ChannelUploadLimits(
    val videos: Int = 60,
    val shorts: Int = 20,
    val live: Int = 20,
)
