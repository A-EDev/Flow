package io.github.aedev.flow.data.shorts

import android.util.Log
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
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

        // A subscription sweep asks this for every channel. Unbounded, those browses took all of the
        // client's per-host slots and starved search, related lanes and the player behind them.
        private val lookups = Semaphore(MAX_CONCURRENT_LOOKUPS)
        private val cache = LinkedHashMap<String, Entry>()

        /** Null when the Shorts tab could not be read: the slice is then unclassified, not reel-free. */
        suspend fun markReels(
            channelId: String,
            videos: List<Video>,
            nowMillis: Long = System.currentTimeMillis(),
        ): List<Video>? {
            if (channelId.isBlank() || videos.isEmpty()) return videos
            if (videos.all { it.isShort }) return videos

            val reelIds = reelIds(channelId, nowMillis) ?: return null
            return videos.markedWith(reelIds)
        }

        /** [markReels] from the cache alone; null when the channel would need a lookup. */
        suspend fun markReelsIfCached(
            channelId: String,
            videos: List<Video>,
            nowMillis: Long = System.currentTimeMillis(),
        ): List<Video>? {
            if (channelId.isBlank() || videos.isEmpty()) return videos
            if (videos.all { it.isShort }) return videos
            val reelIds =
                lock.withLock {
                    cache[channelId]?.takeIf { nowMillis - it.fetchedAtMillis < CACHE_TTL_MS }?.reelIds
                } ?: return null
            return videos.markedWith(reelIds)
        }

        private fun List<Video>.markedWith(reelIds: Set<String>): List<Video> {
            if (reelIds.isEmpty()) return this
            return map { video ->
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

            // The wait for a slot counts against the timeout, so a queue behind slow lookups cannot
            // hold a refresh past it.
            val result =
                withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { lookups.withPermit { YouTube.channelShorts(channelId) } }
                    ?: run {
                        Log.w(TAG, "[$channelId] Shorts tab lookup timed out")
                        return null
                    }
            val page =
                result.getOrElse { error ->
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
            const val MAX_CONCURRENT_LOOKUPS = 4
            const val LOOKUP_TIMEOUT_MS = 8_000L
        }
    }
