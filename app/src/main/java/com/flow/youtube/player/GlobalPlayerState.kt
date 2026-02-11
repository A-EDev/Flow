package com.flow.youtube.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.flow.youtube.data.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mini player expansion states for in-app PiP functionality
 */
enum class MiniPlayerExpansionState {
    COLLAPSED,  // Small floating player in corner
    EXPANDED,   // Full screen player overlay
    HIDDEN      // Mini player not visible
}

/**
 * Global singleton to manage persistent video player state across the app.
 * Now delegates to EnhancedPlayerManager for actual player operations.
 * Maintains compatibility with existing code while providing enhanced features.
 */
@UnstableApi
object GlobalPlayerState {
    
    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo: StateFlow<Video?> = _currentVideo.asStateFlow()
    
    private val _isMiniPlayerVisible = MutableStateFlow(false)
    val isMiniPlayerVisible: StateFlow<Boolean> = _isMiniPlayerVisible.asStateFlow()
    
    private val _miniPlayerExpansionState = MutableStateFlow(MiniPlayerExpansionState.HIDDEN)
    val miniPlayerExpansionState: StateFlow<MiniPlayerExpansionState> = _miniPlayerExpansionState.asStateFlow()
    
    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()
    
    // Delegate to EnhancedPlayerManager for player state
    val playerState: StateFlow<EnhancedPlayerState> = EnhancedPlayerManager.getInstance().playerState
    
    // Computed properties from EnhancedPlayerManager - delegates to player state
    val isPlaying: StateFlow<Boolean> get() {
        val flow = MutableStateFlow(false)
        flow.value = EnhancedPlayerManager.getInstance().isPlaying()
        return flow.asStateFlow()
    }
    
    val currentPosition: StateFlow<Long> get() {
        val flow = MutableStateFlow(0L)
        flow.value = EnhancedPlayerManager.getInstance().getCurrentPosition()
        return flow.asStateFlow()
    }
    
    val duration: StateFlow<Long> get() {
        val flow = MutableStateFlow(0L)
        flow.value = EnhancedPlayerManager.getInstance().getDuration()
        return flow.asStateFlow()
    }
    
    // Legacy compatibility - delegates to EnhancedPlayerManager
    @Deprecated("Use EnhancedPlayerManager.getPlayer() instead", ReplaceWith("EnhancedPlayerManager.getInstance().getPlayer()"))
    val exoPlayer get() = EnhancedPlayerManager.getInstance().getPlayer()
    
    /**
     * Initialize the player - delegates to EnhancedPlayerManager.
     */
    fun initialize(context: Context) {
        EnhancedPlayerManager.getInstance().initialize(context)
    }
    
    /**
     * Set PiP mode state.
     */
    fun setPipMode(inPipMode: Boolean) {
        _isInPipMode.value = inPipMode
    }
    
    /**
     * Set the current video being played.
     */
    fun setCurrentVideo(video: Video?) {
        _currentVideo.value = video
    }
    
    /**
     * Update playback position and duration (legacy compatibility).
     */
    @Deprecated("Position tracking now handled automatically by EnhancedPlayerManager")
    fun updatePlaybackInfo(_position: Long, _duration: Long) {
        // No-op - EnhancedPlayerManager handles this internally
    }
    
    /**
     * Show the mini player (collapsed state).
     */
    fun showMiniPlayer() {
        if (_currentVideo.value != null) {
            _isMiniPlayerVisible.value = true
            _miniPlayerExpansionState.value = MiniPlayerExpansionState.COLLAPSED
        }
    }
    
    /**
     * Hide the mini player.
     */
    fun hideMiniPlayer() {
        _isMiniPlayerVisible.value = false
        _miniPlayerExpansionState.value = MiniPlayerExpansionState.HIDDEN
    }
    
    /**
     * Set the mini player expansion state.
     */
    fun setMiniPlayerExpansionState(state: MiniPlayerExpansionState) {
        _miniPlayerExpansionState.value = state
        _isMiniPlayerVisible.value = state != MiniPlayerExpansionState.HIDDEN
    }
    
    /**
     * Collapse the mini player to corner position.
     */
    fun collapseMiniPlayer() {
        if (_currentVideo.value != null) {
            _miniPlayerExpansionState.value = MiniPlayerExpansionState.COLLAPSED
            _isMiniPlayerVisible.value = true
        }
    }
    
    /**
     * Expand the mini player to full screen overlay.
     */
    fun expandMiniPlayer() {
        if (_currentVideo.value != null) {
            _miniPlayerExpansionState.value = MiniPlayerExpansionState.EXPANDED
            _isMiniPlayerVisible.value = true
        }
    }
    
    /**
     * Toggle play/pause state - delegates to EnhancedPlayerManager.
     */
    fun togglePlayPause() {
        if (EnhancedPlayerManager.getInstance().isPlaying()) {
            EnhancedPlayerManager.getInstance().pause()
        } else {
            EnhancedPlayerManager.getInstance().play()
        }
    }
    
    /**
     * Pause playback - delegates to EnhancedPlayerManager.
     */
    fun pause() {
        EnhancedPlayerManager.getInstance().pause()
    }
    
    /**
     * Resume playback - delegates to EnhancedPlayerManager.
     */
    fun play() {
        EnhancedPlayerManager.getInstance().play()
    }
    
    /**
     * Stop playback and clear current video.
     */
    fun stop() {
        EnhancedPlayerManager.getInstance().stop()
        _currentVideo.value = null
        _isMiniPlayerVisible.value = false
        _miniPlayerExpansionState.value = MiniPlayerExpansionState.HIDDEN
    }
    
    /**
     * Release the player - delegates to EnhancedPlayerManager.
     */
    fun release() {
        EnhancedPlayerManager.getInstance().release()
        _currentVideo.value = null
        _isMiniPlayerVisible.value = false
        _miniPlayerExpansionState.value = MiniPlayerExpansionState.HIDDEN
    }
    
    /**
     * Get progress as a float between 0 and 1.
     */
    fun getProgress(): Float {
        val position = EnhancedPlayerManager.getInstance().getCurrentPosition()
        val dur = EnhancedPlayerManager.getInstance().getDuration()
        return if (dur > 0) {
            (position.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
}
