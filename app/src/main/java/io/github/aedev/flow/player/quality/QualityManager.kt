package io.github.aedev.flow.player.quality

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.player.state.QualityOption
import io.github.aedev.flow.player.stream.VideoCodecUtils
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

        fun normalizeQualityHeight(rawHeight: Int): Int {
            return when {
                rawHeight <= 0 -> 0
                rawHeight in setOf(2160, 1440, 1080, 720, 480, 360, 240, 144) -> rawHeight
                rawHeight >= 3300 -> 2160
                rawHeight in 2400..3299 -> 1440
                rawHeight in 1800..2399 -> 1080
                rawHeight in 1200..1799 -> 720
                rawHeight in 800..1199 -> 480
                rawHeight in 560..799 -> 360
                rawHeight in 300..559 -> 240
                else -> 144
            }
        }
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
    
    private var lastQualitySwitchTime = 0L
    private val MIN_QUALITY_SWITCH_INTERVAL_MS = 10_000L
    
    var isDashSource = false
    
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
        availableVideoStreams = streams.distinctBy { it.getContent() }.sortedByDescending { qualityHeight(it) }
    }
    
    /**
     * Set the current video stream.
     */
    fun setCurrentStream(stream: VideoStream?) {
        currentVideoStream = stream
        lastAdaptiveQualityHeight = stream?.let(::qualityHeight) ?: 0
    }
    
    /**
     * Get the current video stream.
     */
    fun getCurrentStream(): VideoStream? = currentVideoStream
    
    /**
     * Reset quality state for a new video.
     * Defaults to adaptive mode; call setManualMode() after if user has non-AUTO preference.
     */
    fun resetForNewVideo() {
        failedStreamUrls.clear()
        streamErrorCount = 0
        currentVideoStream = null
        isAdaptiveQualityEnabled = true
        manualQualityHeight = null
        consecutiveBufferingCount = 0
        lastQualitySwitchTime = 0L
        applyAdaptiveTrackSelectorDefaults()
    }
    
    /**
     * Set manual (fixed) quality mode. Called when user has a non-AUTO quality preference.
     * Disables adaptive quality switching.
     */
    fun setManualMode(height: Int) {
        isAdaptiveQualityEnabled = false
        manualQualityHeight = normalizeQualityHeight(height)
        Log.d(TAG, "Manual quality mode set: ${manualQualityHeight}p (adaptive disabled)")
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

    private fun qualityHeight(stream: VideoStream): Int {
        return normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(stream))
    }
    
    /**
     * Select smart initial quality based on bandwidth.
     */
    fun selectSmartInitialQuality(): VideoStream? {
        val estimatedBandwidth = bandwidthMeter?.bitrateEstimate ?: 2_000_000L
        val targetHeight = PlayerConfig.calculateInitialQualityTarget(estimatedBandwidth)
        
        val smartStream = availableVideoStreams
            .sortedWith(
                compareBy<VideoStream> { kotlin.math.abs(qualityHeight(it) - targetHeight) }
                    .thenBy { VideoCodecUtils.playbackCodecRank(it) }
                    .thenByDescending { it.bitrate }
            )
            .firstOrNull()
        
        Log.d(TAG, "Smart quality selection: bandwidth=${estimatedBandwidth/1_000_000}Mbps, target=${targetHeight}p, selected=${smartStream?.let(::qualityHeight)}p")
        
        return smartStream
    }
    
    /**
     * Build quality options list for UI.
     */
    fun buildQualityOptions(): List<QualityOption> {
        return listOf(
            QualityOption(height = 0, label = "Auto", bitrate = 0L)
        ) + availableVideoStreams
            .groupBy { "${qualityHeight(it)}_${VideoCodecUtils.codecKeyFromStream(it)}" }
            .values
            .mapNotNull { streams ->
                val bestStream = streams.maxByOrNull { it.bitrate } ?: return@mapNotNull null
                val height = qualityHeight(bestStream)
                val codecKey = VideoCodecUtils.codecKeyFromStream(bestStream)
                QualityOption(
                    height = height,
                    label = "${height}p ${VideoCodecUtils.codecLabelFromKey(codecKey)}",
                    bitrate = bestStream.bitrate.toLong(),
                    codecKey = codecKey,
                    streamKey = streamKey(bestStream)
                )
            }
            .sortedWith(
                compareByDescending<QualityOption> { it.height }
                    .thenBy { VideoCodecUtils.playbackCodecRank(it.codecKey) }
                    .thenByDescending { it.bitrate }
            )
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
        val targetStream = availableVideoStreams
            .filter { qualityHeight(it) == height }
            .minWithOrNull(
                compareBy<VideoStream> { VideoCodecUtils.playbackCodecRank(it) }
                    .thenByDescending { it.bitrate }
            )
        if (targetStream == null) {
            Log.w(TAG, "No stream found for ${height}p, available: ${availableVideoStreams.map { qualityHeight(it) }}")
            return false
        }
        
        val targetHeight = qualityHeight(targetStream)
        currentVideoStream = targetStream
        onQualitySwitch(targetStream, currentPosition)
        
        stateFlow.value = stateFlow.value.copy(
            currentQuality = targetHeight,
            effectiveQuality = targetHeight,
            currentQualityKey = streamKey(targetStream)
        )
        return true
    }

    fun switchQuality(option: QualityOption, currentPosition: Long): Boolean {
        option.streamKey?.let { return switchQualityByStreamKey(it, currentPosition) }
        return switchQualityByHeight(option.height, currentPosition)
    }

    fun switchQualityByStreamKey(streamKey: String, currentPosition: Long): Boolean {
        val targetStream = availableVideoStreams.firstOrNull { streamKey(it) == streamKey }
        if (targetStream == null) {
            Log.w(TAG, "No stream found for key=$streamKey")
            return false
        }

        val targetHeight = qualityHeight(targetStream)
        val targetCodec = VideoCodecUtils.codecKeyFromStream(targetStream)
        isAdaptiveQualityEnabled = false
        manualQualityHeight = targetHeight
        currentVideoStream = targetStream
        Log.d(TAG, "Switching to FIXED quality: ${targetHeight}p ${VideoCodecUtils.codecLabelFromKey(targetCodec)}")

        if (isDashSource) {
            switchDashQualitySeamlessly(targetStream.height, targetHeight)
        } else {
            onQualitySwitch(targetStream, currentPosition)
        }

        stateFlow.value = stateFlow.value.copy(
            currentQuality = targetHeight,
            effectiveQuality = targetHeight,
            currentQualityKey = streamKey
        )
        return true
    }
    
    /**
     * Enable adaptive quality mode.
     */
    private fun enableAdaptiveQuality(currentPosition: Long) {
        isAdaptiveQualityEnabled = true
        manualQualityHeight = null
        
        Log.d(TAG, "Enabling adaptive quality mode")
        if (isDashSource) {
            applyAdaptiveTrackSelectorDefaults()
        }
        
        val estimatedBandwidth = bandwidthMeter?.bitrateEstimate ?: 2_000_000L
        val targetHeight = PlayerConfig.calculateInitialQualityTarget(estimatedBandwidth)
        
        Log.d(TAG, "Auto quality: Estimated bandwidth ${estimatedBandwidth/1_000_000}Mbps -> targeting ${targetHeight}p")
        
        val targetStream = availableVideoStreams
            .sortedWith(
                compareBy<VideoStream> { kotlin.math.abs(qualityHeight(it) - targetHeight) }
                    .thenBy { VideoCodecUtils.playbackCodecRank(it) }
                    .thenByDescending { it.bitrate }
            )
            .firstOrNull()
        
        if (targetStream != null && qualityHeight(targetStream) != currentVideoStream?.let(::qualityHeight)) {
            val selectedHeight = qualityHeight(targetStream)
            currentVideoStream = targetStream
            onQualitySwitch(targetStream, currentPosition)
            
            stateFlow.value = stateFlow.value.copy(
                currentQuality = 0,
                effectiveQuality = selectedHeight,
                currentQualityKey = null
            )
            lastAdaptiveQualityHeight = selectedHeight
        } else {
            stateFlow.value = stateFlow.value.copy(currentQuality = 0, currentQualityKey = null)
            lastAdaptiveQualityHeight = currentVideoStream?.let(::qualityHeight) ?: 0
        }
    }
    
    /**
     * Check if we can upgrade quality based on current bandwidth.
     * Called periodically when playback is smooth.
     */
    fun checkAdaptiveQualityUpgrade(currentPosition: Long) {
        if (!isAdaptiveQualityEnabled || availableVideoStreams.isEmpty()) return
        
        val currentHeight = currentVideoStream?.let(::qualityHeight) ?: return
        val estimatedBandwidth = bandwidthMeter?.bitrateEstimate ?: return
        
        val targetHeight = PlayerConfig.calculateTargetQualityForBandwidth(estimatedBandwidth)
        
        // Only upgrade if target is significantly higher than current
        if (targetHeight > currentHeight) {
            val nextHigherStream = availableVideoStreams
                .filter { qualityHeight(it) > currentHeight && qualityHeight(it) <= targetHeight }
                .minByOrNull { qualityHeight(it) }
            
            if (nextHigherStream != null) {
                val nextHeight = qualityHeight(nextHigherStream)
                val streamBitrate = nextHigherStream.bitrate.toLong()
                val requiredBandwidth = (streamBitrate * PlayerConfig.QUALITY_UPGRADE_THRESHOLD).toLong()
                
                if (estimatedBandwidth > requiredBandwidth || streamBitrate == 0L) {
                    Log.d(TAG, "Adaptive UPGRADE: ${currentHeight}p -> ${nextHeight}p (bandwidth: ${estimatedBandwidth/1_000_000}Mbps)")
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
        
        val currentHeight = currentVideoStream?.let(::qualityHeight) ?: return
        val estimatedBandwidth = bandwidthMeter?.bitrateEstimate ?: 1_000_000L
        
        val nextLowerStream = availableVideoStreams
            .filter { qualityHeight(it) < currentHeight }
            .maxByOrNull { qualityHeight(it) }
        
        if (nextLowerStream != null) {
            val nextHeight = qualityHeight(nextLowerStream)
            if (forceCheck) {
                Log.d(TAG, "Adaptive DOWNGRADE (buffering): ${currentHeight}p -> ${nextHeight}p")
                performAdaptiveQualitySwitch(nextLowerStream, currentPosition)
            } else {
                val currentStreamBitrate = currentVideoStream?.bitrate?.toLong() ?: 0L
                if (currentStreamBitrate > 0 && estimatedBandwidth < (currentStreamBitrate * PlayerConfig.QUALITY_DOWNGRADE_THRESHOLD).toLong()) {
                    Log.d(TAG, "Adaptive DOWNGRADE (low bandwidth): ${currentHeight}p -> ${nextHeight}p (bandwidth: ${estimatedBandwidth/1_000_000}Mbps)")
                    performAdaptiveQualitySwitch(nextLowerStream, currentPosition)
                }
            }
        } else {
            Log.d(TAG, "Adaptive: Already at lowest quality (${currentHeight}p), cannot downgrade further")
        }
    }
    
    /**
     * Perform the actual quality switch for adaptive mode.
     * Includes time-based debounce to prevent rapid switching.
     */
    private fun performAdaptiveQualitySwitch(targetStream: VideoStream, currentPosition: Long) {
        val targetHeight = qualityHeight(targetStream)
        // Don't switch if we just switched recently (same height debounce)
        if (targetHeight == lastAdaptiveQualityHeight) {
            Log.d(TAG, "Adaptive: Skipping switch, already at ${targetHeight}p")
            return
        }
        
        val now = System.currentTimeMillis()
        if (now - lastQualitySwitchTime < MIN_QUALITY_SWITCH_INTERVAL_MS) {
            Log.d(TAG, "Adaptive: Debouncing quality switch (${now - lastQualitySwitchTime}ms since last switch, min=${MIN_QUALITY_SWITCH_INTERVAL_MS}ms)")
            return
        }
        
        currentVideoStream = targetStream
        lastAdaptiveQualityHeight = targetHeight
        lastQualitySwitchTime = now
        
        if (isDashSource) {
            switchDashQualitySeamlessly(targetStream.height, targetHeight)
        } else {
            onQualitySwitch(targetStream, currentPosition)
        }
        
        stateFlow.value = stateFlow.value.copy(
            currentQuality = 0,
            effectiveQuality = targetHeight,
            currentQualityKey = null
        )
    }
    
    /**
     * Switch quality seamlessly via TrackSelector when using a DASH source.
     * This constrains the maximum video height, letting ExoPlayer
     * do a smooth in-buffer quality switch without interrupting playback.
     */
    private fun switchDashQualitySeamlessly(maxHeight: Int, qualityHeight: Int) {
        trackSelector?.let { selector ->
            val minHeight = (maxHeight - 1).coerceAtLeast(0)
            val params = selector.buildUponParameters()
                .setPreferredVideoMimeTypes(*PlayerConfig.PREFERRED_VIDEO_MIME_TYPES)
                .setMinVideoSize(0, minHeight)
                .setMaxVideoSize(Int.MAX_VALUE, maxHeight)
                .setForceHighestSupportedBitrate(true)
                .build()
            selector.setParameters(params)
            Log.d(TAG, "DASH seamless quality switch: constrained raw max height to ${maxHeight}px for ${qualityHeight}p")
        }
    }
    
    /**
     * Apply default track selector parameters for adaptive quality.
     */
    fun applyAdaptiveTrackSelectorDefaults() {
        trackSelector?.let { selector ->
            val params = selector.buildUponParameters()
                .setPreferredVideoMimeTypes(*PlayerConfig.PREFERRED_VIDEO_MIME_TYPES)
                .setAllowVideoMixedMimeTypeAdaptiveness(false)
                .setAllowMultipleAdaptiveSelections(true)
                .setMaxVideoSize(PlayerConfig.MAX_VIDEO_WIDTH, PlayerConfig.MAX_VIDEO_HEIGHT)
                .clearVideoSizeConstraints()
                .setForceHighestSupportedBitrate(false)
                .build()
            selector.setParameters(params)
        }
    }

    private fun streamKey(stream: VideoStream): String = stream.getContent().takeIf { it.isNotBlank() } ?: stream.hashCode().toString()
    
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
                { qualityHeight(it) }
            ))
            .firstOrNull()
        
        if (lowerQualityStream != null) {
            val lowerHeight = qualityHeight(lowerQualityStream)
            Log.w(TAG, "Downgrading quality to ${lowerHeight}p (${lowerQualityStream.format?.mimeType}) - Failed: ${failedStreamUrls.size}/${availableVideoStreams.size}")
            currentVideoStream = lowerQualityStream
            streamErrorCount = 0
            
            stateFlow.value = stateFlow.value.copy(
                currentQuality = lowerHeight,
                currentQualityKey = streamKey(lowerQualityStream),
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
        
        val currentHeight = currentVideoStream?.let(::qualityHeight) ?: return null
        
        val lowerQualityStream = availableVideoStreams
            .filter { qualityHeight(it) < currentHeight }
            .maxByOrNull { qualityHeight(it) }
            
        if (lowerQualityStream != null) {
            val lowerHeight = qualityHeight(lowerQualityStream)
            Log.w(TAG, "Bandwidth adaptation: Downgrading from ${currentHeight}p to ${lowerHeight}p")
            currentVideoStream = lowerQualityStream
            
            stateFlow.value = stateFlow.value.copy(
                effectiveQuality = lowerHeight
            )
            
            return lowerQualityStream
        }
        
        Log.w(TAG, "Bandwidth adaptation: No lower quality available")
        return null
    }
}
