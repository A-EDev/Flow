package io.github.aedev.flow.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.data.model.Video
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

    private val _dismissRequested = MutableStateFlow(false)
    val dismissRequested: StateFlow<Boolean> = _dismissRequested.asStateFlow()
    
    // Delegate to EnhancedPlayerManager for player state. This is the single reactive
    // source of truth for playback; collect playerState for isPlaying/position/duration.
    val playerState: StateFlow<EnhancedPlayerState> = EnhancedPlayerManager.getInstance().playerState

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

    fun requestDismiss() {
        _dismissRequested.value = true
    }

    fun resetDismiss() {
        _dismissRequested.value = false
    }
    
    /**
     * Set the current video being played.
     */
    fun setCurrentVideo(video: Video?) {
        _currentVideo.value = video
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
}
