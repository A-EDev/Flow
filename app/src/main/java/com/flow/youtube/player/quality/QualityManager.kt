package com.flow.youtube.player.quality

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.flow.youtube.player.config.PlayerConfig
import com.flow.youtube.player.state.EnhancedPlayerState
import com.flow.youtube.player.state.QualityOption
import kotlinx.coroutines.flow.MutableStateFlow
import org.schabi.newpipe.extractor.stream.VideoStream

@UnstableApi
class QualityManager(
    private val bandwidthMeter: DefaultBandwidthMeter?,
    private val trackSelector: DefaultTrackSelector?,
    private val stateFlow: MutableStateFlow<EnhancedPlayerState>,
    private val onQualitySwitch: (VideoStream, Long) -> Unit // Callback for quality switch with position
) {
    companion object {
        private const val TAG = "QualityManager"
    }
    
    // Quality mode tracking
    var isAdaptiveQualityEnabled = true
        private set
    var manualQualityHeight: Int? = null
        private set
    
    // Adaptive quality monitoring
    var lastBandwidthCheckTime = 0L
        private set
    var lastAdaptiveQualityHeight = 0
        private set
    var consecutiveBufferingCount = 0
        private set
    
    // Available streams
    private var availableVideoStreams: List<VideoStream> = emptyList()
    private var currentVideoStream: VideoStream? = null
    
    // Track failed streams
    private val failedStreamUrls = mutableSetOf<String>()
    var streamErrorCount = 0
        private set
    
    /**
     * Update available video streams.
     */
    fun setAvailableStreams(streams: List<VideoStream>) {
        availableVideoStreams = streams.distinctBy { it.getContent() }.sortedByDescending { it.height }
    }
    
    /**
     * Set the current video stream.
     */
    fun setCurrentStream(stream: VideoStream?) {
        currentVideoStream = stream
        lastAdaptiveQualityHeight = stream?.height ?: 0
    }
    
    /**
     * Get the current video stream.
     */
    fun getCurrentStream(): VideoStream? = currentVideoStream
    
    /**
     * Reset quality state for a new video.
     */
    fun resetForNewVideo() {
        failedStreamUrls.clear()
        streamErrorCount = 0
        currentVideoStream = null
        isAdaptiveQualityEnabled = true
        manualQualityHeight = null
        consecutiveBufferingCount = 0
        applyAdaptiveTrackSelectorDefaults()
    }
    
    /**
     * Mark a stream URL as failed.
     */
    fun markStreamFailed(url: String) {
        failedStreamUrls.add(url)
        streamErrorCount++
        Log.w(TAG, "Stream marked as failed: $url - Error count: $streamErrorCount")
    }
    
    /**
     * Check if max stream errors have been reached.
     */
    fun hasReachedMaxErrors(): Boolean = streamErrorCount >= PlayerConfig.MAX_STREAM_ERRORS
    
    /**
     * Reset stream error count.
     */
    fun resetStreamErrors() {
        streamErrorCount = 0
    }
    
    /**
     * Check if a stream URL has failed.
     */
    fun hasStreamFailed(url: String): Boolean = failedStreamUrls.contains(url)
    
    /**
     * Get all working streams (streams that haven't failed).
     */
    fun getWorkingStreams(): List<VideoStream> = 
        availableVideoStreams.filter { !failedStreamUrls.contains(it.getContent()) }
    
    /**
     * Select smart initial quality based on bandwidth.
     */
    fun selectSmartInitialQuality(): VideoStream? {
        val estimatedBandwidth = bandwidthMeter?.bitrateEstimate ?: 2_000_000L
        val targetHeight = PlayerConfig.calculateInitialQualityTarget(estimatedBandwidth)
        
        val smartStream = availableVideoStreams
            .sortedBy { kotlin.math.abs(it.height - targetHeight) }
            .firstOrNull()
        
        Log.d(TAG, "Smart quality selection: bandwidth=${estimatedBandwidth/1_000_000}Mbps, target=${targetHeight}p, selected=${smartStream?.height}p")
        
        return smartStream
    }
    
    /**
     * Build quality options list for UI.
     */
    fun buildQualityOptions(): List<QualityOption> {
        return listOf(
            QualityOption(height = 0, label = "Auto", bitrate = 0L)
        ) + availableVideoStreams
            .distinctBy { it.height }
            .sortedByDescending { it.height }
            .map { 
                QualityOption(
                    height = it.height,
                    label = "${it.height}p",
                    bitrate = it.bitrate.toLong()
                )
            }
    }
    
    /**
     * Switch quality by height. Height 0 means auto (adaptive).
     */
    fun switchQualityByHeight(height: Int, currentPosition: Long): Boolean {
        // Height 0 means Auto (adaptive quality)
        if (height == 0) {
            enableAdaptiveQuality(currentPosition)
            return true
        }
        
        // Check if we're already at this quality
        if (manualQualityHeight == height) {
            Log.d(TAG, "Already at ${height}p, no change needed")
            return false
        }
        
        // Disable adaptive and set fixed quality
        isAdaptiveQualityEnabled = false
        manualQualityHeight = height

        Log.d(TAG, "Switching to FIXED quality: ${height}p")
        
        // Find the stream matching this height
        val targetStream = availableVideoStreams.find { it.height == height }
        if (targetStream == null) {
            Log.w(TAG, "No stream found for ${height}p, available: ${availableVideoStreams.map { it.height }}")
            return false
        }
        
        currentVideoStream = targetStream
        onQualitySwitch(targetStream, currentPosition)
        
        stateFlow.value = stateFlow.value.copy(currentQuality = height, effectiveQuality = height)
        return true
    }
    
    /**
     * Enable adaptive quality mode.
     */
    private fun enableAdaptiveQuality(currentPosition: Long) {
        isAdaptiveQualityEnabled = true
        manualQualityHeight = null
        
        Log.d(TAG, "Enabling adaptive quality mode")
        
        val estimatedBandwidth = bandwidthMeter?.bitrateEstimate ?: 2_000_000L
        val targetHeight = PlayerConfig.calculateInitialQualityTarget(estimatedBandwidth)
        
        Log.d(TAG, "Auto quality: Estimated bandwidth ${estimatedBandwidth/1_000_000}Mbps -> targeting ${targetHeight}p")
        
        val targetStream = availableVideoStreams
            .sortedBy { kotlin.math.abs(it.height - targetHeight) }
            .firstOrNull()
        
        if (targetStream != null && targetStream.height != currentVideoStream?.height) {
            currentVideoStream = targetStream
            onQualitySwitch(targetStream, currentPosition)
            
            stateFlow.value = stateFlow.value.copy(
                currentQuality = 0,
                effectiveQuality = targetStream.height
            )
            lastAdaptiveQualityHeight = targetStream.height
        } else {
            stateFlow.value = stateFlow.value.copy(currentQuality = 0)
            lastAdaptiveQualityHeight = currentVideoStream?.height ?: 0
        }
    }
    
    /**
     * Check if we can upgrade quality based on current bandwidth.
     * Called periodically when playback is smooth.
     */
    fun checkAdaptiveQualityUpgrade(currentPosition: Long) {
        if (!isAdaptiveQualityEnabled || availableVideoStreams.isEmpty()) return
        
        val currentHeight = currentVideoStream?.height ?: return
        val estimatedBandwidth = bandwidthMeter?.bitrateEstimate ?: return
        
        val targetHeight = PlayerConfig.calculateTargetQualityForBandwidth(estimatedBandwidth)
        
        // Only upgrade if target is significantly higher than current
        if (targetHeight > currentHeight) {
            val nextHigherStream = availableVideoStreams
                .filter { it.height > currentHeight && it.height <= targetHeight }
                .minByOrNull { it.height }
            
            if (nextHigherStream != null) {
                val streamBitrate = nextHigherStream.bitrate.toLong()
                val requiredBandwidth = (streamBitrate * PlayerConfig.QUALITY_UPGRADE_THRESHOLD).toLong()
                
                if (estimatedBandwidth > requiredBandwidth || streamBitrate == 0L) {
                    Log.d(TAG, "Adaptive UPGRADE: ${currentHeight}p -> ${nextHigherStream.height}p (bandwidth: ${estimatedBandwidth/1_000_000}Mbps)")
                    performAdaptiveQualitySwitch(nextHigherStream, currentPosition)
                }
            }
        }
    }
    
    /**
     * Check if we need to downgrade quality due to buffering or low bandwidth.
     */
    fun checkAdaptiveQualityDowngrade(forceCheck: Boolean, currentPosition: Long) {
        if (!isAdaptiveQualityEnabled || availableVideoStreams.isEmpty()) return
        
        val currentHeight = currentVideoStream?.height ?: return
        val estimatedBandwidth = bandwidthMeter?.bitrateEstimate ?: 1_000_000L
        
        val nextLowerStream = availableVideoStreams
            .filter { it.height < currentHeight }
            .maxByOrNull { it.height }
        
        if (nextLowerStream != null) {
            if (forceCheck) {
                Log.d(TAG, "Adaptive DOWNGRADE (buffering): ${currentHeight}p -> ${nextLowerStream.height}p")
                performAdaptiveQualitySwitch(nextLowerStream, currentPosition)
            } else {
                val currentStreamBitrate = currentVideoStream?.bitrate?.toLong() ?: 0L
                if (currentStreamBitrate > 0 && estimatedBandwidth < (currentStreamBitrate * PlayerConfig.QUALITY_DOWNGRADE_THRESHOLD).toLong()) {
                    Log.d(TAG, "Adaptive DOWNGRADE (low bandwidth): ${currentHeight}p -> ${nextLowerStream.height}p (bandwidth: ${estimatedBandwidth/1_000_000}Mbps)")
                    performAdaptiveQualitySwitch(nextLowerStream, currentPosition)
                }
            }
        } else {
            Log.d(TAG, "Adaptive: Already at lowest quality (${currentHeight}p), cannot downgrade further")
        }
    }
    
    /**
     * Perform the actual quality switch for adaptive mode.
     */
    private fun performAdaptiveQualitySwitch(targetStream: VideoStream, currentPosition: Long) {
        // Don't switch if we just switched recently (debounce)
        if (targetStream.height == lastAdaptiveQualityHeight) {
            Log.d(TAG, "Adaptive: Skipping switch, already at ${targetStream.height}p")
            return
        }
        
        currentVideoStream = targetStream
        lastAdaptiveQualityHeight = targetStream.height
        
        onQualitySwitch(targetStream, currentPosition)
        
        stateFlow.value = stateFlow.value.copy(
            currentQuality = 0,
            effectiveQuality = targetStream.height
        )
    }
    
    /**
     * Apply default track selector parameters for adaptive quality.
     */
    fun applyAdaptiveTrackSelectorDefaults() {
        trackSelector?.let { selector ->
            val params = selector.buildUponParameters()
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowMultipleAdaptiveSelections(true)
                .setMaxVideoSize(PlayerConfig.MAX_VIDEO_WIDTH, PlayerConfig.MAX_VIDEO_HEIGHT)
                .clearVideoSizeConstraints()
                .setForceHighestSupportedBitrate(false)
                .build()
            selector.setParameters(params)
        }
    }
    
    /**
     * Increment buffering count for adaptive quality tracking.
     */
    fun incrementBufferingCount() {
        if (isAdaptiveQualityEnabled) {
            consecutiveBufferingCount++
        }
    }
    
    /**
     * Reset buffering count (called when playback is smooth).
     */
    fun resetBufferingCount() {
        consecutiveBufferingCount = 0
    }
    
    /**
     * Check if buffering threshold is reached for quality downgrade.
     */
    fun hasReachedBufferingThreshold(): Boolean = 
        consecutiveBufferingCount >= PlayerConfig.BUFFERING_COUNT_THRESHOLD
    
    /**
     * Update bandwidth check time.
     */
    fun updateBandwidthCheckTime() {
        lastBandwidthCheckTime = System.currentTimeMillis()
    }
    
    /**
     * Check if enough time has passed since last bandwidth check.
     */
    fun shouldCheckBandwidth(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastBandwidthCheckTime >= PlayerConfig.BANDWIDTH_CHECK_INTERVAL_MS
    }
    
    /**
     * Attempt quality downgrade when stream is corrupted.
     * Returns the new stream or null if no alternatives available.
     */
    fun attemptQualityDowngrade(): VideoStream? {
        if (!isAdaptiveQualityEnabled) {
            Log.w(TAG, "Manual quality selected - skipping automatic downgrade")
            return currentVideoStream
        }

        val workingStreams = getWorkingStreams()
        
        if (workingStreams.isEmpty()) {
            Log.e(TAG, "No working streams available - all streams failed")
            return null
        }
        
        if (failedStreamUrls.size >= availableVideoStreams.size) {
            Log.e(TAG, "All available streams exhausted - stopping playback")
            return null
        }
        
        // Prefer MP4 over WebM for better compatibility
        val lowerQualityStream = workingStreams
            .sortedWith(compareBy(
                { it.format?.mimeType?.contains("webm", ignoreCase = true) == true },
                { it.height }
            ))
            .firstOrNull()
        
        if (lowerQualityStream != null) {
            Log.w(TAG, "Downgrading quality to ${lowerQualityStream.height}p (${lowerQualityStream.format?.mimeType}) - Failed: ${failedStreamUrls.size}/${availableVideoStreams.size}")
            currentVideoStream = lowerQualityStream
            streamErrorCount = 0
            
            stateFlow.value = stateFlow.value.copy(
                currentQuality = lowerQualityStream.height,
                isBuffering = true
            )
            
            return lowerQualityStream
        }
        
        return null
    }
    
    /**
     * Downgrade quality due to bandwidth issues.
     * Returns the new stream or null if already at lowest quality.
     */
    fun downgradeQualityDueToBandwidth(): VideoStream? {
        if (!isAdaptiveQualityEnabled) return null
        
        val currentHeight = currentVideoStream?.height ?: return null
        
        val lowerQualityStream = availableVideoStreams
            .filter { it.height < currentHeight }
            .maxByOrNull { it.height }
            
        if (lowerQualityStream != null) {
            Log.w(TAG, "Bandwidth adaptation: Downgrading from ${currentHeight}p to ${lowerQualityStream.height}p")
            currentVideoStream = lowerQualityStream
            
            stateFlow.value = stateFlow.value.copy(
                effectiveQuality = lowerQualityStream.height
            )
            
            return lowerQualityStream
        }
        
        Log.w(TAG, "Bandwidth adaptation: No lower quality available")
        return null
    }
}
