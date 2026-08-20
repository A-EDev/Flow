package io.github.aedev.flow.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.aedev.flow.data.model.DistinctKeyTracker
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.shorts.ChannelShortsFeed
import io.github.aedev.flow.data.shorts.ChannelShortsOwner
import io.github.aedev.flow.innertube.pages.ChannelSortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The channel Shorts tab grid, in the user's chosen sort order.
 *
 * Separate from [ChannelVideosPagingSource] because sorting is not something NewPipe's
 * `ChannelTabInfo` can express — the Latest/Popular/Oldest chips only exist in the native browse
 * response (#547).
 *
 * @param sortToken the chosen chip's continuation token, or null for the channel's own default.
 * @param onPageLoaded reports the sort bar and channel identity back, so the screen can render the
 *   chips and the queue can be opened with the same owner without a second browse.
 */
class ChannelShortsPagingSource(
    private val channelId: String,
    private val sortToken: String?,
    private val onPageLoaded: (sorts: List<ChannelSortOption>, owner: ChannelShortsOwner) -> Unit = { _, _ -> },
) : PagingSource<String, Video>() {
    private val seen = DistinctKeyTracker()
    private var owner = ChannelShortsOwner(id = channelId)

    override fun getRefreshKey(state: PagingState<String, Video>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Video> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cursor = params.key
                val page =
                    if (cursor == null) {
                        ChannelShortsFeed.initial(channelId, sortToken)
                    } else {
                        ChannelShortsFeed.more(cursor, owner)
                    }

                if (page == null) {
                    LoadResult.Page<String, Video>(emptyList(), prevKey = null, nextKey = null)
                } else {
                    owner = page.owner
                    if (page.sorts.isNotEmpty() || cursor == null) onPageLoaded(page.sorts, page.owner)
                    LoadResult.Page(
                        data = seen.filter(page.videos, Video::id),
                        prevKey = null,
                        nextKey = page.continuation,
                    )
                }
            }.getOrElse { LoadResult.Error(it) }
        }
}
