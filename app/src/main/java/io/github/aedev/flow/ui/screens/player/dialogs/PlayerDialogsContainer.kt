package io.github.aedev.flow.ui.screens.player.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.aedev.flow.ui.screens.player.components.PlayerSettingsPage
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun PlayerDialogsContainer(
    screenState: PlayerScreenState,
    playerState: EnhancedPlayerState,
    uiState: VideoPlayerUiState,
    video: Video,
    viewModel: VideoPlayerViewModel,
    renderSettingsMenu: Boolean = true
) {
    val context = LocalContext.current
    val playerPreferences = remember { PlayerPreferences(context) }
    val rememberPlaybackSpeed by playerPreferences.rememberPlaybackSpeed.collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        playerPreferences.subtitleStyle.collect { style ->
            if (screenState.subtitleStyle != style) {
                screenState.subtitleStyle = style
            }
        }
    }

    // Download Quality Dialog
    if (screenState.showDownloadDialog) {
        val downloadDialogStyle by playerPreferences.downloadDialogStyle.collectAsState(
            initial = io.github.aedev.flow.data.local.DownloadDialogStyle.FULL
        )
        if (downloadDialogStyle == io.github.aedev.flow.data.local.DownloadDialogStyle.COMPACT) {
            DownloadQualityDialogCompact(
                streamInfo = uiState.streamInfo,
                streamSizes = uiState.streamSizes,
                innerTubeVideoFormats = uiState.innerTubeVideoFormats,
                innerTubeAudioFormats = uiState.innerTubeAudioFormats,
                video = video,
                onDismiss = { screenState.showDownloadDialog = false }
            )
        } else {
            DownloadQualityDialog(
                streamInfo = uiState.streamInfo,
                streamSizes = uiState.streamSizes,
                innerTubeVideoFormats = uiState.innerTubeVideoFormats,
                innerTubeAudioFormats = uiState.innerTubeAudioFormats,
                video = video,
                onDismiss = { screenState.showDownloadDialog = false }
            )
        }
    }

    val settingsInitialPage = when {
        screenState.showQualitySelector -> PlayerSettingsPage.Quality
        screenState.showAudioTrackSelector -> PlayerSettingsPage.Audio
        screenState.showPlaybackSpeedSelector -> PlayerSettingsPage.Speed
        screenState.showSubtitleSelector -> PlayerSettingsPage.Subtitles
        else -> PlayerSettingsPage.Main
    }
    val showSettingsSurface = screenState.showSettingsMenu ||
        screenState.showQualitySelector ||
        screenState.showAudioTrackSelector ||
        screenState.showPlaybackSpeedSelector ||
        screenState.showSubtitleSelector

    if (false && screenState.showQualitySelector) {
        QualitySelectorDialog(
            availableQualities = playerState.availableQualities,
            currentQuality = playerState.currentQuality,
            currentQualityKey = playerState.currentQualityKey,
            onDismiss = { screenState.showQualitySelector = false },
            onQualitySelected = { option ->
                EnhancedPlayerManager.getInstance().switchQuality(option)
            },
            onBack = {
                screenState.showQualitySelector = false
                screenState.showSettingsMenu = true
            }
        )
    }
    
    // Audio track selector
    if (false && screenState.showAudioTrackSelector) {
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
    if (false && screenState.showSubtitleSelector) {
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
                EnhancedPlayerManager.getInstance().selectSubtitle(null)
                screenState.disableSubtitles()
            },
            onShowStyleCustomizer = {
                screenState.showSubtitleSelector = false
                screenState.showSubtitleStyleCustomizer = true
            },
            onBack = {
                screenState.showSubtitleSelector = false
                screenState.showSettingsMenu = true
            }
        )
    }
    
    // Settings menu
    if (showSettingsSurface && renderSettingsMenu) {
        SettingsMenuDialog(
            playerState = playerState,
            autoplayEnabled = uiState.autoplayEnabled,
            subtitlesEnabled = screenState.subtitlesEnabled,
            initialPage = settingsInitialPage,
            onDismiss = {
                screenState.showSettingsMenu = false
                screenState.showQualitySelector = false
                screenState.showAudioTrackSelector = false
                screenState.showPlaybackSpeedSelector = false
                screenState.showSubtitleSelector = false
            },
            onQualitySelected = { option ->
                EnhancedPlayerManager.getInstance().switchQuality(option)
            },
            onAudioTrackSelected = { index ->
                EnhancedPlayerManager.getInstance().switchAudioTrack(index)
            },
            onSpeedSelected = { speed ->
                EnhancedPlayerManager.getInstance().setPlaybackSpeed(speed)
                screenState.normalSpeed = speed
                if (rememberPlaybackSpeed) {
                    coroutineScope.launch { playerPreferences.setPlaybackSpeed(speed) }
                }
            },
            selectedSubtitleUrl = screenState.selectedSubtitleUrl,
            onSubtitleSelected = { index, url ->
                screenState.selectedSubtitleUrl = url
                EnhancedPlayerManager.getInstance().selectSubtitle(index)
                screenState.subtitlesEnabled = true
            },
            onDisableSubtitles = {
                EnhancedPlayerManager.getInstance().selectSubtitle(null)
                screenState.disableSubtitles()
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
                    io.github.aedev.flow.player.PictureInPictureHelper.requestPlayerPipMode(
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
    if (false && screenState.showPlaybackSpeedSelector) {
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
            onStyleChange = {
                screenState.subtitleStyle = it
                coroutineScope.launch { playerPreferences.setSubtitleStyle(it) }
            },
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
            currentQualityKey = playerState.currentQualityKey,
            onDismiss = onDismiss,
            onQualitySelected = { option ->
                EnhancedPlayerManager.getInstance().switchQuality(option)
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
