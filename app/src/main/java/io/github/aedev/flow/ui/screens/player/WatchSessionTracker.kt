package io.github.aedev.flow.ui.screens.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.aedev.flow.data.local.CachedHomeVideo
import io.github.aedev.flow.data.local.HomeFeedCacheRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.stats.VideoStatsRecorder
import io.github.aedev.flow.data.stats.ViewEvent
import io.github.aedev.flow.data.stats.ViewFormat
import io.github.aedev.flow.player.PlayerRelatedVideosPolicy
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** Below this, an abandoned view is navigation noise rather than a deliberate skip. */
private const val MIN_SKIP_SIGNAL_POSITION_MS = 10_000L

/** At or above this share of the video, the view counts as watched rather than skipped. */
private const val WATCHED_FRACTION = 0.20

private const val RELATED_PREWARM_TIMEOUT_MS = 4_000L

/** Longest gap between two progress reports that still counts as continuous watching. */
private const val MAX_WATCH_STEP_MS = 30_000L

/** A live stream has no length to grade against; this much watching makes it a view. */
private const val LIVE_VIEW_MS = 60_000L

/**
 * Real time spent watching between two progress reports: the wall-clock gap, but never more than
 * the position actually advanced. A seek forward adds only the elapsed time, a pause adds nothing,
 * and faster playback counts the minutes the viewer really spent.
 */
internal fun watchedStep(
    wallDeltaMs: Long,
    positionDeltaMs: Long,
): Long = minOf(wallDeltaMs.coerceIn(0L, MAX_WATCH_STEP_MS), positionDeltaMs.coerceAtLeast(0L))

/** What a finished session adds to the recap ledger, or null when it left nothing to count. */
internal fun viewEventFor(
    video: Video,
    format: ViewFormat,
    watchedMs: Long,
    signal: WatchSignal?,
): ViewEvent? {
    val counted =
        when (format) {
            ViewFormat.LIVE -> watchedMs >= LIVE_VIEW_MS
            else -> signal?.type == InteractionType.WATCHED
        }
    val skipped = format != ViewFormat.LIVE && signal?.type == InteractionType.SKIPPED
    if (!counted && !skipped && watchedMs <= 0L) return null
    return ViewEvent(
        videoId = video.id,
        title = video.title,
        channelId = video.channelId,
        channelName = video.channelName,
        format = format,
        watchedMs = watchedMs,
        counted = counted,
        skipped = skipped,
        channelAvatarUrl = video.channelThumbnailUrl,
    )
}

/** The terminal learning signal a view earned, with the share of the video it covered. */
internal class WatchSignal(
    val type: InteractionType,
    val fractionWatched: Float,
)

/**
 * Grades a finished view. Anything below [WATCHED_FRACTION] that still ran past
 * [MIN_SKIP_SIGNAL_POSITION_MS] is a real abandonment; a shorter bounce carries no signal at all,
 * and neither does a video of unknown length.
 *
 * At or above the threshold the signal is WATCHED, whose percent-scaled learning already grades a
 * 20-40% view as weak-positive — no separate tier is needed.
 */
internal fun watchSignalFor(
    positionMs: Long,
    durationMs: Long,
): WatchSignal? {
    if (durationMs <= 0L) return null
    val fraction = positionMs.toDouble() / durationMs
    if (fraction < WATCHED_FRACTION && positionMs < MIN_SKIP_SIGNAL_POSITION_MS) return null
    val type = if (fraction >= WATCHED_FRACTION) InteractionType.WATCHED else InteractionType.SKIPPED
    return WatchSignal(type, fraction.toFloat())
}

/**
 * Everything a view leaves behind: the history row, the resume position, the one terminal signal
 * the recommendation engine learns from, and the related-video prewarm that fills the home feed's
 * reserve while the user is still watching.
 *
 * A session spans one video: positions are folded into it as they arrive, and it is graded exactly
 * once — when the next video takes over, or when the screen goes away.
 */
