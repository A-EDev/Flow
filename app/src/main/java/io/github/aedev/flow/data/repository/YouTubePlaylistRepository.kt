package io.github.aedev.flow.data.repository

import android.util.Log
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.VideoPlaylistPage
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YouTubePlaylistRepo"
private const val MAX_PAGES = 60
private const val FULL_LIST_TTL_MS = 10 * 60 * 1000L

/** A YouTube playlist as the page shows it: the first page now, the rest through [YouTubePlaylistRepository.nextPage]. */
data class RemotePlaylistPage(
    val title: String,
    val ownerName: String?,
    val ownerId: String?,
    val description: String,
    val thumbnailUrl: String,
    val videos: List<Video>,
    val continuation: String?,
)

/**
 * YouTube playlists from InnerTube, one request per page. The first page shows at once and later
 * pages load on demand; a complete list is kept for [FULL_LIST_TTL_MS] so reopening a playlist or
 * syncing a saved one does not walk it again.
 */
@Singleton
class YouTubePlaylistRepository
    @Inject
    constructor() {
        private data class CachedList(
            val page: RemotePlaylistPage,
            val loadedAtMs: Long,
        )

        private val completeLists = ConcurrentHashMap<String, CachedList>()

        /** The whole playlist if it was loaded recently, so the page can show it without a request. */
        fun cachedComplete(playlistId: String): RemotePlaylistPage? =
            completeLists[playlistId]
                ?.takeIf { System.currentTimeMillis() - it.loadedAtMs < FULL_LIST_TTL_MS }
                ?.page

        fun rememberComplete(
            playlistId: String,
            page: RemotePlaylistPage,
        ) {
            completeLists[playlistId] = CachedList(page.copy(continuation = null), System.currentTimeMillis())
        }

        suspend fun firstPage(playlistId: String): RemotePlaylistPage? =
            fetch { YouTube.videoPlaylistPage(playlistId) }
                ?.takeIf { it.title != null || it.videos.isNotEmpty() }
                ?.let { page ->
                    RemotePlaylistPage(
                        title = page.title.orEmpty(),
                        ownerName = page.ownerName,
                        ownerId = page.ownerId?.takeIf(String::isNotBlank),
                        description = page.description.orEmpty(),
                        thumbnailUrl =
                            page.thumbnailUrl ?: page.videos
                                .firstOrNull()
                                ?.thumbnailUrl
                                .orEmpty(),
                        videos = page.videos,
                        continuation = page.continuation,
                    )
                }

        /** The videos behind [continuation] and the token after them. */
        suspend fun nextPage(
            playlistId: String,
            continuation: String,
        ): Pair<List<Video>, String?>? = fetch { YouTube.videoPlaylistPage(playlistId, continuation) }?.let { it.videos to it.continuation }

        /** Every video, page by page, for a caller that needs the whole list (syncing a saved copy). */
        suspend fun complete(playlistId: String): RemotePlaylistPage? {
            cachedComplete(playlistId)?.let { return it }
            val first = firstPage(playlistId) ?: return null
            val videos = first.videos.toMutableList()
            var token = first.continuation
            var pages = 1
            while (token != null && pages < MAX_PAGES) {
                val (more, next) = nextPage(playlistId, token) ?: break
                videos += more
                token = next.takeIf { more.isNotEmpty() }
                pages++
            }
            val complete = first.copy(videos = videos.distinctBy { it.id }, continuation = null)
            rememberComplete(playlistId, complete)
            return complete
        }

        private suspend fun fetch(block: suspend () -> Result<VideoPlaylistPage>): VideoPlaylistPage? =
            withContext(PerformanceDispatcher.networkIO) {
                block()
                    .onFailure { if (it is CancellationException) throw it }
                    .onFailure { Log.w(TAG, "Playlist request failed: ${it.message}") }
                    .getOrNull()
            }

        companion object {
            /** Upper bound on pages walked for one playlist, about 6,000 videos. */
            const val PAGE_LIMIT = MAX_PAGES
        }
    }
