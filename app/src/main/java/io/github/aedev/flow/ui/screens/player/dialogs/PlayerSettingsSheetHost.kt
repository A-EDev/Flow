package io.github.aedev.flow.ui.screens.player.dialogs

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.PictureInPictureHelper
import io.github.aedev.flow.player.dlna.DlnaCastManager
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.ui.components.videoplayer.settings.SettingsMenuDialog
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.PlayerSheet
import io.github.aedev.flow.ui.screens.player.state.SubtitleSelection
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The single wiring of the player settings sheet. Both the bottom-sheet slot over the portrait
 * player and the landscape fullscreen side panel render it; [asSidePanel] is the only thing that
 * separates the two, alongside the height each has to fill.
 *
 * @param pipAspectRatio aspect ratio to hand the PiP request, or null to let the helper pick one.
 */
@Composable
internal fun PlayerSettingsSheetHost(
    screenState: PlayerScreenState,
    playerState: EnhancedPlayerState,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    playerPreferences: PlayerPreferences,
    scope: CoroutineScope,
    rememberPlaybackSpeed: Boolean,
    ambientModeEnabled: Boolean,
    groupedQualitySelectorEnabled: Boolean,
    rememberSubtitleLanguage: (String) -> Unit,
    asSidePanel: Boolean,
    expandedHeight: Dp?,
    onDismiss: () -> Unit,
    collapsedHeight: Dp = 0.dp,
    pipAspectRatio: Float? = null,
    onSheetProgressChange: (Float) -> Unit = {},
) {
    val context = LocalContext.current

    SettingsMenuDialog(
        playerState = playerState,
        autoplayEnabled = uiState.autoplayEnabled,
        subtitlesEnabled = screenState.subtitlesEnabled,
        initialPage = screenState.settingsPage,
        onDismiss = onDismiss,
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
                scope.launch { playerPreferences.setPlaybackSpeed(speed) }
            }
        },
        selectedSubtitleUrl = screenState.selectedSubtitleUrl,
        onSubtitleSelected = { index ->
            SubtitleSelection.applyAt(
                screenState = screenState,
                subtitles = playerState.availableSubtitles,
                index = index,
                rememberLanguage = rememberSubtitleLanguage,
            )
        },
        onDisableSubtitles = { SubtitleSelection.disable(screenState) },
        onAutoplayToggle = { viewModel.toggleAutoplay(it) },
        onSkipSilenceToggle = { viewModel.toggleSkipSilence(it) },
        onStableVolumeToggle = { viewModel.toggleStableVolume(it) },
        subtitleStyle = screenState.subtitleStyle,
        onSubtitleStyleChange = { style ->
            screenState.subtitleStyle = style
            scope.launch { playerPreferences.setSubtitleStyle(style) }
        },
        onLoopToggle = { viewModel.toggleLoop(it) },
        ambientModeEnabled = ambientModeEnabled,
        onAmbientModeToggle = { scope.launch { playerPreferences.setVideoAmbientModeEnabled(it) } },
        onCastClick = {
            DlnaCastManager.startDiscovery(context)
            screenState.open(PlayerSheet.Dlna)
        },
        onPipClick = {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                PictureInPictureHelper.isPlayerPopupSupported(context)
            ) {
                onDismiss()
                PictureInPictureHelper.requestPlayerPipMode(
                    activity = context as ComponentActivity,
                    aspectRatio = pipAspectRatio ?: PictureInPictureHelper.currentVideoAspectRatio,
                    isPlaying = playerState.isPlaying,
                )
            }
        },
        onSleepTimerClick = { screenState.open(PlayerSheet.SleepTimer) },
        expandedHeight = expandedHeight,
        collapsedHeight = collapsedHeight,
        enableVerticalDismiss = !asSidePanel,
        useGroupedQualitySelector = groupedQualitySelectorEnabled,
        onSheetProgressChange = onSheetProgressChange,
        modifier = if (asSidePanel) Modifier.fillMaxSize() else Modifier,
    )
}