internal class WatchSessionTracker(
    private val context: Context,
    private val viewHistory: ViewHistory,
    private val repository: YouTubeRepository,
    private val homeFeedCacheRepository: HomeFeedCacheRepository,
    private val videoStats: VideoStatsRecorder,
    private val scope: CoroutineScope,
    private val networkDispatcher: CoroutineDispatcher,
    private val shortsEnabled: () -> Boolean,
    private val relatedVideosFor: (String) -> List<Video>,
    private val richVideoFor: (String) -> Video?,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private inner class Session(
        val video: Video,
        var maxPositionMs: Long,
        var durationMs: Long,
        val format: ViewFormat,
        var lastPositionMs: Long,
        var lastWallMs: Long = elapsedRealtime(),
        var watchedMs: Long = 0L,
    ) {
        fun advance(positionMs: Long) {
            val wall = elapsedRealtime()
            watchedMs += watchedStep(wall - lastWallMs, positionMs - lastPositionMs)
            lastWallMs = wall
            lastPositionMs = positionMs
        }
    }

    private var session: Session? = null
    private var lastReportedVideoId: String? = null
    private val prewarmedVideoIds = ConcurrentHashMap.newKeySet<String>()

    fun saveHistoryEntry(video: Video) {
        if (video.id.startsWith("recovered_")) return
        scope.launch {
            viewHistory.touchHistoryEntry(
                videoId = video.id,
                duration = if (video.duration > 0) video.duration * 1000L else 0L,
                title = video.title,
                thumbnailUrl =
                    video.thumbnailUrl.takeIf { it.isNotEmpty() }
                        ?: ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(video.id),
                channelName = video.channelName,
                channelId = video.channelId,
                isShort = video.isShort,
            )
        }
    }

    fun savePlaybackPosition(
        videoId: String,
        positionMs: Long,
        durationMs: Long,
        title: String,
        thumbnailUrl: String,
        channelName: String,
        channelId: String,
        isShort: Boolean,
        isLocal: Boolean,
    ) {
        scope.launch {
            viewHistory.savePlaybackPosition(
                videoId = videoId,
                position = positionMs,
                duration = durationMs,
                title = title,
                thumbnailUrl = thumbnailUrl,
                channelName = channelName,
                channelId = channelId,
                isShort = isShort,
                isLocal = isLocal,
            )
        }
        if (!isLocal && durationMs > 0) {
            track(videoId, positionMs, durationMs, title, thumbnailUrl, channelName, channelId, isShort)
        }
        maybePrewarmRelated(
            videoId = videoId,
            positionMs = positionMs,
            durationMs = durationMs,
            isShort = isShort,
            isLocal = isLocal,
        )
    }

    /** Persists a resume point without opening or grading a session; the error recovery path. */
    suspend fun saveResumePosition(
        videoId: String,
        positionMs: Long,
        durationMs: Long,
        video: Video?,
    ) {
        viewHistory.savePlaybackPosition(
            videoId = videoId,
            position = positionMs,
            duration = durationMs,
            title = video?.title.orEmpty(),
            thumbnailUrl = video?.thumbnailUrl.orEmpty(),
            channelName = video?.channelName.orEmpty(),
            channelId = video?.channelId.orEmpty(),
            isShort = video?.isShort == true,
        )
    }

    /**
     * Live streams write no history row and earn no engine signal, but their watching time still
     * belongs in the recap. The progress loop reports them here instead of [savePlaybackPosition].
     */
    fun trackLive(
        video: Video,
        positionMs: Long,
    ) {
        if (video.id.startsWith("recovered_")) return
        val current = session
        if (current != null && current.video.id == video.id) {
            current.advance(positionMs)
            return
        }
        current?.let(::finalize)
        session = Session(video, maxPositionMs = positionMs, durationMs = 0L, format = ViewFormat.LIVE, lastPositionMs = positionMs)
    }

    /** Grades whatever session is open — the screen is going away and nothing else will. */
    fun finalizeActiveSession() {
        session?.let(::finalize)
        session = null
    }

    private fun track(
        videoId: String,
        positionMs: Long,
        durationMs: Long,
        title: String,
        thumbnailUrl: String,
        channelName: String,
        channelId: String,
        isShort: Boolean,
    ) {
        val current = session
        if (current != null && current.video.id == videoId) {
            current.maxPositionMs = maxOf(current.maxPositionMs, positionMs)
            current.durationMs = maxOf(current.durationMs, durationMs)
            current.advance(positionMs)
            return
        }
        current?.let(::finalize)
        session =
            Session(
                video =
                    Video(
                        id = videoId,
                        title = title,
                        channelName = channelName,
                        channelId = channelId,
                        thumbnailUrl = thumbnailUrl,
                        duration = (durationMs / 1000L).toInt(),
                        viewCount = 0,
                        uploadDate = "",
                        isShort = isShort,
                    ),
                maxPositionMs = positionMs,
                durationMs = durationMs,
                format = if (isShort) ViewFormat.SHORT else ViewFormat.LONG,
                lastPositionMs = positionMs,
            )
    }

    private fun finalize(session: Session) {
        // Prefer the rich (tags/description) video the screen still holds over the session stub.
        val video = richVideoFor(session.video.id) ?: session.video
        val signal = watchSignalFor(session.maxPositionMs, session.durationMs)
        viewEventFor(video, session.format, session.watchedMs, signal)?.let { videoStats.onView(it, video) }

        // Shorts played here teach the engine through the Shorts classifier's rules, not these.
        if (session.format != ViewFormat.LONG || signal == null || video.id == lastReportedVideoId) return

        lastReportedVideoId = video.id

        // Engine-scope dispatch: survives ViewModel teardown.
        FlowNeuroEngine.onVideoInteractionAsync(
            context,
            video,
            signal.type,
            percentWatched = signal.fractionWatched,
        )
    }

    private fun maybePrewarmRelated(
        videoId: String,
        positionMs: Long,
        durationMs: Long,
        isShort: Boolean,
        isLocal: Boolean,
    ) {
        val alreadyPrewarmed = videoId in prewarmedVideoIds
        if (!shouldPrewarmRelatedPlayback(videoId, positionMs, durationMs, isShort, isLocal, alreadyPrewarmed)) {
            return
        }
        if (!prewarmedVideoIds.add(videoId)) return

        val playerRelated = relatedVideosFor(videoId)
        scope.launch(networkDispatcher) {
            runCatching {
                val related =
                    PlayerRelatedVideosPolicy.sanitize(
                        videoId = videoId,
                        candidates =
                            playerRelated.ifEmpty {
                                withTimeoutOrNull(RELATED_PREWARM_TIMEOUT_MS) {
                                    repository.getRelatedCandidates(videoId)
                                }.orEmpty()
                            },
                        shortsEnabled = shortsEnabled(),
                    )

                if (related.isEmpty()) return@runCatching

                homeFeedCacheRepository.saveRelated(videoId, related)
                homeFeedCacheRepository.saveReserve(
                    related.map { video ->
                        CachedHomeVideo(
                            video = video,
                            source = HomeFeedCacheRepository.SOURCE_RELATED,
                            relatedSeedId = videoId,
                        )
                    },
                )
            }.onFailure { error ->
                Log.w("WatchSessionTracker", "Related prewarm failed for $videoId", error)
            }
        }
    }
}
