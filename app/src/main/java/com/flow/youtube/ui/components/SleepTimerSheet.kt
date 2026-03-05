package com.flow.youtube.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flow.youtube.R
import com.flow.youtube.player.SleepTimerManager
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Sleep timer bottom sheet, shared between the music player and video player.
 *
 * When the timer is already active it shows the remaining time and Reset/Cancel actions.
 * When inactive it exposes:
 *  - a slider (5 – 120 min in 5-minute steps)
 *  - a free-form minute text field
 *  - an "End of song / video" option
 *  - Cancel and Start action buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isActive = SleepTimerManager.isActive
    val pauseAtEndOfMedia = SleepTimerManager.pauseAtEndOfMedia
    val triggerTimeMs = SleepTimerManager.triggerTimeMs

    var remainingMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isActive, triggerTimeMs) {
        if (isActive && triggerTimeMs > 0L) {
            while (true) {
                remainingMs = (triggerTimeMs - System.currentTimeMillis()).coerceAtLeast(0L)
                if (remainingMs == 0L) break
                delay(500L)
            }
        } else {
            remainingMs = 0L
        }
    }

    var sliderValue by remember { mutableFloatStateOf(30f) }
    var customInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── Header ─────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.sleep_timer),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            // ── Content ─────────────────────────────────────────────────────────
            AnimatedContent(targetState = isActive, label = "sleepTimerContent") { active ->
                if (active) {
                    ActiveTimerContent(
                        pauseAtEndOfMedia = pauseAtEndOfMedia,
                        remainingMs = remainingMs,
                        onReset = {
                            val minutes = sliderValue.roundToInt()
                            SleepTimerManager.start(minutes)
                        },
                        onCancel = {
                            SleepTimerManager.cancel()
                            onDismiss()
                        }
                    )
                } else {
                    // ── Inactive state ───────────────────────────────────────
                    InactiveTimerContent(
                        sliderValue = sliderValue,
                        onSliderChange = { value ->
                            sliderValue = value
                            if (customInput.isEmpty() || customInput.toIntOrNull() != null) {
                                customInput = value.roundToInt().toString()
                                inputError = false
                            }
                        },
                        customInput = customInput,
                        onCustomInputChange = { text ->
                            customInput = text
                            inputError = false
                            val parsed = text.toIntOrNull()
                            if (parsed != null && parsed in 1..1440) {
                                sliderValue = parsed.toFloat().coerceIn(5f, 120f)
                            }
                        },
                        inputError = inputError,
                        onEndOfMedia = {
                            SleepTimerManager.startEndOfMedia()
                            onDismiss()
                        },
                        onCancel = onDismiss,
                        onStart = {
                            val minutes = customInput.toIntOrNull()
                            when {
                                customInput.isNotEmpty() && (minutes == null || minutes < 1 || minutes > 1440) -> {
                                    inputError = true
                                }
                                customInput.isNotEmpty() && minutes != null -> {
                                    SleepTimerManager.start(minutes)
                                    onDismiss()
                                }
                                else -> {
                                    SleepTimerManager.start(sliderValue.roundToInt())
                                    onDismiss()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ── Sub-composables ─────────────────────────────────────────────────────────────

@Composable
private fun ActiveTimerContent(
    pauseAtEndOfMedia: Boolean,
    remainingMs: Long,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.sleep_timer_stops_in),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        if (pauseAtEndOfMedia) {
            Text(
                text = stringResource(R.string.sleep_timer_end_of_media),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = formatCountdown(remainingMs),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                enabled = !pauseAtEndOfMedia
            ) {
                Text(stringResource(R.string.sleep_timer_reset))
            }
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.sleep_timer_cancel_timer))
            }
        }
    }
}

@Composable
private fun InactiveTimerContent(
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    customInput: String,
    onCustomInputChange: (String) -> Unit,
    inputError: Boolean,
    onEndOfMedia: () -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Slider ──────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sleep_timer_duration),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.sleep_timer_minutes, sliderValue.roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = sliderValue,
                onValueChange = onSliderChange,
                valueRange = 5f..120f,
                steps = 22,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.sleep_timer_min_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.sleep_timer_max_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Custom input ─────────────────────────────────────────────────────
        OutlinedTextField(
            value = customInput,
            onValueChange = onCustomInputChange,
            label = { Text(stringResource(R.string.sleep_timer_custom_label)) },
            supportingText = if (inputError) {
                { Text(stringResource(R.string.sleep_timer_input_error)) }
            } else null,
            isError = inputError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                Text(
                    text = stringResource(R.string.sleep_timer_unit),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        // ── End of song/video option ──────────────────────────────────────────
        OutlinedButton(
            onClick = onEndOfMedia,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sleep_timer_end_of_song))
        }

        Spacer(Modifier.height(4.dp))

        // ── Action buttons ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.sleep_timer_start))
            }
        }
    }
}

// ── Utilities ────────────────────────────────────────────────────────────────

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
