package com.flow.youtube.player.sponsorblock

import android.util.Log
import com.flow.youtube.data.model.SponsorBlockSegment
import com.flow.youtube.data.repository.SponsorBlockRepository
import kotlinx.coroutines.CoroutineScope
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
    private var sponsorSegments: List<SponsorBlockSegment> = emptyList()
    private var lastSkippedSegmentUuid: String? = null
    var isEnabled: Boolean = false
        private set
    
    /**
     * Set whether SponsorBlock is enabled.
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
    
    /**
     * Load SponsorBlock segments for a video.
     */
    fun loadSegments(videoId: String) {
        if (!isEnabled) return
        
        sponsorSegments = emptyList()
        lastSkippedSegmentUuid = null
        
        scope.launch {
            try {
                sponsorSegments = sponsorBlockRepository.getSegments(videoId)
                Log.d(TAG, "Loaded ${sponsorSegments.size} segments for video $videoId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load segments for video $videoId", e)
            }
        }
    }
    
    /**
     * Reset SponsorBlock state for a new video.
     */
    fun reset() {
        sponsorSegments = emptyList()
        lastSkippedSegmentUuid = null
    }
    
    /**
     * Check if we need to skip a segment at the given position.
     * Returns the seek position in milliseconds if a skip is needed, null otherwise.
     */
    fun checkForSkip(currentPositionMs: Long): Long? {
        if (!isEnabled || sponsorSegments.isEmpty()) return null
        
        val posSec = currentPositionMs / 1000f
        val segment = sponsorSegments.find { 
            posSec >= it.startTime && posSec < it.endTime 
        }
        
        if (segment != null && segment.uuid != lastSkippedSegmentUuid) {
            Log.d(TAG, "Skipping ${segment.category} from ${segment.startTime} to ${segment.endTime}")
            lastSkippedSegmentUuid = segment.uuid
            return (segment.endTime * 1000).toLong()
        }
        
        return null
    }
    
    /**
     * Get the current segments list.
     */
    fun getSegments(): List<SponsorBlockSegment> = sponsorSegments
    
    /**
     * Check if segments have been loaded.
     */
    fun hasSegments(): Boolean = sponsorSegments.isNotEmpty()
}
