package io.github.aedev.flow.ui.components.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import kotlin.math.roundToInt

@Composable
internal fun SleepTimerSheetContent(
    isActive: Boolean,
    pauseAtEndOfMedia: Boolean,
    remainingMs: Long,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    customInput: String,
    onCustomInputChange: (String) -> Unit,
    inputError: Boolean,
    onEndOfMedia: () -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    closeAppOnExpiry: Boolean,
    onCloseAppToggle: (Boolean) -> Unit,
    onReset: () -> Unit,
    onCancelTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnimatedContent(targetState = isActive, label = "sleepTimerContent") { active ->
            if (active) {
                ActiveTimerContent(
                    pauseAtEndOfMedia = pauseAtEndOfMedia,
                    remainingMs = remainingMs,
                    closeAppOnExpiry = closeAppOnExpiry,
                    onReset = onReset,
                    onCancel = onCancelTimer,
                )
            } else {
                InactiveTimerContent(
                    sliderValue = sliderValue,
                    onSliderChange = onSliderChange,
                    customInput = customInput,
                    onCustomInputChange = onCustomInputChange,
                    inputError = inputError,
                    onEndOfMedia = onEndOfMedia,
                    onCancel = onCancel,
                    onStart = onStart,
                    closeAppOnExpiry = closeAppOnExpiry,
                    onCloseAppToggle = onCloseAppToggle,
                )
            }
        }
    }
}

@Composable
private fun ActiveTimerContent(
    pauseAtEndOfMedia: Boolean,
    remainingMs: Long,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    closeAppOnExpiry: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = stringResource(R.string.sleep_timer_stops_in),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text =
                        if (pauseAtEndOfMedia) {
                            stringResource(R.string.sleep_timer_end_of_media)
                        } else {
                            formatCountdown(remainingMs)
                        },
                    style =
                        if (pauseAtEndOfMedia) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.displayLarge
                        },
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (closeAppOnExpiry) {
            Text(
                text = stringResource(R.string.sleep_timer_will_close_app),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = onReset,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp),
                enabled = !pauseAtEndOfMedia,
            ) {
                Text(stringResource(R.string.sleep_timer_reset))
            }
            Button(
                onClick = onCancel,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp),
            ) {
                Text(stringResource(R.string.sleep_timer_cancel_timer))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InactiveTimerContent(
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    customInput: String,
    onCustomInputChange: (String) -> Unit,
    inputError: Boolean,
    onEndOfMedia: () -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    closeAppOnExpiry: Boolean,
    onCloseAppToggle: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = sliderValue.roundToInt().toString(),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.sleep_timer_unit),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                }

                Slider(
                    value = sliderValue,
                    onValueChange = onSliderChange,
                    valueRange = 5f..120f,
                    steps = 22,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_min_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.sleep_timer_max_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val haptics = LocalHapticFeedback.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            SleepTimerPresets.forEachIndexed { index, minutes ->
                ToggleButton(
                    checked = sliderValue.roundToInt() == minutes,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSliderChange(minutes.toFloat())
                    },
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            SleepTimerPresets.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    contentPadding = PresetContentPadding,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = minutes.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }

        OutlinedTextField(
            value = customInput,
            onValueChange = onCustomInputChange,
            label = { Text(stringResource(R.string.sleep_timer_custom_label)) },
            supportingText =
                if (inputError) {
                    { Text(stringResource(R.string.sleep_timer_input_error)) }
                } else {
                    null
                },
            isError = inputError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                Text(
                    text = stringResource(R.string.sleep_timer_unit),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        FilledTonalButton(
            onClick = onEndOfMedia,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sleep_timer_end_of_song))
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sleep_timer_close_app),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.sleep_timer_close_app_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = closeAppOnExpiry,
                    onCheckedChange = onCloseAppToggle,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            FilledTonalButton(
                onClick = onCancel,
                shapes =
                    ButtonDefaults.shapes(
                        shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                        pressedShape = ButtonGroupDefaults.connectedLeadingButtonPressShape,
                    ),
                modifier =
                    Modifier
                        .weight(1f)
                        .height(ActionButtonHeight),
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onStart()
                },
                shapes =
                    ButtonDefaults.shapes(
                        shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                        pressedShape = ButtonGroupDefaults.connectedTrailingButtonPressShape,
                    ),
                modifier =
                    Modifier
                        .weight(1f)
                        .height(ActionButtonHeight),
            ) {
                Text(stringResource(R.string.sleep_timer_start))
            }
        }
    }
}

private fun formatCountdown(ms: Long): String {
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private val SleepTimerPresets = listOf(10, 15, 30, 45, 60, 90)
private val PresetContentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
private val ActionButtonHeight = 52.dp
