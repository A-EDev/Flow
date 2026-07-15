package io.github.aedev.flow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

private const val MIN_PLAYBACK_SPEED = 0.1f
private const val NORMAL_PLAYBACK_SPEED = 1.0f
private const val MAX_SLIDER_PLAYBACK_SPEED = 4.0f
private const val PLAYBACK_SPEED_STEP = 0.05f

private val defaultPlaybackSpeeds = listOf(
    0.25f,
    0.5f,
    0.75f,
    1.0f,
    1.25f,
    1.5f,
    1.75f,
    2.0f,
    2.5f,
    3.0f,
    3.5f,
    4.0f
)

private val defaultSliderPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

internal fun playbackSpeedOptions(
    customSpeedsEnabled: Boolean,
    customSpeedPresetsRaw: String
): List<Float> {
    if (!customSpeedsEnabled || customSpeedPresetsRaw.isBlank()) return defaultPlaybackSpeeds

    return customSpeedPresetsRaw
        .split(",")
        .mapNotNull { it.trim().toFloatOrNull() }
        .filter { it in MIN_PLAYBACK_SPEED..10.0f }
        .distinct()
        .sorted()
        .ifEmpty { defaultPlaybackSpeeds }
}

internal fun playbackSpeedSliderPresets(
    customSpeedsEnabled: Boolean,
    customSpeedPresetsRaw: String
): List<Float> = if (customSpeedsEnabled) {
    playbackSpeedOptions(customSpeedsEnabled = true, customSpeedPresetsRaw)
        .filter { it <= MAX_SLIDER_PLAYBACK_SPEED }
} else {
    defaultSliderPresets
}

private fun sliderToSpeed(progress: Float): Float {
    val center = 0.5f
    return if (progress <= center) {
        val t = progress / center
        MIN_PLAYBACK_SPEED + t * t * (NORMAL_PLAYBACK_SPEED - MIN_PLAYBACK_SPEED)
    } else {
        val t = (progress - center) / center
        NORMAL_PLAYBACK_SPEED + t * t * (MAX_SLIDER_PLAYBACK_SPEED - NORMAL_PLAYBACK_SPEED)
    }
}

private fun speedToSlider(speed: Float): Float {
    val clamped = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_SLIDER_PLAYBACK_SPEED)
    return if (clamped <= NORMAL_PLAYBACK_SPEED) {
        val t = sqrt(
            (clamped - MIN_PLAYBACK_SPEED) /
                (NORMAL_PLAYBACK_SPEED - MIN_PLAYBACK_SPEED)
        )
        t * 0.5f
    } else {
        val t = sqrt(
            (clamped - NORMAL_PLAYBACK_SPEED) /
                (MAX_SLIDER_PLAYBACK_SPEED - NORMAL_PLAYBACK_SPEED)
        )
        0.5f + t * 0.5f
    }
}

private fun roundedPlaybackSpeed(progress: Float): Float =
    (round(sliderToSpeed(progress).toDouble() / PLAYBACK_SPEED_STEP) * PLAYBACK_SPEED_STEP)
        .toFloat()
        .coerceIn(MIN_PLAYBACK_SPEED, MAX_SLIDER_PLAYBACK_SPEED)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaybackSpeedSlider(
    currentSpeed: Float,
    quickPresets: List<Float>,
    onSpeedSelected: (Float) -> Unit,
    onSpeedSelectionFinished: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var sliderProgress by remember { mutableStateOf(speedToSlider(currentSpeed)) }
    var lastAppliedSpeed by remember { mutableFloatStateOf(currentSpeed) }
    val roundedSpeed = roundedPlaybackSpeed(sliderProgress)

    LaunchedEffect(currentSpeed) {
        if (abs(lastAppliedSpeed - currentSpeed) >= 0.001f) {
            lastAppliedSpeed = currentSpeed
            sliderProgress = speedToSlider(currentSpeed)
        }
    }

    fun selectSpeed(speed: Float) {
        val selectedSpeed = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_SLIDER_PLAYBACK_SPEED)
        sliderProgress = speedToSlider(selectedSpeed)
        if (abs(lastAppliedSpeed - selectedSpeed) >= 0.001f) {
            lastAppliedSpeed = selectedSpeed
            onSpeedSelected(selectedSpeed)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (roundedSpeed == NORMAL_PLAYBACK_SPEED) {
                stringResource(R.string.normal)
            } else {
                stringResource(R.string.playback_speed_multiplier_precise, roundedSpeed)
            },
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    val speed = (roundedSpeed - PLAYBACK_SPEED_STEP)
                        .coerceAtLeast(MIN_PLAYBACK_SPEED)
                    selectSpeed(speed)
                    onSpeedSelectionFinished(speed)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.speed_slider_decrease)
                )
            }
            Slider(
                value = sliderProgress,
                onValueChange = { progress ->
                    sliderProgress = progress
                    val speed = roundedPlaybackSpeed(progress)
                    if (abs(lastAppliedSpeed - speed) >= 0.001f) {
                        lastAppliedSpeed = speed
                        onSpeedSelected(speed)
                    }
                },
                onValueChangeFinished = {
                    onSpeedSelectionFinished(lastAppliedSpeed)
                },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val speed = (roundedSpeed + PLAYBACK_SPEED_STEP)
                        .coerceAtMost(MAX_SLIDER_PLAYBACK_SPEED)
                    selectSpeed(speed)
                    onSpeedSelectionFinished(speed)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.speed_slider_increase)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(
                    R.string.playback_speed_multiplier,
                    MIN_PLAYBACK_SPEED.toString()
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.playback_speed_multiplier,
                    MAX_SLIDER_PLAYBACK_SPEED.toString()
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            quickPresets.forEach { preset ->
                FilterChip(
                    selected = abs(roundedSpeed - preset) < 0.01f,
                    onClick = {
                        selectSpeed(preset)
                        onSpeedSelectionFinished(preset)
                    },
                    label = {
                        Text(
                            text = if (preset == NORMAL_PLAYBACK_SPEED) {
                                stringResource(R.string.normal)
                            } else {
                                stringResource(R.string.playback_speed_multiplier, preset.toString())
                            }
                        )
                    }
                )
            }
        }
    }
}
