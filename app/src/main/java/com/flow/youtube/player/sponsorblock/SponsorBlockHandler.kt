package com.flow.youtube.player.sponsorblock

import android.util.Log
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
    
    private val _skipEvent = MutableSharedFlow<SponsorBlockSegment>(extraBufferCapacity = 1)
    val skipEvent: SharedFlow<SponsorBlockSegment> = _skipEvent.asSharedFlow()

    private var loadJob: Job? = null
    private var lastSkippedSegmentUuid: String? = null
    var isEnabled: Boolean = false
        private set
    
    /**
     * Set whether SponsorBlock is enabled.
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) {
            reset()
        }
    }
    
    /**
     * Load SponsorBlock segments for a video.
     */
    fun loadSegments(videoId: String) {
        if (!isEnabled) return
        
        // Cancel previous load and clear state
        loadJob?.cancel()
        _sponsorSegments.value = emptyList()
        lastSkippedSegmentUuid = null
        
        loadJob = scope.launch {
            try {
                val segments = sponsorBlockRepository.getSegments(videoId)
                _sponsorSegments.value = segments
                Log.d(TAG, "Loaded ${segments.size} segments for video $videoId")
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
    }
    
    /**
     * Check if we need to skip a segment at the given position.
     * Returns the seek position in milliseconds if a skip is needed, null otherwise.
     */
    fun checkForSkip(currentPositionMs: Long): Long? {
        if (!isEnabled) return null
        val segments = _sponsorSegments.value
        if (segments.isEmpty()) return null
        
        val posSec = currentPositionMs / 1000f
        val segment = segments.find { 
            posSec >= it.startTime && posSec < it.endTime 
        }
        
        if (segment != null && segment.uuid != lastSkippedSegmentUuid) {
            Log.d(TAG, "Skipping ${segment.category} from ${segment.startTime} to ${segment.endTime}")
            lastSkippedSegmentUuid = segment.uuid
            _skipEvent.tryEmit(segment)
            return (segment.endTime * 1000).toLong()
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
