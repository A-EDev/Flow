package io.github.aedev.flow.player.sponsorblock

import android.util.Log
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.model.SponsorBlockCategories
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.repository.SponsorBlockRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles SponsorBlock segment loading and skip logic.
 *
 * Per-category actions are controlled by [categoryActions] map.
 * Supported actions: SKIP (seek to end), MUTE (emit mute/unmute events), SHOW_TOAST (notify only), IGNORE.
 */
class SponsorBlockHandler(
    private val scope: CoroutineScope,
    private val apiSegments: suspend (String) -> List<SponsorBlockSegment> = { videoId ->
        SponsorBlockRepository().getSegments(videoId)
    },
    private val fallbackSegments: suspend (String) -> List<SponsorBlockSegment> = { emptyList() },
) {
    companion object {
        private const val TAG = "SponsorBlockHandler"

        /** A skip only re-arms for a rewind this far before the segment, so a seek that lands slightly
         * short of the requested end (SABR rebuilds at a segment boundary) cannot loop the skip. */
        private const val SEEK_BACK_REARM_MARGIN_SEC = 1f
    }

    private val _sponsorSegments = MutableStateFlow<List<SponsorBlockSegment>>(emptyList())
    val sponsorSegments: StateFlow<List<SponsorBlockSegment>> = _sponsorSegments.asStateFlow()

    /** Emitted when a segment should be skipped (seeked past). */
    private val _skipEvent = MutableSharedFlow<SponsorBlockSegment>(extraBufferCapacity = 1)
    val skipEvent: SharedFlow<SponsorBlockSegment> = _skipEvent.asSharedFlow()

    /** Emitted when entering a MUTE segment (true) or leaving one (false). */
    private val _muteEvent = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val muteEvent: SharedFlow<Boolean> = _muteEvent.asSharedFlow()

    /** Emitted when a SHOW_TOAST segment is encountered. */
    private val _toastEvent = MutableSharedFlow<SponsorBlockSegment>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<SponsorBlockSegment> = _toastEvent.asSharedFlow()

    private var loadJob: Job? = null
    private var lastSkippedSegmentUuid: String? = null
    private var currentMutedSegmentUuid: String? = null
    private var currentVideoId: String? = null

    /** True when segments were loaded via [loadSegmentsFromList] (offline DB). Prevents
     * [setEnabled] from wiping them with a network refresh that will fail offline.
     */
    private var offlineSegmentsLoaded: Boolean = false

    var isEnabled: Boolean = false
        private set

    /** Category id to action; a category missing here takes [SponsorBlockCategories.defaultAction]. */
    var categoryActions: Map<String, SponsorBlockAction> = emptyMap()

    /** Callback invoked whenever new segments are resolved and published. */
    var onSegmentsLoaded: ((List<SponsorBlockSegment>) -> Unit)? = null

    /**
     * Set whether SponsorBlock is enabled.
     */
    fun setEnabled(enabled: Boolean) {
        if (isEnabled != enabled) {
            isEnabled = enabled
            if (enabled) {
                if (!offlineSegmentsLoaded) {
                    currentVideoId?.let { loadSegments(it) }
                } else {
                    Log.d(TAG, "setEnabled(true): keeping offline segments, skipping network refresh")
                }
            } else {
                loadJob?.cancel()
                _sponsorSegments.value = emptyList()
                lastSkippedSegmentUuid = null
                currentMutedSegmentUuid = null
                offlineSegmentsLoaded = false
            }
        }
    }

    /**
     * Load SponsorBlock segments directly from a pre-fetched list (e.g. saved offline).
     * Bypasses the network API call. Safe to call even when [isEnabled] is false —
     * the segments are stored and will be used if SponsorBlock is later enabled.
     */
    fun loadSegmentsFromList(
        videoId: String,
        segments: List<SponsorBlockSegment>,
    ) {
        currentVideoId = videoId
        loadJob?.cancel()
        lastSkippedSegmentUuid = null
        currentMutedSegmentUuid = null
        offlineSegmentsLoaded = segments.isNotEmpty()
        _sponsorSegments.value = segments
        Log.d(TAG, "Loaded ${segments.size} offline SponsorBlock segments for video $videoId")
        onSegmentsLoaded?.invoke(segments)
    }

    /**
     * Apply early streaming model spans while full inference is still running.
     * Only affects the in-memory segment list; the final load below overwrites
     * it, and offline-saved state is never touched.
     */
    fun setProvisionalSegments(
        videoId: String,
        segments: List<SponsorBlockSegment>,
    ) {
        if (!isEnabled || currentVideoId != videoId || segments.isEmpty()) return
        _sponsorSegments.value = segments
        Log.d(TAG, "Applied ${segments.size} provisional on-device segments for video $videoId")
        onSegmentsLoaded?.invoke(segments)
    }

    /**
     * Load SponsorBlock segments for a video.
     */
    fun loadSegments(videoId: String) {
        currentVideoId = videoId

        if (!isEnabled) {
            Log.d(TAG, "loadSegments($videoId) ignored: SponsorBlock disabled")
            return
        }
        Log.d(TAG, "loadSegments($videoId) started")

        // Cancel previous load and clear state
        loadJob?.cancel()
        _sponsorSegments.value = emptyList()
        lastSkippedSegmentUuid = null
        currentMutedSegmentUuid = null

        loadJob =
            scope.launch {
                try {
                    val remoteSegments = apiSegments(videoId)
                    val segments = remoteSegments.ifEmpty { fallbackSegments(videoId) }
                    _sponsorSegments.value = segments
                    Log.d(
                        TAG,
                        "Loaded ${segments.size} ${if (remoteSegments.isEmpty()) "on-device" else "API"} segments for video $videoId",
                    )
                    segments.forEach {
                        Log.d(TAG, "Segment: ${it.category} [${it.startTime} - ${it.endTime}]")
                    }
                    onSegmentsLoaded?.invoke(segments)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load segments for video $videoId", e)
                }
            }
    }

    /**
     * Refetch [videoId]'s segments in place, e.g. after the user submitted one. The current list stays
     * on the seek bar until the new one arrives, and an empty answer (the fetch failed, or the server
     * has not published the submission yet) keeps it.
     */
    fun reloadSegments(videoId: String) {
        if (!isEnabled || videoId != currentVideoId) return
        loadJob?.cancel()
        loadJob =
            scope.launch {
                try {
                    val segments = apiSegments(videoId)
                    if (segments.isNotEmpty() && videoId == currentVideoId) {
                        offlineSegmentsLoaded = false
                        _sponsorSegments.value = segments
                    }
                    Log.d(TAG, "Reloaded ${segments.size} segments for video $videoId")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reload segments for video $videoId", e)
                }
            }
    }

    /**
     * Reset SponsorBlock state for a new video.
     */
    fun reset() {
        loadJob?.cancel()
        _sponsorSegments.value = emptyList()
        lastSkippedSegmentUuid = null
        currentMutedSegmentUuid = null
        currentVideoId = null
        offlineSegmentsLoaded = false
    }

    /**
     * Check if we need to act on a segment at the given position.
     * Returns the seek position in milliseconds if a SKIP is needed, null otherwise.
     * MUTE and SHOW_TOAST actions are handled via their respective flows.
     */
    fun checkForSkip(currentPositionMs: Long): Long? {
        if (!isEnabled && !offlineSegmentsLoaded) return null
        val segments = _sponsorSegments.value
        if (segments.isEmpty()) return null

        val posSec = currentPositionMs / 1000f

        // Handle seek-back: reset last skipped/muted segment if we've gone before it
        if (lastSkippedSegmentUuid != null) {
            val lastSegment = segments.find { it.uuid == lastSkippedSegmentUuid }
            if (lastSegment != null && posSec < lastSegment.startTime - SEEK_BACK_REARM_MARGIN_SEC) {
                Log.d(TAG, "Seek back detected, resetting last skipped segment: ${lastSegment.category}")
                lastSkippedSegmentUuid = null
            }
        }

        // Find a segment overlapping current position
        val segment = segments.find { posSec >= it.startTime && posSec < it.endTime }

        // Handle mute-segment exit
        if (currentMutedSegmentUuid != null) {
            val mutedSeg = segments.find { it.uuid == currentMutedSegmentUuid }
            if (mutedSeg == null || posSec >= mutedSeg.endTime || posSec < mutedSeg.startTime) {
                Log.d(TAG, "Exiting mute segment")
                currentMutedSegmentUuid = null
                _muteEvent.tryEmit(false)
            }
        }

        if (segment != null && segment.uuid != lastSkippedSegmentUuid) {
            val action = resolveSponsorBlockAction(segment, categoryActions)
            Log.d(TAG, "Segment hit: ${segment.category} action=$action type=${segment.actionType}")

            return when (action) {
                SponsorBlockAction.SKIP -> {
                    lastSkippedSegmentUuid = segment.uuid
                    _skipEvent.tryEmit(segment)
                    (segment.endTime * 1000).toLong()
                }

                SponsorBlockAction.MUTE -> {
                    if (currentMutedSegmentUuid != segment.uuid) {
                        currentMutedSegmentUuid = segment.uuid
                        _muteEvent.tryEmit(true)
                    }
                    null
                }

                SponsorBlockAction.SHOW_TOAST -> {
                    lastSkippedSegmentUuid = segment.uuid
                    _toastEvent.tryEmit(segment)
                    null
                }

                SponsorBlockAction.IGNORE -> {
                    null
                }
            }
        }

        return null
    }

    /**
     * Get the current segments list.
     */
    fun getSegments(): List<SponsorBlockSegment> = _sponsorSegments.value

    /**
     * Check if segments have been loaded.
     */
    fun hasSegments(): Boolean = _sponsorSegments.value.isNotEmpty()

    /**
     * Calculates the polling delay for playback position tracking.
     * When approaching an auto-skip segment within 2 seconds, the delay is reduced
     * so the skip triggers smoothly right at the segment boundary.
     */
    fun getNextSkipCheckDelayMs(currentPositionMs: Long): Long {
        if (!isEnabled && !offlineSegmentsLoaded) return 1_000L
        val segments = _sponsorSegments.value
        if (segments.isEmpty()) return 1_000L

        val posSec = currentPositionMs / 1000f

        val nextSeg =
            segments
                .filter { it.startTime > posSec && resolveSponsorBlockAction(it, categoryActions) == SponsorBlockAction.SKIP }
                .minByOrNull { it.startTime }

        if (nextSeg != null) {
            val remainingMs = ((nextSeg.startTime - posSec) * 1000).toLong()
            if (remainingMs in 1L..2_000L) {
                return remainingMs.coerceIn(50L, 250L)
            }
        }
        return 1_000L
    }
}

/**
 * Combines the user's per-category preference with the API [SponsorBlockSegment.actionType].
 *
 * `full` / `poi` / `chapter` mark the whole video or a highlight, not a skippable range —
 * auto-skip would seek to the end. API `mute` segments stay mute even if the category
 * default is skip.
 */
internal fun resolveSponsorBlockAction(
    segment: SponsorBlockSegment,
    categoryActions: Map<String, SponsorBlockAction>,
): SponsorBlockAction {
    val userAction = categoryActions[segment.category] ?: SponsorBlockCategories.defaultAction(segment.category)
    if (userAction == SponsorBlockAction.IGNORE) return SponsorBlockAction.IGNORE
    return when {
        SponsorBlockCategories.isWholeVideoAction(segment.actionType) -> {
            if (userAction == SponsorBlockAction.SKIP) SponsorBlockAction.SHOW_TOAST else userAction
        }

        SponsorBlockCategories.isMuteAction(segment.actionType) -> {
            SponsorBlockAction.MUTE
        }

        else -> {
            userAction
        }
    }
}
