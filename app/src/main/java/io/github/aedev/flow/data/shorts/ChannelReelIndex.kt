package io.github.aedev.flow.data.shorts

import android.util.Log
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelReelIndex
    @Inject
    constructor() {
        private data class Entry(
            val reelIds: Set<String>,
            val fetchedAtMillis: Long,
        )

        private val lock = Mutex()
        private val cache = LinkedHashMap<String, Entry>()

        suspend fun markReels(
            channelId: String,
            videos: List<Video>,
            nowMillis: Long = System.currentTimeMillis(),
        ): List<Video> {
            if (channelId.isBlank() || videos.isEmpty()) return videos
            if (videos.all { it.isShort }) return videos

            val reelIds = reelIds(channelId, nowMillis) ?: return videos
            if (reelIds.isEmpty()) return videos
            return videos.map { video ->
                if (!video.isShort && video.id in reelIds) video.copy(isShort = true) else video
            }
        }

        suspend fun reelIds(
            channelId: String,
            nowMillis: Long = System.currentTimeMillis(),
        ): Set<String>? {
            lock
                .withLock {
                    cache[channelId]?.takeIf { nowMillis - it.fetchedAtMillis < CACHE_TTL_MS }
                }?.let { return it.reelIds }

            val page =
                YouTube.channelShorts(channelId).getOrElse { error ->
                    Log.w(TAG, "[$channelId] Shorts tab lookup failed: ${error::class.simpleName}: ${error.message}")
                    return null
                }
            val ids = page.shorts.mapTo(HashSet()) { it.id }

            lock.withLock {
                cache.remove(channelId)
                cache[channelId] = Entry(reelIds = ids, fetchedAtMillis = nowMillis)
                while (cache.size > MAX_CACHED_CHANNELS) {
                    cache.remove(cache.keys.first())
                }
            }
            return ids
        }

        private companion object {
            const val TAG = "ChannelReelIndex"
            const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
            const val MAX_CACHED_CHANNELS = 400
        }
    }
