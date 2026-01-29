package com.flow.youtube.player.audio

import android.content.Context
import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.player.state.EnhancedPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Manages audio-related features like skip silence and playback speed.
 */
@UnstableApi
class AudioFeaturesManager(
    private val scope: CoroutineScope,
    private val stateFlow: MutableStateFlow<EnhancedPlayerState>
) {
    companion object {
        private const val TAG = "AudioFeaturesManager"
    }
    
    // Audio processor for skipping silence - needs to be created during player initialization
    val silenceSkippingProcessor = SilenceSkippingAudioProcessor()
    
    /**
     * Set skip silence state internally (without persisting).
     */
    fun setSkipSilenceInternal(isEnabled: Boolean) {
        silenceSkippingProcessor.setEnabled(isEnabled)
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
