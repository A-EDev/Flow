package com.flow.youtube.player.sponsorblock

import android.util.Log
import com.flow.youtube.data.local.SponsorBlockAction
import com.flow.youtube.data.model.SponsorBlockSegment
import com.flow.youtube.data.repository.SponsorBlockRepository
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
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SponsorBlockHandler"
    }

    private val sponsorBlockRepository = SponsorBlockRepository()

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

    var isEnabled: Boolean = false
        private set

    /** Map from category string (e.g. "sponsor") to the action to take. Defaults to SKIP for all. */
    var categoryActions: Map<String, SponsorBlockAction> = emptyMap()

    /**
     * Set whether SponsorBlock is enabled.
     */
    fun setEnabled(enabled: Boolean) {
        if (isEnabled != enabled) {
            isEnabled = enabled
            if (enabled) {
                currentVideoId?.let { loadSegments(it) }
            } else {
                loadJob?.cancel()
                _sponsorSegments.value = emptyList()
                lastSkippedSegmentUuid = null
                currentMutedSegmentUuid = null
            }
        }
    }

    /**
     * Load SponsorBlock segments for a video.
     */
    fun loadSegments(videoId: String) {
        currentVideoId = videoId

        if (!isEnabled) return

        // Cancel previous load and clear state
        loadJob?.cancel()
        _sponsorSegments.value = emptyList()
        lastSkippedSegmentUuid = null
        currentMutedSegmentUuid = null

        loadJob = scope.launch {
            try {
                val segments = sponsorBlockRepository.getSegments(videoId)
                _sponsorSegments.value = segments
                Log.d(TAG, "Loaded ${segments.size} segments for video $videoId")
                segments.forEach {
                    Log.d(TAG, "Segment: ${it.category} [${it.startTime} - ${it.endTime}]")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load segments for video $videoId", e)
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
    }

    /**
     * Check if we need to act on a segment at the given position.
     * Returns the seek position in milliseconds if a SKIP is needed, null otherwise.
     * MUTE and SHOW_TOAST actions are handled via their respective flows.
     */
    fun checkForSkip(currentPositionMs: Long): Long? {
        if (!isEnabled) return null
        val segments = _sponsorSegments.value
        if (segments.isEmpty()) return null

        val posSec = currentPositionMs / 1000f

        // Handle seek-back: reset last skipped/muted segment if we've gone before it
        if (lastSkippedSegmentUuid != null) {
            val lastSegment = segments.find { it.uuid == lastSkippedSegmentUuid }
            if (lastSegment != null && posSec < lastSegment.startTime) {
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
            val action = categoryActions[segment.category] ?: SponsorBlockAction.SKIP
            Log.d(TAG, "Segment hit: ${segment.category} action=$action")

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
                SponsorBlockAction.IGNORE -> null
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
}
