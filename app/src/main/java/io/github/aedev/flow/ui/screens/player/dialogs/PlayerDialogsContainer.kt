package io.github.aedev.flow.ui.screens.player.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.components.*
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun PlayerDialogsContainer(
    screenState: PlayerScreenState,
    playerState: EnhancedPlayerState,
    uiState: VideoPlayerUiState,
    video: Video,
    viewModel: VideoPlayerViewModel
) {
    val context = LocalContext.current
    val playerPreferences = remember { PlayerPreferences(context) }
    val rememberPlaybackSpeed by playerPreferences.rememberPlaybackSpeed.collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()

    // Download Quality Dialog
    if (screenState.showDownloadDialog) {
        DownloadQualityDialog(
            streamInfo = uiState.streamInfo,
            streamSizes = uiState.streamSizes,
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
            },
            onBack = {
                screenState.showQualitySelector = false
                screenState.showSettingsMenu = true
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
            },
            onBack = {
                screenState.showAudioTrackSelector = false
                screenState.showSettingsMenu = true
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
            },
            onBack = {
                screenState.showSubtitleSelector = false
                screenState.showSettingsMenu = true
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
            onShowQuality = { 
                screenState.showSettingsMenu = false
                screenState.showQualitySelector = true 
            },
            onShowAudio = { 
                screenState.showSettingsMenu = false
                screenState.showAudioTrackSelector = true 
            },
            onShowSpeed = { 
                screenState.showSettingsMenu = false
                screenState.showPlaybackSpeedSelector = true 
            },
            onShowSubtitles = { 
                screenState.showSettingsMenu = false
                screenState.showSubtitleSelector = true 
            },
            onAutoplayToggle = { viewModel.toggleAutoplay(it) },
            onSkipSilenceToggle = { viewModel.toggleSkipSilence(it) },
            onStableVolumeToggle = { viewModel.toggleStableVolume(it) },
            onShowSubtitleStyle = { 
                screenState.showSettingsMenu = false
                screenState.showSubtitleStyleCustomizer = true 
            },
            onLoopToggle = { viewModel.toggleLoop(it) },
            onCastClick = {
                io.github.aedev.flow.player.dlna.DlnaCastManager.startDiscovery(context)
                screenState.showSettingsMenu = false
                screenState.showDlnaDialog = true
            },
            onPipClick = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                    io.github.aedev.flow.player.PictureInPictureHelper.isPipSupported(context)) {
                    screenState.showSettingsMenu = false
                    io.github.aedev.flow.player.PictureInPictureHelper.enterPipMode(
                        activity = context as androidx.activity.ComponentActivity,
                        isPlaying = playerState.isPlaying
                    )
                }
            },
            onSleepTimerClick = {
                screenState.showSettingsMenu = false
                screenState.showSleepTimerSheet = true
            }
        )
    }

    // Playback speed selector
    if (screenState.showPlaybackSpeedSelector) {
        PlaybackSpeedSelectorDialog(
            currentSpeed = playerState.playbackSpeed,
            onDismiss = { screenState.showPlaybackSpeedSelector = false },
            onSpeedSelected = { speed ->
                EnhancedPlayerManager.getInstance().setPlaybackSpeed(speed)
                screenState.normalSpeed = speed
                if (rememberPlaybackSpeed) {
                    coroutineScope.launch { playerPreferences.setPlaybackSpeed(speed) }
                }
            },
            onBack = {
                screenState.showPlaybackSpeedSelector = false
                screenState.showSettingsMenu = true
            }
        )
    }

    // Subtitle Style Customizer
    if (screenState.showSubtitleStyleCustomizer) {
        SubtitleStyleCustomizerDialog(
            subtitleStyle = screenState.subtitleStyle,
            onStyleChange = { screenState.subtitleStyle = it },
            onDismiss = { screenState.showSubtitleStyleCustomizer = false },
            onBack = {
                screenState.showSubtitleStyleCustomizer = false
                screenState.showSettingsMenu = true
            }
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
