package io.github.aedev.flow.data.paging

import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.distinctByNonBlankKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import javax.inject.Inject

private const val MAX_RESULTS = 15

/** One page of channels matching a query, for pickers that only ever need the first page. */
class ChannelSearch
    @Inject
    constructor() {
        suspend fun search(query: String): List<Channel> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf("channels"), null)
                    extractor.fetchPage()
                    extractor.initialPage.items
                        .filterIsInstance<ChannelInfoItem>()
                        .mapNotNull { item -> item.toChannel() }
                        .distinctByNonBlankKey(Channel::id)
                        .take(MAX_RESULTS)
                }.getOrDefault(emptyList())
            }

        private fun ChannelInfoItem.toChannel(): Channel? {
            val id =
                when {
                    url.contains("/channel/") -> url.substringAfter("/channel/").substringBefore("/").substringBefore("?")
                    url.contains("/@") -> url.substringAfter("/@").substringBefore("/").substringBefore("?")
                    else -> url.substringAfterLast("/").substringBefore("?")
                }
            if (id.isEmpty() || name.isNullOrEmpty()) return null
            return Channel(
                id = id,
                name = name,
                thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url.orEmpty(),
                subscriberCount = subscriberCount,
            )
        }
    }
