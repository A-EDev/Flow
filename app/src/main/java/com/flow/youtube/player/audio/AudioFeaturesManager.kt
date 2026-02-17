package com.flow.youtube.player.audio

import android.content.Context
import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.player.state.EnhancedPlayerState
import com.flow.youtube.service.Media3MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Manages audio-related features like skip silence and playback speed.
 * 
 * Uses ExoPlayer's built-in [ExoPlayer.setSkipSilenceEnabled] instead of a manual
 * [SilenceSkippingAudioProcessor] to avoid audio-video desync. The built-in API
 * correctly adjusts the media clock when silence is removed, keeping audio and
 * video in perfect sync.
 * 
 * **External Audio Processor Compatibility:**
 * This manager works with external audio processors like James DSP.
 * The audio session ID is exposed via [Media3MusicService.currentAudioSessionId]
 * which external apps can use to apply audio effects to Flow's output.
 */
@UnstableApi
class AudioFeaturesManager(
    private val scope: CoroutineScope,
    private val stateFlow: MutableStateFlow<EnhancedPlayerState>
) {
    companion object {
        private const val TAG = "AudioFeaturesManager"
        
        /**
         * Get the current audio session ID for external audio processors.
         * Returns 0 if no active session exists.
         * 
         * External apps like James DSP can use this to target Flow's audio output.
         */
        fun getAudioSessionId(): Int = Media3MusicService.currentAudioSessionId
    }
    
    private var playerRef: ExoPlayer? = null
    
    private var pendingSkipSilence: Boolean? = null
    
    /**
     * Set the player reference. Must be called after ExoPlayer is created.
     * Applies any pending skip-silence state that was set before the player was ready.
     */
    fun setPlayer(player: ExoPlayer) {
        this.playerRef = player
        pendingSkipSilence?.let { pending ->
            player.skipSilenceEnabled = pending
            pendingSkipSilence = null
            Log.d(TAG, "Applied pending skip silence: $pending")
        }
    }
    
    /**
     * Clear the player reference (call on release).
     */
    fun clearPlayer() {
        playerRef = null
    }
    
    /**
     * Set skip silence state internally (without persisting).
     * Uses ExoPlayer's built-in skipSilenceEnabled for correct A/V sync.
     */
    fun setSkipSilenceInternal(isEnabled: Boolean) {
        val player = playerRef
        if (player != null) {
            player.skipSilenceEnabled = isEnabled
        } else {
            pendingSkipSilence = isEnabled
            Log.d(TAG, "Player not ready, queuing skip silence: $isEnabled")
        }
        stateFlow.value = stateFlow.value.copy(isSkipSilenceEnabled = isEnabled)
    }
    
    /**
     * Toggle skip silence and persist the preference.
     */
    fun toggleSkipSilence(isEnabled: Boolean, context: Context?) {
        setSkipSilenceInternal(isEnabled)
        context?.let { ctx ->
            scope.launch {
                PlayerPreferences(ctx).setSkipSilenceEnabled(isEnabled)
            }
        }
    }
    
    /**
     * Set playback speed.
     */
    fun setPlaybackSpeed(player: ExoPlayer?, speed: Float) {
        player?.let { exoPlayer ->
            val params = PlaybackParameters(speed)
            exoPlayer.setPlaybackParameters(params)
            stateFlow.value = stateFlow.value.copy(playbackSpeed = speed)
            Log.d(TAG, "Playback speed set to: ${speed}x")
        }
    }
    
    /**
     * Observe skip silence preference changes.
     */
    fun observeSkipSilencePreference(context: Context) {
        scope.launch {
            PlayerPreferences(context).skipSilenceEnabled.collect { isEnabled ->
                setSkipSilenceInternal(isEnabled)
            }
        }
    }
}
