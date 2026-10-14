package io.github.aedev.flow.ui.components.videoplayer.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowRowGroup
import io.github.aedev.flow.ui.components.shared.flowRowGroupShape
import java.text.DecimalFormat
import kotlin.math.abs

/**
 * Moves the captions against the picture, for a subtitle file made for another cut or frame rate:
 * later when lines arrive before they are spoken, earlier when they lag.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubtitleTimingCard(
    offsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    FlowRowGroup(modifier = modifier) {
        Surface(
            shape = flowRowGroupShape(0, 1),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.subtitle_timing), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = timingLabel(offsetMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (offsetMs != 0L) {
                        TextButton(onClick = { onOffsetChange(0L) }) { Text(stringResource(R.string.reset)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimingSteps.forEach { step ->
                        FilledTonalButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onOffsetChange((offsetMs + step).coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS))
                            },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.subtitle_timing_step_template, stepLabel(step)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun timingLabel(offsetMs: Long): String =
    when {
        offsetMs > 0L -> stringResource(R.string.subtitle_timing_later_template, seconds(offsetMs))
        offsetMs < 0L -> stringResource(R.string.subtitle_timing_earlier_template, seconds(offsetMs))
        else -> stringResource(R.string.subtitle_timing_in_sync)
    }

private fun seconds(offsetMs: Long): String = DecimalFormat("0.0#").format(abs(offsetMs) / MS_PER_SECOND)

private fun stepLabel(stepMs: Long): String = (if (stepMs < 0) "−" else "+") + DecimalFormat("0.#").format(abs(stepMs) / MS_PER_SECOND)

private val TimingSteps = listOf(-1_000L, -100L, 100L, 1_000L)
private const val MAX_OFFSET_MS = 10 * 60 * 1_000L
private const val MS_PER_SECOND = 1_000.0
