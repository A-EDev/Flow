package com.flow.youtube.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.flow.youtube.data.model.Channel
import com.flow.youtube.data.model.Playlist
import com.flow.youtube.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Sealed class representing any unified search result item.
 * Allows mixing videos, channels, and playlists in a single paged list.
 */
sealed class SearchResultItem {
    data class VideoResult(val video: Video) : SearchResultItem()
    data class ChannelResult(val channel: Channel) : SearchResultItem()
    data class PlaylistResult(val playlist: Playlist) : SearchResultItem()
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
    private val contentFilters: List<String> = emptyList()
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

                val extractor = service.getSearchExtractor(query, contentFilters, "")
                extractor.fetchPage()

                val infoPage = if (page != null) {
                    extractor.getPage(page)
                } else {
                    extractor.initialPage
                }

                val items: List<SearchResultItem> = infoPage.items.mapNotNull { item ->
                    when (item) {
                        is StreamInfoItem -> {
                            val videoId = extractVideoId(item.url)
                            val thumbnail = item.thumbnails.maxByOrNull { it.width }?.url
                                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                            val channelThumb = try {
                                item.uploaderAvatars.maxByOrNull { it.width }?.url ?: ""
                            } catch (_: Exception) { "" }

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
                                    isShort = item.duration in 1..60
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

                Log.d(TAG, "Loaded ${items.size} items | query='$query' | nextPage=${infoPage.nextPage != null}")

                LoadResult.Page(
                    data = items,
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
}
