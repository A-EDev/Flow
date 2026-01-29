package com.flow.youtube.ui.screens.player.state

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.*
import com.flow.youtube.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import com.flow.youtube.ui.components.SubtitleCue
import com.flow.youtube.ui.components.SubtitleStyle

class PlayerScreenState {
    // UI Visibility States
    var showControls by mutableStateOf(true)
    var isFullscreen by mutableStateOf(false)
    var isInPipMode by mutableStateOf(false)
    
    // Playback Position
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    
    // Dialog States
    var showQualitySelector by mutableStateOf(false)
    var showAudioTrackSelector by mutableStateOf(false)
    var showSubtitleSelector by mutableStateOf(false)
    var showSettingsMenu by mutableStateOf(false)
    var showDownloadDialog by mutableStateOf(false)
    var showPlaybackSpeedSelector by mutableStateOf(false)
    var showSubtitleStyleCustomizer by mutableStateOf(false)
    
    // Bottom Sheet States
    var showQuickActions by mutableStateOf(false)
    var showCommentsSheet by mutableStateOf(false)
    var showDescriptionSheet by mutableStateOf(false)
    var showChaptersSheet by mutableStateOf(false)
    
    // Comment Sorting
    var isTopComments by mutableStateOf(true)
    
    // Gesture States
    var brightnessLevel by mutableFloatStateOf(0.5f)
    var volumeLevel by mutableFloatStateOf(0.5f)
    var showBrightnessOverlay by mutableStateOf(false)
    var showVolumeOverlay by mutableStateOf(false)
    
    // Seek Animation States
    var showSeekForwardAnimation by mutableStateOf(false)
    var showSeekBackAnimation by mutableStateOf(false)
    
    // Subtitle States
    var subtitlesEnabled by mutableStateOf(false)
    var currentSubtitles by mutableStateOf<List<SubtitleCue>>(emptyList())
    var selectedSubtitleUrl by mutableStateOf<String?>(null)
    var subtitleStyle by mutableStateOf(SubtitleStyle())
    
    // Video Display
    var resizeMode by mutableIntStateOf(0) // 0=Fit, 1=Fill, 2=Zoom
    
    // Speed Control
    var isSpeedBoostActive by mutableStateOf(false)
    var normalSpeed by mutableFloatStateOf(1.0f)
    
    // Shorts/Music Prompt
    var showShortsPrompt by mutableStateOf(false)
    var hasShownShortsPrompt by mutableStateOf(false)
    
    // Seekbar Preview
    var seekbarPreviewHelper by mutableStateOf<SeekbarPreviewThumbnailHelper?>(null)
   
    fun resetForNewVideo() {
        showControls = true
        currentPosition = 0L
        duration = 0L
        subtitlesEnabled = false
        currentSubtitles = emptyList()
        selectedSubtitleUrl = null
        seekbarPreviewHelper = null
        showBrightnessOverlay = false
        showVolumeOverlay = false
        showSeekBackAnimation = false
        showSeekForwardAnimation = false
        hasShownShortsPrompt = false
        showShortsPrompt = false
    }
    
    fun cycleResizeMode() {
        resizeMode = (resizeMode + 1) % 3
    }
    
    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
    }
    
    fun enableSubtitles(url: String, subtitles: List<SubtitleCue>) {
        selectedSubtitleUrl = url
        currentSubtitles = subtitles
        subtitlesEnabled = subtitles.isNotEmpty()
    }
    
    fun disableSubtitles() {
        subtitlesEnabled = false
        selectedSubtitleUrl = null
        currentSubtitles = emptyList()
    }
}

@Composable
fun rememberPlayerScreenState(): PlayerScreenState {
    return remember { PlayerScreenState() }
}

data class AudioSystemInfo(
    val audioManager: AudioManager,
    val maxVolume: Int
)

@Composable
fun rememberAudioSystemInfo(context: Context): AudioSystemInfo {
    return remember {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        AudioSystemInfo(
            audioManager = audioManager,
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        )
    }
}
