package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video

/**
 * Process-lifetime in-memory cache for the Home feed.
 *
 * Survives ViewModel recreation (which happens when the user navigates away
 * from Home and comes back via the bottom nav), preventing an unwanted
 * network reload on every tab switch. The cache expires after [CACHE_TTL_MS]
 * (default 30 minutes) and is explicitly cleared when the user pulls-to-refresh.
 */
internal object HomeFeedCache {
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

    @Volatile var videos: List<Video> = emptyList()
        private set

    @Volatile var shorts: List<Video> = emptyList()
        private set

    @Volatile var timestamp: Long = 0L
        private set

    fun isFresh(): Boolean = videos.isNotEmpty() && (System.currentTimeMillis() - timestamp) < CACHE_TTL_MS

    fun update(
        newVideos: List<Video>,
        newShorts: List<Video>,
    ) {
        videos = newVideos
        shorts = newShorts.sortedByDescending { it.timestamp }
        timestamp = System.currentTimeMillis()
    }

    fun clear() {
        videos = emptyList()
        shorts = emptyList()
        timestamp = 0L
    }

    /**
     * Remove videos by blocked channel/topic from the cached feed without
     * requiring a network refetch, keeping the cache TTL alive.
     */
    fun filterOut(
        channelId: String? = null,
        videoId: String? = null,
    ) {
        if (channelId != null) {
            videos = videos.filter { it.channelId != channelId }
            shorts = shorts.filter { it.channelId != channelId }
        }
        if (videoId != null) {
            videos = videos.filter { it.id != videoId }
            shorts = shorts.filter { it.id != videoId }
        }
    }
}
