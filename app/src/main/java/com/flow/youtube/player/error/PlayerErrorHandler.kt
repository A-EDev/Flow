package com.flow.youtube.player.error

import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.flow.youtube.player.config.PlayerConfig
import com.flow.youtube.player.state.EnhancedPlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Handles player errors and recovery logic.
 * Inspired by NewPipe's error handling approach.
 */
@UnstableApi
class PlayerErrorHandler(
    private val stateFlow: MutableStateFlow<EnhancedPlayerState>,
    private val onReloadStream: (Long, String) -> Unit,
    private val onQualityDowngrade: () -> Unit,
    private val onPlaybackShutdown: () -> Unit,
    private val getFailedStreamUrls: () -> Set<String>,
    private val markStreamFailed: (String) -> Unit,
    private val incrementStreamErrors: () -> Unit,
    private val getStreamErrorCount: () -> Int,
    private val isAdaptiveQualityEnabled: () -> Boolean,
    private val getManualQualityHeight: () -> Int?,
    private val getCurrentVideoStream: () -> VideoStream?,
    private val getCurrentAudioStream: () -> AudioStream?,
    private val getAvailableAudioStreams: () -> List<AudioStream>,
    private val setCurrentAudioStream: (AudioStream) -> Unit,
    private val setRecoveryState: () -> Unit,
    private val reloadPlaybackManager: () -> Unit
) {
    companion object {
        private const val TAG = "PlayerErrorHandler"
    }
    
    /**
     * Handle player errors from ExoPlayer.
     * Returns true if the error was handled, false otherwise.
     */
    fun handleError(error: PlaybackException, player: ExoPlayer?): Boolean {
        Log.e(TAG, "ExoPlayer - onPlayerError() called with:", error)

        saveStreamProgressState(player)
        var isCatchableException = false

        when (error.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                isCatchableException = true
                handleBehindLiveWindow(player)
            }
            
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> {
                handleParsingError(error)
            }
            
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_UNSPECIFIED -> {
                handleNetworkError(error)
            }
            
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> {
                handleDecoderError(error)
            }
            
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> {
                handleDrmError(error)
            }
            
            else -> {
                Log.w(TAG, "Unspecified error, attempting recovery: ${error.errorCode}")
                setRecoveryState()
                reloadPlaybackManager()
            }
        }

        if (!isCatchableException) {
            createErrorNotification(error)
        }

        return isCatchableException
    }
    
    private fun handleBehindLiveWindow(player: ExoPlayer?) {
        Log.w(TAG, "Behind live window, seeking to live edge")
        player?.seekToDefaultPosition()
        player?.prepare()
        stateFlow.value = stateFlow.value.copy(
            isBuffering = true,
            error = null
        )
    }
    
    private fun handleParsingError(error: PlaybackException) {
        Log.e(TAG, "Source validation error: ${error.errorCode} - ${error.message}")
        
        val errorMessage = error.message ?: ""
        val causeMessage = error.cause?.message ?: ""
        val fullErrorInfo = "$errorMessage $causeMessage"
        
        // Check for UnrecognizedInputFormatException
        if (fullErrorInfo.contains("UnrecognizedInputFormatException", ignoreCase = true) ||
            error.cause?.javaClass?.simpleName == "UnrecognizedInputFormatException") {
            
            Log.w(TAG, "Unrecognized format error - trying alternative stream format")
            
            val videoContent = getCurrentVideoStream()?.getContent()
            if (videoContent != null) {
                markStreamFailed(videoContent)
                Log.d(TAG, "Marking incompatible format stream: ${getCurrentVideoStream()?.format?.mimeType}")
                onQualityDowngrade()
                return
            }
        }
        
        // Check for NAL corruption errors
        val videoContent = getCurrentVideoStream()?.getContent()
        if ((fullErrorInfo.contains("NAL", ignoreCase = true) || 
             error.cause is androidx.media3.common.ParserException) && 
            videoContent != null) {
            
            markStreamFailed(videoContent)
            incrementStreamErrors()
            Log.w(TAG, "Corrupted stream detected (NAL/Parser error): $videoContent - Error count: ${getStreamErrorCount()}")
            
            if (getStreamErrorCount() >= PlayerConfig.MAX_STREAM_ERRORS) {
                Log.w(TAG, "Max stream errors reached")
                if (isAdaptiveQualityEnabled()) {
                    Log.w(TAG, "Adaptive mode enabled - attempting quality downgrade")
                    onQualityDowngrade()
                } else {
                    Log.w(TAG, "Manual quality locked (${getManualQualityHeight()}p) - retrying same stream")
                    onReloadStream(0L, "manual-quality-parser-error")
                }
                return
            }
        }
        
        setRecoveryState()
        reloadPlaybackManager()
    }
    
    private fun handleNetworkError(error: PlaybackException) {
        val errorMessage = error.message ?: ""
        val causeMessage = error.cause?.message ?: ""
        val fullErrorInfo = "$errorMessage $causeMessage"
        
        // Check if this is actually a parser error disguised as IO error
        if (fullErrorInfo.contains("NAL", ignoreCase = true) || 
            fullErrorInfo.contains("ParserException", ignoreCase = true) ||
            error.cause is androidx.media3.common.ParserException) {
            
            Log.e(TAG, "Parser error detected in IO error: $fullErrorInfo")
            
            val videoContent = getCurrentVideoStream()?.getContent()
            if (videoContent != null) {
                markStreamFailed(videoContent)
                incrementStreamErrors()
                Log.w(TAG, "Corrupted stream detected (NAL/Parser error): $videoContent - Error count: ${getStreamErrorCount()}")
                
                if (getStreamErrorCount() >= PlayerConfig.MAX_STREAM_ERRORS) {
                    Log.w(TAG, "Max stream errors reached")
                    if (isAdaptiveQualityEnabled()) {
                        Log.w(TAG, "Adaptive mode enabled - attempting quality downgrade")
                        onQualityDowngrade()
                    } else {
                        Log.w(TAG, "Manual quality locked - retrying same stream")
                        onReloadStream(0L, "manual-quality-parser-io")
                    }
                    return
                }
            }
            
            setRecoveryState()
            reloadPlaybackManager()
            return
        }
        
        // Network errors
        Log.w(TAG, "Network error encountered: ${error.errorCode} - ${error.message}")
        
        val videoContent = getCurrentVideoStream()?.getContent()
        if (videoContent != null) {
            incrementStreamErrors()
            Log.w(TAG, "Network error on stream: $videoContent - Error count: ${getStreamErrorCount()}")
            
            if (getStreamErrorCount() >= PlayerConfig.MAX_STREAM_ERRORS) {
                Log.w(TAG, "Max network errors reached for stream")
                if (isAdaptiveQualityEnabled()) {
                    markStreamFailed(videoContent)
                    Log.w(TAG, "Adaptive mode - marking stream failed and trying alternative quality")
                    onQualityDowngrade()
                } else {
                    Log.w(TAG, "Manual quality locked - retrying same stream after network error")
                    onReloadStream(0L, "manual-quality-network")
                }
                return
            }
        }
        
        setRecoveryState()
        reloadPlaybackManager()
    }
    
    private fun handleDecoderError(error: PlaybackException) {
        Log.e(TAG, "Decoder/renderer error: ${error.errorCode}")
        
        val isAudioError = error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                         error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                         error.message?.contains("AudioRenderer", ignoreCase = true) == true
        
        if (isAudioError && getCurrentAudioStream()?.getContent() != null) {
            markStreamFailed(getCurrentAudioStream()!!.getContent())
            Log.w(TAG, "Audio decoder error - trying alternative audio stream")
            
            val failedUrls = getFailedStreamUrls()
            val alternativeAudio = getAvailableAudioStreams()
                .filter { !failedUrls.contains(it.getContent()) }
                .sortedByDescending { it.averageBitrate }
                .firstOrNull()
            
            if (alternativeAudio != null) {
                setCurrentAudioStream(alternativeAudio)
                Log.d(TAG, "Switching to alternative audio format: ${alternativeAudio.format?.mimeType}")
                setRecoveryState()
                reloadPlaybackManager()
                return
            }
        }
        
        Log.e(TAG, "Decoder error - no alternatives available, stopping playback")
        onPlaybackShutdown()
        stateFlow.value = stateFlow.value.copy(
            error = "Playback device error: ${error.message}",
            isPlaying = false
        )
    }
    
    private fun handleDrmError(error: PlaybackException) {
        Log.e(TAG, "DRM error: ${error.errorCode}")
        onPlaybackShutdown()
        stateFlow.value = stateFlow.value.copy(
            error = "Content protection error: ${error.message}",
            isPlaying = false
        )
    }
    
    /**
     * Save current stream progress state for recovery.
     */
    private fun saveStreamProgressState(player: ExoPlayer?) {
        player?.let { exoPlayer ->
            val currentPosition = exoPlayer.currentPosition
            Log.d(TAG, "Saved progress state: position=$currentPosition")
        }
    }
    
    /**
     * Create error notification for user awareness.
     */
    private fun createErrorNotification(error: PlaybackException) {
        Log.e(TAG, "Player error notification: ${error.message}")
    }
    
    /**
     * Set recovery state.
     */
    fun setRecovery() {
        Log.d(TAG, "Setting recovery state")
        stateFlow.value = stateFlow.value.copy(
            isBuffering = true,
            error = null,
            recoveryAttempted = true
        )
    }
    
    /**
     * Handle playback shutdown on critical errors.
     */
    fun handlePlaybackShutdown(player: ExoPlayer?) {
        Log.w(TAG, "Playback shutdown initiated")
        try {
            player?.stop()
            player?.clearMediaItems()
            stateFlow.value = stateFlow.value.copy(
                isPlaying = false,
                isBuffering = false,
                error = "Playback stopped due to critical error"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during playback shutdown", e)
        }
    }
}
