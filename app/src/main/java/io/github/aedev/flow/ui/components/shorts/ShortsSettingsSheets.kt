package io.github.aedev.flow.ui.components.shorts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.shorts.ShortAudioTrack
import io.github.aedev.flow.data.shorts.ShortVideoQuality
import io.github.aedev.flow.player.QualityOption
import io.github.aedev.flow.ui.components.shared.FlowRowGroup
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.MediaAudioTrackRow
import io.github.aedev.flow.ui.components.shared.MediaPlaybackSpeedPicker
import io.github.aedev.flow.ui.components.shared.audioTrackBitrateLabel
import io.github.aedev.flow.ui.components.shared.audioTrackFallbackLabel
import io.github.aedev.flow.ui.components.shared.flowRowGroupShape
import io.github.aedev.flow.ui.components.videoplayer.settings.PlayerSettingsQualityPage

private val SheetBottomPadding = 32.dp

@Composable
internal fun ShortsSpeedSheet(
    currentSpeed: Float,
    speedSliderEnabled: Boolean,
    customSpeedsEnabled: Boolean,
    customSpeedPresetsRaw: String,
    onSpeedSelected: (Float) -> Unit,
    onSpeedSelectionFinished: (Float) -> Unit,
    sheetInsets: ShortsSheetInsetState,
    onDismiss: () -> Unit,
) {
    ShortsSettingsSheet(title = stringResource(R.string.shorts_playback_speed), sheetInsets = sheetInsets, onDismiss = onDismiss) {
        MediaPlaybackSpeedPicker(
            currentSpeed = currentSpeed,
            sliderEnabled = speedSliderEnabled,
            customSpeedsEnabled = customSpeedsEnabled,
            customSpeedPresetsRaw = customSpeedPresetsRaw,
            onSpeedSelected = onSpeedSelected,
            onSliderSelectionFinished = onSpeedSelectionFinished,
            onSpeedRowSelected = { speed ->
                onSpeedSelectionFinished(speed)
                onDismiss()
            },
        )
    }
}

@Composable
internal fun ShortsAudioTrackSheet(
    audioTracks: List<ShortAudioTrack>,
    selectedIndex: Int,
    onTrackSelected: (Int) -> Unit,
    sheetInsets: ShortsSheetInsetState,
    onDismiss: () -> Unit,
) {
    ShortsSettingsSheet(title = stringResource(R.string.shorts_audio_track), sheetInsets = sheetInsets, onDismiss = onDismiss) {
        FlowRowGroup {
            audioTracks.forEachIndexed { index, track ->
                MediaAudioTrackRow(
                    label = track.label.ifBlank { audioTrackFallbackLabel(index) },
                    supportingText = audioTrackBitrateLabel(track.bitrate),
                    selected = index == selectedIndex,
                    shape = flowRowGroupShape(index, audioTracks.size),
                    onClick = { onTrackSelected(index) },
                )
            }
        }
    }
}

@Composable
internal fun ShortsQualitySheet(
    qualities: List<ShortVideoQuality>,
    selectedHeight: Int?,
    selectedVideoUrl: String?,
    groupedByResolution: Boolean,
    onQualitySelected: (ShortVideoQuality) -> Unit,
    sheetInsets: ShortsSheetInsetState,
    onDismiss: () -> Unit,
) {
    val options =
        remember(qualities) {
            qualities.map { quality ->
                QualityOption(
                    height = quality.heightClass,
                    label = quality.label,
                    bitrate = 0L,
                    codecKey = quality.codecKey,
                    streamKey = quality.videoUrl,
                )
            }
        }
    val selectedKey = selectedVideoUrl ?: qualities.firstOrNull { it.heightClass == selectedHeight }?.videoUrl
    ShortsSettingsSheet(title = stringResource(R.string.shorts_quality), sheetInsets = sheetInsets, onDismiss = onDismiss) {
        PlayerSettingsQualityPage(
            availableQualities = options,
            currentQuality = -1,
            currentQualityKey = selectedKey,
            useGroupedQualitySelector = groupedByResolution,
            onQualitySelected = { option ->
                qualities.firstOrNull { it.videoUrl == option.streamKey }?.let(onQualitySelected)
            },
        )
    }
}

@Composable
private fun ShortsSettingsSheet(
    title: String,
    sheetInsets: ShortsSheetInsetState,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ShortsPlayerSheet(insets = sheetInsets, onDismiss = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = SheetBottomPadding),
        ) {
            FlowSheetHeader(title = title, onClose = onDismiss)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        }
    }
}
