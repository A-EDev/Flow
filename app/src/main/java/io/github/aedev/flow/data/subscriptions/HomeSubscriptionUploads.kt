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
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The live half of Home's subscription lane: the newest uploads of a rotating window of the channels
 * the viewer follows, read from each channel's Videos tab (and Shorts tab when the Home shelf wants
 * reels). All channels share one deadline and the ones that answered in time are kept, so a slow
 * network thins the lane instead of emptying it.
 */
@Singleton
class HomeSubscriptionUploads
    @Inject
    constructor(
        private val uploads: ChannelUploadsClient,
        private val playerPreferences: PlayerPreferences,
    ) {
        suspend fun fetch(
            subscriptions: List<FeedItemOwner>,
            includeShorts: Boolean,
            deadlineMillis: Long = DEADLINE_MS,
        ): List<Video> {
            val channels = subscriptions.filter { it.id.startsWith("UC") }.distinctBy { it.id }.sortedBy { it.id }
            if (channels.isEmpty()) return emptyList()
            val cursor = playerPreferences.homeSubsRotationCursor.first()
            val window = rotatingWindow(channels, cursor, homeSubsWindowSize(channels.size))
            playerPreferences.setHomeSubsRotationCursor(nextCursor(cursor, window.size, channels.size))

            val collected = ConcurrentLinkedQueue<Video>()
            val gate = Semaphore(CONCURRENCY)
            withTimeoutOrNull(deadlineMillis) {
                coroutineScope {
                    window.forEach { owner ->
                        launch {
                            gate.withPermit {
                                uploads
                                    .latest(owner, VIDEOS_PER_CHANNEL, if (includeShorts) SHORTS_PER_CHANNEL else 0)
                                    .onSuccess { collected += it }
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
            Log.d(TAG, "Home subs: ${window.size} of ${channels.size} channels asked, ${videos.size} uploads")
            return videos
        }

        private companion object {
            const val TAG = "HomeSubscriptionUploads"
            const val DEADLINE_MS = 7_000L
            const val CONCURRENCY = 6
            const val VIDEOS_PER_CHANNEL = 5
            const val SHORTS_PER_CHANNEL = 3
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
