package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R

/**
 * The playback speed picker shared by the video player settings sheet and the Shorts speed sheet:
 * either the continuous slider or the discrete list, decided by the user's slider preference.
 *
 * The two "finished" callbacks are not interchangeable. A slider release only commits a value,
 * while tapping a row also closes the surface the picker sits in — collapsing them would dismiss
 * the sheet mid-drag.
 */
@Composable
fun MediaPlaybackSpeedPicker(
    currentSpeed: Float,
    sliderEnabled: Boolean,
    customSpeedsEnabled: Boolean,
    customSpeedPresetsRaw: String,
    onSpeedSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onSliderSelectionFinished: (Float) -> Unit = {},
    onSpeedRowSelected: (Float) -> Unit = {},
) {
    if (sliderEnabled) {
        val sliderPresets =
            remember(customSpeedsEnabled, customSpeedPresetsRaw) {
                playbackSpeedSliderPresets(customSpeedsEnabled, customSpeedPresetsRaw)
            }
        MediaPlaybackSpeedSlider(
            currentSpeed = currentSpeed,
            quickPresets = sliderPresets,
            onSpeedSelected = onSpeedSelected,
            onSpeedSelectionFinished = onSliderSelectionFinished,
            modifier = modifier,
        )
        return
    }

    val speeds =
        remember(customSpeedsEnabled, customSpeedPresetsRaw) {
            playbackSpeedOptions(customSpeedsEnabled, customSpeedPresetsRaw)
        }
    Column(modifier = modifier.fillMaxWidth()) {
        speeds.forEach { speed ->
            FlowSelectionRow(
                title = playbackSpeedLabel(speed),
                selected = speed == currentSpeed,
                onClick = {
                    onSpeedSelected(speed)
                    onSpeedRowSelected(speed)
                },
            )
        }
    }
}

/** The label for a playback speed, so every surface spells "1.5×" and "Normal" the same way. */
@Composable
fun playbackSpeedLabel(speed: Float): String =
    if (speed == NORMAL_PLAYBACK_SPEED) {
        stringResource(R.string.normal)
    } else {
        stringResource(R.string.playback_speed_multiplier, speed.toString())
    }
