package com.flow.youtube.ui.screens.player.dialogs

import androidx.compose.runtime.Composable
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.state.EnhancedPlayerState
import com.flow.youtube.ui.screens.player.VideoPlayerUiState
import com.flow.youtube.ui.screens.player.VideoPlayerViewModel
import com.flow.youtube.ui.screens.player.components.*
import com.flow.youtube.ui.screens.player.state.PlayerScreenState

@Composable
fun PlayerDialogsContainer(
    screenState: PlayerScreenState,
    playerState: EnhancedPlayerState,
    uiState: VideoPlayerUiState,
    video: Video,
    viewModel: VideoPlayerViewModel
) {
    // Download Quality Dialog
    if (screenState.showDownloadDialog) {
        DownloadQualityDialog(
            uiState = uiState,
            video = video,
            onDismiss = { screenState.showDownloadDialog = false }
        )
    }

    // Quality selector
    if (screenState.showQualitySelector) {
        QualitySelectorDialog(
            availableQualities = playerState.availableQualities,
            currentQuality = playerState.currentQuality,
            onDismiss = { screenState.showQualitySelector = false },
            onQualitySelected = { height ->
                EnhancedPlayerManager.getInstance().switchQuality(height)
            }
        )
    }
    
    // Audio track selector
    if (screenState.showAudioTrackSelector) {
        AudioTrackSelectorDialog(
            availableAudioTracks = playerState.availableAudioTracks,
            currentAudioTrack = playerState.currentAudioTrack,
            onDismiss = { screenState.showAudioTrackSelector = false },
            onTrackSelected = { index ->
                EnhancedPlayerManager.getInstance().switchAudioTrack(index)
            }
        )
    }
    
    // Subtitle selector
    if (screenState.showSubtitleSelector) {
        SubtitleSelectorDialog(
            availableSubtitles = playerState.availableSubtitles,
            selectedSubtitleUrl = screenState.selectedSubtitleUrl,
            subtitlesEnabled = screenState.subtitlesEnabled,
            onDismiss = { screenState.showSubtitleSelector = false },
            onSubtitleSelected = { index, url ->
                screenState.selectedSubtitleUrl = url
                EnhancedPlayerManager.getInstance().selectSubtitle(index)
                screenState.subtitlesEnabled = true
            },
            onDisableSubtitles = {
                screenState.disableSubtitles()
            }
        )
    }
    
    // Settings menu
    if (screenState.showSettingsMenu) {
        SettingsMenuDialog(
            playerState = playerState,
            autoplayEnabled = uiState.autoplayEnabled,
            subtitlesEnabled = screenState.subtitlesEnabled,
            onDismiss = { screenState.showSettingsMenu = false },
            onShowQuality = { screenState.showQualitySelector = true },
            onShowAudio = { screenState.showAudioTrackSelector = true },
            onShowSpeed = { screenState.showPlaybackSpeedSelector = true },
            onShowSubtitles = { screenState.showSubtitleSelector = true },
            onAutoplayToggle = { viewModel.toggleAutoplay(it) },
            onSkipSilenceToggle = { viewModel.toggleSkipSilence(it) },
            onShowSubtitleStyle = { screenState.showSubtitleStyleCustomizer = true }
        )
    }

    // Playback speed selector
    if (screenState.showPlaybackSpeedSelector) {
        PlaybackSpeedSelectorDialog(
            currentSpeed = playerState.playbackSpeed,
            onDismiss = { screenState.showPlaybackSpeedSelector = false },
            onSpeedSelected = { speed ->
                EnhancedPlayerManager.getInstance().setPlaybackSpeed(speed)
            }
        )
    }

    // Subtitle Style Customizer
    if (screenState.showSubtitleStyleCustomizer) {
        SubtitleStyleCustomizerDialog(
            subtitleStyle = screenState.subtitleStyle,
            onStyleChange = { screenState.subtitleStyle = it },
            onDismiss = { screenState.showSubtitleStyleCustomizer = false }
        )
    }
}

/**
 * Individual dialog for quality selection
 */
@Composable
fun ShowQualityDialog(
    isVisible: Boolean,
    playerState: EnhancedPlayerState,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        QualitySelectorDialog(
            availableQualities = playerState.availableQualities,
            currentQuality = playerState.currentQuality,
            onDismiss = onDismiss,
            onQualitySelected = { height ->
                EnhancedPlayerManager.getInstance().switchQuality(height)
            }
        )
    }
}

/**
 * Individual dialog for audio track selection
 */
@Composable
fun ShowAudioTrackDialog(
    isVisible: Boolean,
    playerState: EnhancedPlayerState,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        AudioTrackSelectorDialog(
            availableAudioTracks = playerState.availableAudioTracks,
            currentAudioTrack = playerState.currentAudioTrack,
            onDismiss = onDismiss,
            onTrackSelected = { index ->
                EnhancedPlayerManager.getInstance().switchAudioTrack(index)
            }
        )
    }
}

/**
 * Individual dialog for playback speed
 */
@Composable
fun ShowPlaybackSpeedDialog(
    isVisible: Boolean,
    currentSpeed: Float,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        PlaybackSpeedSelectorDialog(
            currentSpeed = currentSpeed,
            onDismiss = onDismiss,
            onSpeedSelected = { speed ->
                EnhancedPlayerManager.getInstance().setPlaybackSpeed(speed)
            }
        )
    }
}
