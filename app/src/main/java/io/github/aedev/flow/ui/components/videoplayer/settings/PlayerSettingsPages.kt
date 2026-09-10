package io.github.aedev.flow.ui.components.videoplayer.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.AudioTrackOption
import io.github.aedev.flow.player.QualityOption
import io.github.aedev.flow.player.SubtitleOption
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.components.shared.FlowSelectionRow
import io.github.aedev.flow.ui.components.shared.MediaAudioTrackRow
import io.github.aedev.flow.ui.components.shared.MediaPlaybackSpeedPicker
import io.github.aedev.flow.ui.components.shared.MediaQualitySelectorContent
import io.github.aedev.flow.ui.components.shared.MediaQualitySelectorOption
import io.github.aedev.flow.ui.components.shared.audioTrackFallbackLabel

@Composable
internal fun PlayerSettingsQualityPage(
    availableQualities: List<QualityOption>,
    currentQuality: Int,
    currentQualityKey: String?,
    useGroupedQualitySelector: Boolean,
    onQualitySelected: (QualityOption) -> Unit,
) {
    val autoLabel = stringResource(R.string.quality_auto)
    val selectorOptions =
        availableQualities.map { quality ->
            MediaQualitySelectorOption(
                item = quality,
                height = quality.height,
                label = if (quality.height == 0) autoLabel else quality.displayLabel(),
                codecKey = quality.codecKey,
                codecLabel =
                    quality.codecKey
                        .takeIf { it.isNotBlank() }
                        ?.let(VideoCodecUtils::codecLabelFromKey)
                        .orEmpty(),
                selected = quality.isSelected(currentQuality, currentQualityKey),
            )
        }

    MediaQualitySelectorContent(
        options = selectorOptions,
        groupedByResolution = useGroupedQualitySelector,
        onOptionSelected = onQualitySelected,
    )
}

private fun QualityOption.displayLabel(): String = label.takeIf { it.isNotBlank() } ?: "${height}p"

private fun QualityOption.isSelected(
    currentQuality: Int,
    currentQualityKey: String?,
): Boolean =
    if (height == 0) {
        currentQuality == 0
    } else {
        streamKey != null && streamKey == currentQualityKey
    }

@Composable
internal fun PlayerSettingsSpeedPage(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onSpeedSelectionFinished: () -> Unit,
) {
    val context = LocalContext.current
    val playerPrefs =
        remember {
            io.github.aedev.flow.data.local
                .PlayerPreferences(context)
        }
    val customSpeedsEnabled by playerPrefs.customSpeedsEnabled.collectAsState(initial = false)
    val customSpeedPresetsRaw by playerPrefs.customSpeedPresets.collectAsState(initial = "")
    val speedSliderEnabled by playerPrefs.speedSliderEnabled.collectAsState(initial = false)

    MediaPlaybackSpeedPicker(
        currentSpeed = currentSpeed,
        sliderEnabled = speedSliderEnabled,
        customSpeedsEnabled = customSpeedsEnabled,
        customSpeedPresetsRaw = customSpeedPresetsRaw,
        onSpeedSelected = onSpeedSelected,
        onSpeedRowSelected = { onSpeedSelectionFinished() },
    )
}

@Composable
internal fun PlayerSettingsAudioPage(
    availableAudioTracks: List<AudioTrackOption>,
    currentAudioTrack: Int,
    onTrackSelected: (Int) -> Unit,
) {
    availableAudioTracks.forEachIndexed { index, track ->
        MediaAudioTrackRow(
            label = audioTrackDisplayLabel(track, index),
            supportingText = track.language.takeIf { it.isNotBlank() },
            selected = index == currentAudioTrack,
            onClick = { onTrackSelected(index) },
        )
    }
}

@Composable
internal fun PlayerSettingsSubtitlesPage(
    availableSubtitles: List<SubtitleOption>,
    selectedSubtitleUrl: String?,
    subtitlesEnabled: Boolean,
    onSubtitleSelected: (Int) -> Unit,
    onDisableSubtitles: () -> Unit,
    onShowStyleCustomizer: () -> Unit,
) {
    val automaticLabel = stringResource(R.string.quality_auto)
    val translatedLabel = stringResource(R.string.subtitle_translated)
    FlowSelectionRow(
        title = stringResource(R.string.off),
        selected = !subtitlesEnabled,
        onClick = onDisableSubtitles,
    )
    availableSubtitles.forEachIndexed { index, subtitle ->
        FlowSelectionRow(
            title =
                when {
                    subtitle.isTranslated -> {
                        stringResource(
                            R.string.subtitle_auto_generated_template,
                            subtitle.label,
                            translatedLabel,
                        )
                    }

                    subtitle.isAutoGenerated -> {
                        stringResource(
                            R.string.subtitle_auto_generated_template,
                            subtitle.label,
                            automaticLabel,
                        )
                    }

                    else -> {
                        subtitle.label
                    }
                },
            supportingText = subtitle.language.takeIf { it.isNotBlank() },
            selected = subtitle.url == selectedSubtitleUrl && subtitlesEnabled,
            onClick = { onSubtitleSelected(index) },
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    FlowNavRow(
        title = stringResource(R.string.subtitle_style),
        leadingIcon = Icons.Filled.Tune,
        onClick = onShowStyleCustomizer,
    )
}

@Composable
internal fun audioTrackDisplayLabel(
    track: AudioTrackOption?,
    fallbackIndex: Int,
): String = track?.label?.takeIf { it.isNotBlank() } ?: audioTrackFallbackLabel(fallbackIndex)
