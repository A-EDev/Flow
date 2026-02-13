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
    private var currentVideoId: String? = null
    
    var isEnabled: Boolean = false
        private set
    
    /**
     * Set whether SponsorBlock is enabled.
     */
    fun setEnabled(enabled: Boolean) {
        if (isEnabled != enabled) {
            isEnabled = enabled
            if (enabled) {
                currentVideoId?.let { loadSegments(it) }
            } else {
                // components.reset() // Removed reset() call to keep currentVideoId
                loadJob?.cancel()
                _sponsorSegments.value = emptyList()
                lastSkippedSegmentUuid = null
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
        
        loadJob = scope.launch {
            try {
                val segments = sponsorBlockRepository.getSegments(videoId)
                _sponsorSegments.value = segments
                Log.d(TAG, "Loaded ${segments.size} segments for video $videoId")
                segments.forEach { 
                    Log.d(TAG, "Segment: ${it.category} [${it.startTime} - ${it.endTime}] (Current ID: $currentVideoId)")
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
        currentVideoId = null
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
        
        // Find a segment that overlaps with current position
        val segment = segments.find { 
            posSec >= it.startTime && posSec < it.endTime 
        }
        
        // If we are before the start of the last skipped segment, reset it so we can skip it again (seek back support)
        if (lastSkippedSegmentUuid != null) {
            val lastSegment = segments.find { it.uuid == lastSkippedSegmentUuid }
            if (lastSegment != null && posSec < lastSegment.startTime) {
                Log.d(TAG, "Seek back detected, resetting last skipped segment: ${lastSegment.category}")
                lastSkippedSegmentUuid = null
            }
        }
        
        if (segment != null && segment.uuid != lastSkippedSegmentUuid) {            
            Log.d(TAG, "Skipping ${segment.category} from ${segment.startTime} to ${segment.endTime} (action: ${segment.actionType})")
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
