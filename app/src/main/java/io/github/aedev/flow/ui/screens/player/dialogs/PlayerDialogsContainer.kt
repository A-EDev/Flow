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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun PlayerDialogsContainer(
    screenState: PlayerScreenState,
    playerState: EnhancedPlayerState,
    uiState: VideoPlayerUiState,
    video: Video,
    viewModel: VideoPlayerViewModel,
    renderSettingsMenu: Boolean = true,
    mediaSheetExpandedHeight: Dp? = null,
    mediaSheetCollapsedHeight: Dp = 0.dp,
    onMediaSheetProgressChange: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val playerPreferences = remember { PlayerPreferences(context) }
    val rememberPlaybackSpeed by playerPreferences.rememberPlaybackSpeed.collectAsState(initial = false)
    val ambientModeEnabled by playerPreferences.videoAmbientModeEnabled.collectAsState(initial = false)
    val groupedQualitySelectorEnabled by playerPreferences.groupedQualitySelectorEnabled.collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        playerPreferences.subtitleStyle.collect { style ->
            if (screenState.subtitleStyle != style) {
                screenState.subtitleStyle = style
            }
        }
    }

    // Download Quality Dialog
    val downloadDialogStyle by playerPreferences.downloadDialogStyle.collectAsState(initial = null)
    if (screenState.showDownloadDialog) {
        when (downloadDialogStyle) {
            io.github.aedev.flow.data.local.DownloadDialogStyle.COMPACT ->
                DownloadQualityDialogCompact(
                    streamInfo = uiState.streamInfo,
                    streamSizes = uiState.streamSizes,
                    innerTubeVideoFormats = uiState.innerTubeVideoFormats,
                    innerTubeAudioFormats = uiState.innerTubeAudioFormats,
                    video = video,
                    currentPlayingHeight = playerState.effectiveQuality,
                    onDismiss = { screenState.showDownloadDialog = false }
                )
            io.github.aedev.flow.data.local.DownloadDialogStyle.FULL ->
                DownloadQualityDialog(
                    streamInfo = uiState.streamInfo,
                    streamSizes = uiState.streamSizes,
                    innerTubeVideoFormats = uiState.innerTubeVideoFormats,
                    innerTubeAudioFormats = uiState.innerTubeAudioFormats,
                    video = video,
                    onDismiss = { screenState.showDownloadDialog = false }
                )
            null -> { }
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
            ambientModeEnabled = ambientModeEnabled,
            onAmbientModeToggle = { coroutineScope.launch { playerPreferences.setVideoAmbientModeEnabled(it) } },
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
            },
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            useGroupedQualitySelector = groupedQualitySelectorEnabled,
            onSheetProgressChange = onMediaSheetProgressChange
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
