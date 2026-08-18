package io.github.aedev.flow.data.shorts.queue

import android.util.Log
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.toShortVideo
import io.github.aedev.flow.data.shorts.ShortsClassifier
import io.github.aedev.flow.player.stream.StreamInfoVideoMapper.toFlowVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem

internal object ChannelShortsTab {
    fun find(channelInfo: ChannelInfo): ListLinkHandler? =
        channelInfo.tabs.firstOrNull { tab ->
            val name = runCatching { tab.contentFilters.joinToString() }.getOrDefault("")
            val url = tab.url.orEmpty()
            name.contains("shorts", ignoreCase = true) || url.contains("/shorts", ignoreCase = true)
        }
}

class ChannelShortsLoader(
    private val channelUrl: String,
) : ShortsQueueLoader {
    private var tab: ListLinkHandler? = null
    private var nextPage: Page? = null

    override suspend fun initial(): ShortsQueuePage =
        withContext(Dispatchers.IO) {
            try {
                val service = NewPipe.getService(SERVICE_YOUTUBE)
                val channelInfo = ChannelInfo.getInfo(service, channelUrl)
                val shortsTab = ChannelShortsTab.find(channelInfo) ?: run {
                    Log.w(TAG, "No Shorts tab on $channelUrl")
                    return@withContext exhausted()
                }
                tab = shortsTab

                val tabInfo = ChannelTabInfo.getInfo(service, shortsTab)
                nextPage = tabInfo.nextPage
                page(tabInfo.relatedItems.filterIsInstance<StreamInfoItem>())
            } catch (e: Exception) {
                Log.w(TAG, "Channel Shorts tab failed for $channelUrl: ${e.message}")
                exhausted()
            }
        }

    override suspend fun more(cursor: String?): ShortsQueuePage =
        withContext(Dispatchers.IO) {
            val currentTab = tab ?: return@withContext exhausted()
            val page = nextPage ?: return@withContext exhausted()
            try {
                val more = ChannelTabInfo.getMoreItems(NewPipe.getService(SERVICE_YOUTUBE), currentTab, page)
                nextPage = more.nextPage
                page(more.items.filterIsInstance<StreamInfoItem>())
            } catch (e: Exception) {
                Log.w(TAG, "Channel Shorts pagination failed for $channelUrl: ${e.message}")
                exhausted()
            }
        }

    private fun page(items: List<StreamInfoItem>): ShortsQueuePage {
        val shorts: List<ShortVideo> =
            items
                .filter { ShortsClassifier.isReel(it) }
                .mapNotNull { item -> runCatching { item.toFlowVideo().toShortVideo() }.getOrNull() }
        val hasMore = nextPage != null
        return ShortsQueuePage(
            items = shorts,
            cursor = if (hasMore) MORE else null,
            exhausted = !hasMore,
        )
    }

    private fun exhausted(): ShortsQueuePage {
        nextPage = null
        return ShortsQueuePage(emptyList(), cursor = null, exhausted = true)
    }

    private companion object {
        const val TAG = "ChannelShortsLoader"
        const val SERVICE_YOUTUBE = 0

        const val MORE = "more"
    }
}
