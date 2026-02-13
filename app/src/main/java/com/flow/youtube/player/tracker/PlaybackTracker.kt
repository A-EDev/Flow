package com.flow.youtube.player.tracker

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.flow.youtube.player.config.PlayerConfig
import com.flow.youtube.player.state.EnhancedPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
    
@UnstableApi
class PlaybackTracker(
    private val scope: CoroutineScope,
    private val stateFlow: MutableStateFlow<EnhancedPlayerState>,
    private val onSponsorBlockCheck: (Long) -> Long?, // Returns seek position if skip needed
    private val onBufferingDetected: () -> Unit,
    private val onSmoothPlayback: () -> Unit,
    private val onBandwidthCheckNeeded: () -> Unit
) {
    companion object {
        private const val TAG = "PlaybackTracker"
    }
    
    private var positionTrackerJob: Job? = null
    private var lastCheckedPosition = 0L
    private var stuckCount = 0
    private var lastSaveTime = 0L
    
    // Watchdog state
    private var isWatchdogActive = false
    
    /**
     * Start position tracking.
     */
    fun start(player: ExoPlayer) {
        Log.d(TAG, "start() called")
        stop()
        positionTrackerJob = scope.launch {
            Log.d(TAG, "Position tracker coroutine started")
            lastCheckedPosition = 0L
            stuckCount = 0
            lastSaveTime = 0L
            
            while (true) {
                trackPosition(player)
                delay(PlayerConfig.POSITION_TRACKER_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop position tracking.
     */
    fun stop() {
        positionTrackerJob?.cancel()
        positionTrackerJob = null
        stuckCount = 0
        isWatchdogActive = false
    }
    
    private suspend fun trackPosition(player: ExoPlayer) {
        if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
            val bufferedPos = player.bufferedPosition
            val currentPos = player.currentPosition
            
            // Debug log every 5 seconds (approx 10 ticks)
            if (System.currentTimeMillis() % 5000 < PlayerConfig.POSITION_TRACKER_INTERVAL_MS * 2) {
                 Log.d(TAG, "Tracking position: $currentPos ms")
            }
            
            val duration = player.duration.coerceAtLeast(1)
            val bufferedPct = (bufferedPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            
            stateFlow.value = stateFlow.value.copy(
                bufferedPercentage = bufferedPct
            )

            // Periodic auto-save signal (every 30 seconds)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSaveTime >= PlayerConfig.AUTO_SAVE_INTERVAL_MS && player.isPlaying) {
                Log.d(TAG, "Auto-save trigger: at ${currentPos}ms")
                lastSaveTime = currentTime
            }

            // SponsorBlock Skip Logic
            val skipPosition = onSponsorBlockCheck(currentPos)
            if (skipPosition != null) {
                Log.d(TAG, "Skipping to $skipPosition ms")
                player.seekTo(skipPosition)
            }
            
            // Smart stall detection
            if (player.playbackState == Player.STATE_BUFFERING) {
                if (currentPos == lastCheckedPosition && player.playWhenReady) {
                    stuckCount++
                    // Only log if actually stuck for more than 1 second
                    if (stuckCount >= PlayerConfig.STUCK_DETECTION_THRESHOLD) {
                        val bufferAhead = bufferedPos - currentPos
                        Log.d(TAG, "STALL: Pos=${currentPos}ms | Buff=${bufferedPos}ms (+${bufferAhead}ms ahead) | StuckFor=${stuckCount * PlayerConfig.POSITION_TRACKER_INTERVAL_MS}ms")
                        onBufferingDetected()
                    }
                } else {
                    stuckCount = 0
                }
            } else {
                stuckCount = 0
                onSmoothPlayback()
                
                // Periodic bandwidth check for quality upgrade
                if (player.isPlaying) {
                    onBandwidthCheckNeeded()
                }
            }
            
            lastCheckedPosition = currentPos
        }
    }
    
    /**
     * Get the last checked position.
     */
    fun getLastPosition(): Long = lastCheckedPosition
    
    /**
     * Reset tracking state for a new video.
     */
    fun reset() {
        lastCheckedPosition = 0L
        stuckCount = 0
        lastSaveTime = 0L
    }
    
    /**
     * Check if playback is currently stalled.
     */
    fun isStalled(): Boolean = stuckCount >= PlayerConfig.STUCK_DETECTION_THRESHOLD
    
    /**
     * Get current stuck count for debugging.
     */
    fun getStuckCount(): Int = stuckCount
}
