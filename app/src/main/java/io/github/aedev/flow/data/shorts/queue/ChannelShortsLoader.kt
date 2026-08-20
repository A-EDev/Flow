package io.github.aedev.flow.data.shorts.queue

import android.util.Log
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.toShortVideo
import io.github.aedev.flow.data.shorts.ChannelShortsFeed
import io.github.aedev.flow.data.shorts.ChannelShortsOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo

class ChannelShortsLoader(
    private val channelUrl: String,
    private val sortIndex: Int = 0,
) : ShortsQueueLoader {
    private var owner = ChannelShortsOwner()
    private var nextPage: String? = null

    override suspend fun initial(): ShortsQueuePage =
        withContext(Dispatchers.IO) {
            val channelId =
                resolveChannelId() ?: run {
                    Log.w(TAG, "Could not resolve a channel id from $channelUrl")
                    return@withContext exhausted()
                }

            val firstPage = ChannelShortsFeed.initial(channelId) ?: return@withContext exhausted()
            val chosen = firstPage.sorts.getOrNull(sortIndex)
            val page =
                if (sortIndex == 0 || chosen == null || chosen.selected) {
                    firstPage
                } else {
                    ChannelShortsFeed.initial(channelId, chosen.token) ?: firstPage
                }

            owner = page.owner
            nextPage = page.continuation
            page(page.videos.map { it.toShortVideo() })
        }

    override suspend fun more(cursor: String?): ShortsQueuePage =
        withContext(Dispatchers.IO) {
            val continuation = nextPage ?: return@withContext exhausted()
            val page = ChannelShortsFeed.more(continuation, owner) ?: return@withContext exhausted()
            owner = page.owner
            nextPage = page.continuation
            page(page.videos.map { it.toShortVideo() })
        }

    private suspend fun resolveChannelId(): String? {
        CHANNEL_ID
            .find(channelUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        return runCatching {
            ChannelInfo.getInfo(NewPipe.getService(SERVICE_YOUTUBE), channelUrl).id
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun page(shorts: List<ShortVideo>): ShortsQueuePage {
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
        val CHANNEL_ID = Regex("/channel/(UC[\\w-]+)")
    }
}
