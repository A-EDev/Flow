package io.github.aedev.flow.ui.components.videoplayer.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedPlayerState
import io.github.aedev.flow.player.QualityOption
import io.github.aedev.flow.ui.components.audio.EqualizerEditor
import io.github.aedev.flow.ui.components.shared.FlowBottomSheet
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.defaultSheetExpandedHeight
import io.github.aedev.flow.ui.components.shared.rememberFlowBottomSheetState

@Composable
fun SettingsMenuDialog(
    playerState: EnhancedPlayerState,
    autoplayEnabled: Boolean,
    subtitlesEnabled: Boolean,
    onDismiss: () -> Unit,
    initialPage: PlayerSettingsPage = PlayerSettingsPage.Main,
    onQualitySelected: (QualityOption) -> Unit = {},
    onAudioTrackSelected: (Int) -> Unit = {},
    onSpeedSelected: (Float) -> Unit = {},
    selectedSubtitleUrl: String? = null,
    onSubtitleSelected: (Int) -> Unit = {},
    onDisableSubtitles: () -> Unit = {},
    onAutoplayToggle: (Boolean) -> Unit,
    onSkipSilenceToggle: (Boolean) -> Unit,
    onStableVolumeToggle: (Boolean) -> Unit,
    onShowSubtitleStyle: () -> Unit,
    onLoopToggle: (Boolean) -> Unit,
    ambientModeEnabled: Boolean = false,
    onAmbientModeToggle: (Boolean) -> Unit = {},
    onCastClick: () -> Unit = {},
    onPipClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
    enableVerticalDismiss: Boolean = true,
    useGroupedQualitySelector: Boolean = false,
    onSheetProgressChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberFlowBottomSheetState()
    var currentPage by remember { mutableStateOf(initialPage) }
    val currentTitle =
        when (currentPage) {
            PlayerSettingsPage.Main -> stringResource(R.string.player_settings)
            PlayerSettingsPage.Quality -> stringResource(R.string.video_quality_title)
            PlayerSettingsPage.Speed -> stringResource(R.string.playback_speed)
            PlayerSettingsPage.Audio -> stringResource(R.string.audio_track)
            PlayerSettingsPage.Subtitles -> stringResource(R.string.filter_subtitles)
            PlayerSettingsPage.Equalizer -> stringResource(R.string.equalizer)
        }

    LaunchedEffect(initialPage) {
        currentPage = initialPage
    }

    FlowBottomSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        state = sheetState,
        expandedHeight = expandedHeight ?: defaultSheetExpandedHeight(),
        collapsedHeight = collapsedHeight,
        dismissible = enableVerticalDismiss,
        dismissOnOutsideTap = false,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        onBack =
            if (currentPage == PlayerSettingsPage.Main) {
                null
            } else {
                { currentPage = PlayerSettingsPage.Main }
            },
        onProgressChange = onSheetProgressChange,
        header = { dragModifier ->
            FlowSheetHeader(
                title = currentTitle,
                onClose = { sheetState.dismiss() },
                modifier = dragModifier,
                onBack =
                    if (currentPage == PlayerSettingsPage.Main) {
                        null
                    } else {
                        { currentPage = PlayerSettingsPage.Main }
                    },
                contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
                closeButtonSize = null,
                dividerAlpha = 0.4f,
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            when (currentPage) {
                PlayerSettingsPage.Main -> {
                    PlayerSettingsMainPage(
                        playerState = playerState,
                        autoplayEnabled = autoplayEnabled,
                        subtitlesEnabled = subtitlesEnabled,
                        ambientModeEnabled = ambientModeEnabled,
                        onNavigateToPage = { currentPage = it },
                        onShowSubtitleStyle = onShowSubtitleStyle,
                        onCastClick = { sheetState.dismiss(onCastClick) },
                        onPipClick = { sheetState.dismiss(onPipClick) },
                        onSleepTimerClick = { sheetState.dismiss(onSleepTimerClick) },
                        onLoopToggle = onLoopToggle,
                        onAutoplayToggle = onAutoplayToggle,
                        onSkipSilenceToggle = onSkipSilenceToggle,
                        onStableVolumeToggle = onStableVolumeToggle,
                        onAmbientModeToggle = onAmbientModeToggle,
                    )
                }

                PlayerSettingsPage.Quality -> {
                    PlayerSettingsQualityPage(
                        availableQualities = playerState.availableQualities,
                        currentQuality = playerState.currentQuality,
                        currentQualityKey = playerState.currentQualityKey,
                        useGroupedQualitySelector = useGroupedQualitySelector,
                        onQualitySelected = {
                            onQualitySelected(it)
                            sheetState.dismiss()
                        },
                    )
                }

                PlayerSettingsPage.Speed -> {
                    PlayerSettingsSpeedPage(
                        currentSpeed = playerState.playbackSpeed,
                        onSpeedSelected = onSpeedSelected,
                        onSpeedSelectionFinished = { sheetState.dismiss() },
                    )
                }

                PlayerSettingsPage.Audio -> {
                    PlayerSettingsAudioPage(
                        availableAudioTracks = playerState.availableAudioTracks,
                        currentAudioTrack = playerState.currentAudioTrack,
                        onTrackSelected = {
                            onAudioTrackSelected(it)
                            sheetState.dismiss()
                        },
                    )
                }

                PlayerSettingsPage.Equalizer -> {
                    EqualizerEditor(
                        modifier =
                            Modifier
                                .padding(horizontal = 20.dp)
                                .padding(top = 8.dp, bottom = 16.dp),
                    )
                }

                PlayerSettingsPage.Subtitles -> {
                    PlayerSettingsSubtitlesPage(
                        availableSubtitles = playerState.availableSubtitles,
                        selectedSubtitleUrl = selectedSubtitleUrl,
                        subtitlesEnabled = subtitlesEnabled,
                        onSubtitleSelected = { index ->
                            onSubtitleSelected(index)
                            sheetState.dismiss()
                        },
                        onDisableSubtitles = {
                            onDisableSubtitles()
                            sheetState.dismiss()
                        },
                        onShowStyleCustomizer = {
                            currentPage = PlayerSettingsPage.Main
                            sheetState.dismiss(onShowSubtitleStyle)
                        },
                    )
                }
            }
        }
    }
}

enum class PlayerSettingsPage {
    Main,
    Quality,
    Speed,
    Audio,
    Subtitles,
    Equalizer,
}
