package io.github.aedev.flow.ui.screens.player.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.dlna.DlnaCastManager
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.ui.components.shared.MediaDownloadDialog
import io.github.aedev.flow.ui.components.shared.MediaDownloadDialogCompact
import io.github.aedev.flow.ui.components.videoplayer.DlnaDevicePickerDialog
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.PlayerSheet
import kotlinx.coroutines.launch

@Composable
internal fun PlayerDialogsContainer(
    screenState: PlayerScreenState,
    playerState: EnhancedPlayerState,
    uiState: VideoPlayerUiState,
    video: Video,
    viewModel: VideoPlayerViewModel,
    hostedInSidePanel: Boolean = false,
    mediaSheetExpandedHeight: Dp? = null,
    mediaSheetCollapsedHeight: Dp = 0.dp,
    onMediaSheetProgressChange: (Float) -> Unit = {},
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
    if (screenState.activeSheet == PlayerSheet.Download) {
        when (downloadDialogStyle) {
            io.github.aedev.flow.data.local.DownloadDialogStyle.COMPACT -> {
                MediaDownloadDialogCompact(
                    streamInfo = uiState.streamInfo,
                    streamSizes = uiState.streamSizes,
                    innerTubeVideoFormats = uiState.innerTubeVideoFormats,
                    innerTubeAudioFormats = uiState.innerTubeAudioFormats,
                    video = video,
                    currentPlayingHeight = playerState.effectiveQuality,
                    onDismiss = { screenState.closeSheet() },
                )
            }

            io.github.aedev.flow.data.local.DownloadDialogStyle.FULL -> {
                MediaDownloadDialog(
                    streamInfo = uiState.streamInfo,
                    streamSizes = uiState.streamSizes,
                    innerTubeVideoFormats = uiState.innerTubeVideoFormats,
                    innerTubeAudioFormats = uiState.innerTubeAudioFormats,
                    video = video,
                    onDismiss = { screenState.closeSheet() },
                )
            }

            null -> { }
        }
    }

    if (screenState.isSettingsOpen && !hostedInSidePanel) {
        PlayerSettingsSheetHost(
            screenState = screenState,
            playerState = playerState,
            uiState = uiState,
            viewModel = viewModel,
            playerPreferences = playerPreferences,
            scope = coroutineScope,
            rememberPlaybackSpeed = rememberPlaybackSpeed,
            ambientModeEnabled = ambientModeEnabled,
            groupedQualitySelectorEnabled = groupedQualitySelectorEnabled,
            rememberSubtitleLanguage = { language ->
                coroutineScope.launch { playerPreferences.setPreferredSubtitleLanguage(language) }
            },
            asSidePanel = false,
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            pipAspectRatio = null,
            onSheetProgressChange = onMediaSheetProgressChange,
            onDismiss = { screenState.closeSheet() },
        )
    }

    if (screenState.activeSheet == PlayerSheet.Dlna) {
        val dlnaDevices by DlnaCastManager.devices.collectAsState()
        val isDlnaDiscovering by DlnaCastManager.isDiscovering.collectAsState()
        DlnaDevicePickerDialog(
            devices = dlnaDevices,
            isDiscovering = isDlnaDiscovering,
            isCasting = DlnaCastManager.isCasting,
            videoTitle = video.title,
            onDeviceSelected = { device ->
                val currentPlayerUrl =
                    EnhancedPlayerManager
                        .getInstance()
                        .getPlayer()
                        ?.currentMediaItem
                        ?.localConfiguration
                        ?.uri
                        ?.toString()
                DlnaCastManager.castStreamInfo(
                    device = device,
                    title = video.title,
                    streamInfo = uiState.streamInfo,
                    currentPlayerUrl = currentPlayerUrl,
                )
                screenState.closeSheet()
            },
            onStopCasting = {
                DlnaCastManager.disconnect()
                screenState.closeSheet()
            },
            onDismiss = {
                DlnaCastManager.stopDiscovery()
                screenState.closeSheet()
            },
        )
    }

    // Subtitle Style Customizer
    if (screenState.activeSheet == PlayerSheet.SubtitleStyle) {
        SubtitleStyleSheet(
            subtitleStyle = screenState.subtitleStyle,
            onStyleChange = {
                screenState.subtitleStyle = it
                coroutineScope.launch { playerPreferences.setSubtitleStyle(it) }
            },
            onDismiss = { screenState.closeSheet() },
            onBack = { screenState.open(PlayerSheet.Settings()) },
        )
    }
}
