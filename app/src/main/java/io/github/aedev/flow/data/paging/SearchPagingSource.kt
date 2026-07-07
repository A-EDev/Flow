package io.github.aedev.flow.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.aedev.flow.data.local.Duration
import io.github.aedev.flow.data.local.UploadDate
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.local.SortType
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.utils.avatarImageIdentityKey
import io.github.aedev.flow.utils.distinctBestImageUrls
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Sealed class representing any unified search result item.
 * Allows mixing videos, channels, and playlists in a single paged list.
 */
sealed class SearchResultItem {
    data class VideoResult(val video: Video) : SearchResultItem()
    data class ChannelResult(val channel: Channel) : SearchResultItem()
    data class PlaylistResult(val playlist: Playlist) : SearchResultItem()
    data class ShortsShelfResult(val shorts: List<Video>) : SearchResultItem()
}

/**
 * Paging3 source for YouTube search results with infinite scroll support.
 *
 * Each [load] call creates a fresh extractor for the given [query].
 * For subsequent pages the extractor's [getPage] API is used with the
 * [Page] token returned by the previous call — NewPipe handles the URL
 * resolution internally, so a fresh extractor is safe to reuse this way.
 */
class SearchPagingSource(
    private val query: String,
    private val contentFilters: List<String> = emptyList(),
    private val searchFilter: SearchFilter? = null
) : PagingSource<Page, SearchResultItem>() {

    companion object {
        private const val TAG = "SearchPagingSource"
    }

    private val service = ServiceList.YouTube

    override fun getRefreshKey(state: PagingState<Page, SearchResultItem>): Page? = null

    override suspend fun load(params: LoadParams<Page>): LoadResult<Page, SearchResultItem> {
        return try {
            withContext(Dispatchers.IO) {
                val page = params.key

                // Shorts tab: NewPipe search has no shorts, so serve them directly (single page).
                if (searchFilter?.contentType == ContentType.SHORTS) {
                    val shorts = if (page == null) fetchShortVideos().map { SearchResultItem.VideoResult(it) } else emptyList()
                    return@withContext LoadResult.Page(shorts, prevKey = null, nextKey = null)
                }

                val extractor = service.getSearchExtractor(query, contentFilters, "")
                extractor.fetchPage()

                val infoPage = if (page != null) {
                    extractor.getPage(page)
                } else {
                    extractor.initialPage
                }

                val searchAvatarStacks = if (page == null) {
                    withTimeoutOrNull(4_000L) {
                        YouTube.searchVideoAvatarStacks(query).getOrNull()
                    }.orEmpty()
                } else {
                    emptyMap()
                }

                val items: List<SearchResultItem> = infoPage.items.mapNotNull { item ->
                    when (item) {
                        is StreamInfoItem -> {
                            val isLiveStream = item.streamType == StreamType.LIVE_STREAM ||
                                item.streamType == StreamType.AUDIO_LIVE_STREAM
                            if (searchFilter?.contentType == io.github.aedev.flow.data.local.ContentType.LIVE &&
                                !isLiveStream
                            ) {
                                return@mapNotNull null
                            }

                            val duration = item.duration.toInt()
                            val uploadDate = item.textualUploadDate ?: ""

                            if (searchFilter != null) {
                                if (searchFilter.duration == Duration.UNDER_4_MINUTES && duration >= 240) return@mapNotNull null
                                if (searchFilter.duration == Duration.FROM_4_TO_20_MINUTES && (duration < 240 || duration > 1200)) return@mapNotNull null
                                if (searchFilter.duration == Duration.OVER_20_MINUTES && duration <= 1200) return@mapNotNull null

                                if (searchFilter.uploadDate != UploadDate.ANY && uploadDate.isNotEmpty()) {
                                    val loweredDate = uploadDate.lowercase()
                                    val isHoursOrLess = loweredDate.contains("second") || loweredDate.contains("minute") || loweredDate.contains("hour")
                                    val isDays = loweredDate.contains("day")
                                    val isWeeks = loweredDate.contains("week")
                                    val isMonths = loweredDate.contains("month")
                                    val isYears = loweredDate.contains("year")

                                    val isOne = loweredDate.contains("1 day") || loweredDate.contains("1 week") || loweredDate.contains("1 month") || loweredDate.contains("1 year")

                                    when (searchFilter.uploadDate) {
                                        UploadDate.TODAY -> if (!isHoursOrLess && !(isDays && loweredDate.contains("1 day"))) return@mapNotNull null
                                        UploadDate.THIS_WEEK -> if (isYears || isMonths || (isWeeks && !loweredDate.contains("1 week"))) return@mapNotNull null
                                        UploadDate.THIS_MONTH -> if (isYears || (isMonths && !loweredDate.contains("1 month"))) return@mapNotNull null
                                        UploadDate.THIS_YEAR -> if (isYears && !loweredDate.contains("1 year")) return@mapNotNull null
                                        else -> {}
                                    }
                                }
                            }

                            val videoId = extractVideoId(item.url)
                            val thumbnail = ThumbnailUrlResolver.normalizeVideoThumbnail(
                                videoId,
                                item.thumbnails.maxByOrNull { it.width }?.url
                            )
                            val channelThumbs = try {
                                item.uploaderAvatars.distinctBestImageUrls()
                            } catch (_: Exception) { emptyList() }
                            val mergedChannelThumbs = (
                                searchAvatarStacks[videoId].orEmpty() + channelThumbs
                            )
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .distinctBy { it.avatarImageIdentityKey() }
                                .take(2)
                            val channelThumb = mergedChannelThumbs.firstOrNull().orEmpty()

                            SearchResultItem.VideoResult(
                                Video(
                                    id = videoId,
                                    title = item.name ?: "",
                                    channelName = item.uploaderName ?: "",
                                    channelId = extractChannelId(item.uploaderUrl ?: ""),
                                    thumbnailUrl = thumbnail,
                                    duration = item.duration.toInt(),
                                    viewCount = item.viewCount,
                                    uploadDate = item.textualUploadDate ?: "",
                                    timestamp = System.currentTimeMillis(),
                                    channelThumbnailUrl = channelThumb,
                                    channelThumbnailUrls = mergedChannelThumbs,
                                    isShort = item.duration in 1..60,
                                    isLive = isLiveStream
                                )
                            )
                        }

                        is ChannelInfoItem -> {
                            val thumb = try {
                                item.thumbnails.maxByOrNull { it.width }?.url ?: ""
                            } catch (_: Exception) { "" }

                            SearchResultItem.ChannelResult(
                                Channel(
                                    id = extractChannelId(item.url),
                                    name = item.name ?: "",
                                    thumbnailUrl = thumb,
                                    subscriberCount = item.subscriberCount,
                                    description = item.description ?: "",
                                    url = item.url ?: ""
                                )
                            )
                        }

                        is PlaylistInfoItem -> {
                            val thumb = try {
                                item.thumbnails.maxByOrNull { it.width }?.url ?: ""
                            } catch (_: Exception) { "" }

                            SearchResultItem.PlaylistResult(
                                Playlist(
                                    id = extractPlaylistId(item.url),
                                    name = item.name ?: "",
                                    thumbnailUrl = thumb,
                                    videoCount = item.streamCount.toInt()
                                )
                            )
                        }

                        else -> null
                    }
                }

                // All tab (unfiltered): shorts sit in a shelf NewPipe skips; surface them
                // as a horizontal shelf after the top result (first page only).
                val unfilteredAll = searchFilter == null || (searchFilter.contentType == ContentType.ALL &&
                    searchFilter.duration == Duration.ANY && searchFilter.uploadDate == UploadDate.ANY)
                val combined = if (page == null && unfilteredAll) {
                    val shorts = fetchShortVideos().take(15)
                    when {
                        shorts.isEmpty() -> items
                        items.isEmpty() -> listOf(SearchResultItem.ShortsShelfResult(shorts))
                        else -> listOf(items.first(), SearchResultItem.ShortsShelfResult(shorts)) + items.drop(1)
                    }
                } else items
                val enrichedCombined = enrichCollabVideoResults(combined)

                Log.d(TAG, "Loaded ${items.size} items | query='$query' | nextPage=${infoPage.nextPage != null}")

                LoadResult.Page(
                    data = sortVideoItems(searchFilter = searchFilter, enrichedCombined),
                    prevKey = null,
                    nextKey = infoPage.nextPage
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading search results for '$query': ${e.message}", e)
            LoadResult.Error(e)
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private suspend fun enrichCollabVideoResults(items: List<SearchResultItem>): List<SearchResultItem> {
        val stacks = mutableMapOf<String, List<String>>()

        items.asSequence()
            .flatMap { item ->
                when (item) {
                    is SearchResultItem.VideoResult -> sequenceOf(item.video)
                    is SearchResultItem.ShortsShelfResult -> item.shorts.asSequence()
                    else -> emptySequence()
                }
            }
            .filter { it.needsCollabAvatarStack() }
            .take(10)
            .forEach { video ->
                val stack = withTimeoutOrNull(4_000L) {
                    YouTube.videoAvatarStack(video.id).getOrNull()
                }.orEmpty()
                if (stack.size > 1) stacks[video.id] = stack
            }

        if (stacks.isEmpty()) return items

        fun Video.withCollabStack(): Video {
            val stack = stacks[id].orEmpty()
            if (stack.size <= 1) return this
            val merged = (stack + channelThumbnailUrls + channelThumbnailUrl)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.avatarImageIdentityKey() }
                .take(2)
            return if (merged.size > 1) {
                copy(
                    channelThumbnailUrl = merged.first(),
                    channelThumbnailUrls = merged,
                )
            } else {
                this
            }
        }

        return items.map { item ->
            when (item) {
                is SearchResultItem.VideoResult -> item.copy(video = item.video.withCollabStack())
                is SearchResultItem.ShortsShelfResult -> item.copy(shorts = item.shorts.map { it.withCollabStack() })
                else -> item
            }
        }
    }

    private fun Video.needsCollabAvatarStack(): Boolean =
        id.isNotBlank() &&
            channelThumbnailUrls.size < 2 &&
            channelName.isLikelyCollaborationByline()

    private fun String.isLikelyCollaborationByline(): Boolean {
        val normalized = " ${trim().lowercase()} "
        return normalized.contains(" and ") ||
            normalized.contains(" & ") ||
            normalized.contains(" x ") ||
            normalized.contains(" with ")
    }

    /** Shorts from the web-client search shelf, as [Video]s flagged [Video.isShort]. */
    private suspend fun fetchShortVideos(): List<Video> =
        YouTube.searchShorts(query).getOrNull().orEmpty().map { s ->
            Video(
                id = s.id,
                title = s.title,
                channelName = "",
                channelId = "",
                thumbnailUrl = "https://i.ytimg.com/vi/${s.id}/oar2.jpg",
                duration = 0,
                viewCount = s.viewCount,
                uploadDate = "",
                timestamp = System.currentTimeMillis(),
                isShort = true
            )
        }

    private fun extractVideoId(url: String): String {
        val patterns = listOf(
            "v=([A-Za-z0-9_-]{11})".toRegex(),
            "youtu\\.be/([A-Za-z0-9_-]{11})".toRegex(),
            "shorts/([A-Za-z0-9_-]{11})".toRegex()
        )
        for (pat in patterns) {
            val m = pat.find(url) ?: continue
            return m.groupValues[1]
        }
        return url.substringAfterLast("/").substringBefore("?").take(11)
    }

    private fun extractChannelId(url: String): String =
        url.substringAfter("/channel/").substringBefore("/").substringBefore("?")
            .ifEmpty { url.substringAfterLast("/").substringBefore("?") }

    private fun extractPlaylistId(url: String): String =
        url.substringAfter("list=").substringBefore("&")
            .ifEmpty { url.substringAfterLast("/").substringBefore("?") }

    private fun sortVideoItems(searchFilter: SearchFilter?, items: List<SearchResultItem>): List<SearchResultItem> =
        when (searchFilter?.sortType) {
            SortType.RELEVANCE -> items
            SortType.RATING -> items.sortedByDescending { item ->
                if (item is SearchResultItem.VideoResult) item.video.likeCount else 0L
            }

            SortType.VIEWS -> items.sortedByDescending { item ->
                if (item is SearchResultItem.VideoResult) item.video.viewCount else 0L
            }

            else -> items
        }

}
