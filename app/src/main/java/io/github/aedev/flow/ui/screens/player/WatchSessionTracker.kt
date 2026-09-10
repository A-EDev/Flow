package io.github.aedev.flow.ui.screens.player

import android.content.Context
import android.util.Log
import io.github.aedev.flow.data.local.CachedHomeVideo
import io.github.aedev.flow.data.local.HomeFeedCacheRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.repository.YouTubeRepository
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
    private val scope: CoroutineScope,
    private val networkDispatcher: CoroutineDispatcher,
    private val shortsEnabled: () -> Boolean,
    private val relatedVideosFor: (String) -> List<Video>,
    private val richVideoFor: (String) -> Video?,
) {
    private class Session(
        val video: Video,
        var maxPositionMs: Long,
        var durationMs: Long,
    )

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
        if (!isLocal && !isShort && durationMs > 0) {
            track(videoId, positionMs, durationMs, title, thumbnailUrl, channelName, channelId)
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
    ) {
        val current = session
        if (current != null && current.video.id == videoId) {
            current.maxPositionMs = maxOf(current.maxPositionMs, positionMs)
            current.durationMs = maxOf(current.durationMs, durationMs)
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
                    ),
                maxPositionMs = positionMs,
                durationMs = durationMs,
            )
    }

    private fun finalize(session: Session) {
        // Prefer the rich (tags/description) video the screen still holds over the session stub.
        val video = richVideoFor(session.video.id) ?: session.video
        if (video.id == lastReportedVideoId) return
        val signal = watchSignalFor(session.maxPositionMs, session.durationMs) ?: return

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
