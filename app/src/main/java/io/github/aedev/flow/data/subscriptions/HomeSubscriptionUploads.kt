package io.github.aedev.flow.data.subscriptions

import android.util.Log
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The background top-up of Home's subscription lane: the newest uploads of a window of the channels
 * the viewer follows, read from each channel's Videos tab. Channels whose stored uploads still lack a
 * length go first, then the rotation. All channels share one deadline and the ones that answered in
 * time are kept.
 */
@Singleton
class HomeSubscriptionUploads
    @Inject
    constructor(
        private val uploads: ChannelUploadsClient,
        private val playerPreferences: PlayerPreferences,
    ) {
        private val priorityAskedAt = ConcurrentHashMap<String, Long>()

        suspend fun fetch(
            subscriptions: List<FeedItemOwner>,
            priorityChannelIds: Set<String> = emptySet(),
            deadlineMillis: Long = DEADLINE_MS,
            now: Long = System.currentTimeMillis(),
        ): List<Video> {
            val channels = subscriptions.filter { it.id.startsWith("UC") }.distinctBy { it.id }.sortedBy { it.id }
            if (channels.isEmpty()) return emptyList()
            val size = homeSubsWindowSize(channels.size)
            // A priority channel whose missing length its Videos tab could not fill (an RSS reel, say)
            // is not asked again on every refresh.
            val priority =
                channels
                    .filter { owner ->
                        val askedAt = priorityAskedAt[owner.id]
                        owner.id in priorityChannelIds && (askedAt == null || now - askedAt > PRIORITY_RETRY_MS)
                    }.take(size / 2)
            priority.forEach { priorityAskedAt[it.id] = now }
            val cursor = playerPreferences.homeSubsRotationCursor.first()
            val rotationPool = channels - priority.toSet()
            val rotation = rotatingWindow(rotationPool, cursor, size - priority.size)
            playerPreferences.setHomeSubsRotationCursor(nextCursor(cursor, rotation.size, rotationPool.size))
            val window = priority + rotation

            val collected = ConcurrentLinkedQueue<Video>()
            val gate = Semaphore(CONCURRENCY)
            withTimeoutOrNull(deadlineMillis) {
                coroutineScope {
                    window.forEach { owner ->
                        launch {
                            gate.withPermit {
                                uploads.latest(owner, VIDEOS_PER_CHANNEL).onSuccess { collected += it }
                            }
                        }
                    }
                }
            }
            val videos =
                collected
                    .filter { it.membersOnlyText == null }
                    .distinctBy { it.id }
                    .sortedByDescending { it.timestamp }
            Log.d(
                TAG,
                "Home subs: ${window.size} of ${channels.size} channels asked (${priority.size} missing lengths), ${videos.size} uploads",
            )
            return videos
        }

        private companion object {
            const val TAG = "HomeSubscriptionUploads"
            const val DEADLINE_MS = 20_000L
            const val PRIORITY_RETRY_MS = 30L * 60L * 1000L
            const val CONCURRENCY = 6

            // The whole first page: it costs the same request and busy channels post more than a few a day.
            const val VIDEOS_PER_CHANNEL = 30
        }
    }

/** How many followed channels one Home refresh asks: all of a small list, a rotating slice of a big one. */
internal fun homeSubsWindowSize(channelCount: Int): Int =
    when {
        channelCount <= 10 -> channelCount
        channelCount <= 60 -> 14
        else -> 18
    }

internal fun <T> rotatingWindow(
    items: List<T>,
    start: Int,
    count: Int,
): List<T> {
    if (items.isEmpty() || count <= 0) return emptyList()
    if (items.size <= count) return items
    val first = start.coerceIn(0, items.lastIndex)
    return List(count) { items[(first + it) % items.size] }
}

internal fun nextCursor(
    cursor: Int,
    windowSize: Int,
    channelCount: Int,
): Int = if (channelCount == 0) 0 else (cursor.coerceIn(0, channelCount - 1) + windowSize) % channelCount
