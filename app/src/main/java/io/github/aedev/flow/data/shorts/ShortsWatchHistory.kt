package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.local.dao.WatchProgress
import io.github.aedev.flow.data.model.ShortVideo
import javax.inject.Inject

/** Past this fraction a Short is finished, and history records it as played to the end. */
const val SHORT_COMPLETE_FRACTION = 0.9f

internal fun isShortComplete(
    positionMs: Long,
    durationMs: Long,
): Boolean = durationMs > 0L && positionMs >= (durationMs * SHORT_COMPLETE_FRACTION).toLong()

/**
 * The reels every Shorts filter hides. It is the same line the card badge and [ShortsWatchHistory]
 * draw, not the video watched threshold: under 95 or 99 % a reel saved at 92 % showed as watched
 * but stayed on the shelf (#979).
 */
internal fun List<WatchProgress>.finishedShortIds(): Set<String> =
    filter { isShortComplete(it.position, it.duration) }.mapTo(HashSet()) { it.videoId }

/** The position history stores: the whole length once the Short is finished. */
internal fun shortHistoryPosition(
    positionMs: Long,
    durationMs: Long,
): Long = if (isShortComplete(positionMs, durationMs)) durationMs else positionMs.coerceIn(0L, durationMs)

/**
 * Writes a Short's watch progress. History is what hides a watched Short (#979), so a finished Short
 * is stored as complete, and watching it again never lowers it: a looping Short never ends, so the
 * next playback's first save would otherwise put it back at a few percent.
 */
class ShortsWatchHistory
    @Inject
    constructor(
        private val viewHistory: ViewHistory,
    ) {
        suspend fun save(
            short: ShortVideo,
            positionMs: Long,
            durationMs: Long,
        ) {
            if (durationMs <= 0L) return
            val existing = viewHistory.getWatchProgress(short.id)
            if (existing != null && isShortComplete(existing.position, existing.duration)) {
                // Rows an older build saved short of the end are finished too; store them as such.
                if (existing.position < existing.duration) viewHistory.markCompleted(short.id, existing.duration)
                viewHistory.touchHistoryEntry(
                    videoId = short.id,
                    title = short.title,
                    thumbnailUrl = short.thumbnailUrl,
                    channelName = short.channelName,
                    channelId = short.channelId,
                    duration = existing.duration,
                    isShort = true,
                )
                return
            }
            viewHistory.savePlaybackPosition(
                videoId = short.id,
                position = shortHistoryPosition(positionMs, durationMs),
                duration = durationMs,
                title = short.title,
                thumbnailUrl = short.thumbnailUrl,
                channelName = short.channelName,
                channelId = short.channelId,
                isMusic = false,
                isShort = true,
            )
        }
    }
