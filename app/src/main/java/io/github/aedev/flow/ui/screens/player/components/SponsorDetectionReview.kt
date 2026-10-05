package io.github.aedev.flow.ui.screens.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.R
import io.github.aedev.flow.data.sponsordetection.SponsorDetectionStatus
import io.github.aedev.flow.data.sponsordetection.SponsorDetectionUiState
import io.github.aedev.flow.data.sponsordetection.SponsorFeedbackVerdict
import io.github.aedev.flow.data.sponsordetection.SponsorPredictedSpan
import io.github.aedev.flow.data.sponsordetection.SponsorSpan
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.utils.sponsorCategoryLabelRes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SponsorDetectionReviewControl(
    videoId: String,
    durationMs: Long,
    snackbarHostState: SnackbarHostState,
    manager: EnhancedPlayerManager = EnhancedPlayerManager.getInstance(),
) {
    val state by manager.sponsorDetectionState.collectAsStateWithLifecycle()
    SponsorDetectionReviewUi(
        videoId = videoId,
        durationMs = durationMs,
        state = state,
        snackbarHostState = snackbarHostState,
        onSeekTo = manager::seekTo,
        currentPositionMs = { manager.getCurrentPosition() },
        onRecordFeedback = { verdict, target, corrected ->
            manager.recordSponsorFeedback(verdict, target, corrected)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SponsorDetectionReviewUi(
    videoId: String,
    durationMs: Long,
    state: SponsorDetectionUiState,
    snackbarHostState: SnackbarHostState,
    onSeekTo: (Long) -> Unit,
    currentPositionMs: () -> Long,
    onRecordFeedback: suspend (
        SponsorFeedbackVerdict,
        SponsorPredictedSpan?,
        SponsorSpan?,
    ) -> Boolean,
) {
    if (
        state.videoId != videoId ||
        state.status != SponsorDetectionStatus.READY
    ) {
        return
    }
    var showReview by remember(videoId) { mutableStateOf(false) }
    AssistChip(
        onClick = { showReview = true },
        label = { Text(stringResource(R.string.sponsor_training_review)) },
        leadingIcon = { Icon(Icons.Outlined.RateReview, contentDescription = null) },
        colors = AssistChipDefaults.assistChipColors(),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    if (showReview) {
        SponsorDetectionReviewSheet(
            durationMs = durationMs,
            state = state,
            snackbarHostState = snackbarHostState,
            onSeekTo = onSeekTo,
            currentPositionMs = currentPositionMs,
            onRecordFeedback = onRecordFeedback,
            onDismiss = { showReview = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SponsorDetectionReviewSheet(
    durationMs: Long,
    state: SponsorDetectionUiState,
    snackbarHostState: SnackbarHostState,
    onSeekTo: (Long) -> Unit,
    currentPositionMs: () -> Long,
    onRecordFeedback: suspend (
        SponsorFeedbackVerdict,
        SponsorPredictedSpan?,
        SponsorSpan?,
    ) -> Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editingTarget by remember { mutableStateOf<SponsorPredictedSpan?>(null) }
    var addingMissed by remember { mutableStateOf(false) }
    val matchedIds =
        remember(state.comparison) {
            state.comparison
                ?.matches
                ?.map { it.predictionId }
                .orEmpty()
                .toSet()
        }

    val feedbackSaved = stringResource(R.string.sponsor_training_feedback_saved)
    val feedbackFailed = stringResource(R.string.sponsor_training_feedback_failed)

    fun saveWithMessage(
        verdict: SponsorFeedbackVerdict,
        target: SponsorPredictedSpan? = null,
        corrected: SponsorSpan? = null,
    ) {
        scope.launch {
            val saved = onRecordFeedback(verdict, target, corrected)
            snackbarHostState.showSnackbar(if (saved) feedbackSaved else feedbackFailed)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.sponsor_training_review_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (!state.reviewAvailable) {
            Text(
                text = stringResource(R.string.sponsor_training_review_read_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        if (state.predictions.isEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.sponsor_training_no_predictions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = state.reviewAvailable, onClick = { saveWithMessage(SponsorFeedbackVerdict.CONFIRMED_NO_SPONSOR) }) {
                        Text(stringResource(R.string.sponsor_training_confirm_none))
                    }
                    OutlinedButton(enabled = state.reviewAvailable, onClick = { addingMissed = true }) {
                        Text(stringResource(R.string.sponsor_training_add_missed))
                    }
                }
            }
        } else {
            LazyColumn {
                items(state.predictions, key = SponsorPredictedSpan::spanId) { prediction ->
                    val reviewed = prediction.spanId in state.reviewedSpanIds
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSeekTo(prediction.startMs) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sponsorCategoryLabelRes(prediction.category)?.let { label ->
                            Text(
                                text = stringResource(label),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Text(
                            text =
                                stringResource(
                                    R.string.sponsor_training_prediction_confidence,
                                    formatTimestamp(prediction.startMs),
                                    formatTimestamp(prediction.endMs),
                                    (prediction.confidence * 100).roundToInt(),
                                ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text =
                                stringResource(
                                    if (prediction.spanId in matchedIds) {
                                        R.string.sponsor_training_api_match
                                    } else {
                                        R.string.sponsor_training_model_only
                                    },
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                enabled = state.reviewAvailable && !reviewed,
                                onClick = { saveWithMessage(SponsorFeedbackVerdict.ACCEPTED, prediction) },
                            ) { Text(stringResource(R.string.sponsor_training_accept)) }
                            TextButton(
                                enabled = state.reviewAvailable && !reviewed,
                                onClick = { saveWithMessage(SponsorFeedbackVerdict.REJECTED, prediction) },
                            ) { Text(stringResource(R.string.sponsor_training_reject)) }
                            TextButton(enabled = state.reviewAvailable, onClick = { editingTarget = prediction }) {
                                Text(stringResource(R.string.sponsor_training_edit))
                            }
                        }
                    }
                    HorizontalDivider()
                }
                item {
                    OutlinedButton(
                        enabled = state.reviewAvailable,
                        onClick = { addingMissed = true },
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(stringResource(R.string.sponsor_training_add_missed))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    editingTarget?.let { target ->
        SponsorTimestampEditor(
            initialStartMs = target.startMs,
            initialEndMs = target.endMs,
            durationMs = durationMs,
            onDismiss = { editingTarget = null },
            onSave = { corrected ->
                saveWithMessage(SponsorFeedbackVerdict.CORRECTED, target, corrected)
                editingTarget = null
            },
        )
    }
    if (addingMissed) {
        val start = currentPositionMs().coerceAtLeast(0)
        val end = if (durationMs > start) (start + 30_000).coerceAtMost(durationMs) else start + 30_000
        SponsorTimestampEditor(
            initialStartMs = start,
            initialEndMs = end,
            durationMs = durationMs,
            onDismiss = { addingMissed = false },
            onSave = { corrected ->
                saveWithMessage(SponsorFeedbackVerdict.MISSED, corrected = corrected)
                addingMissed = false
            },
        )
    }
}

@Composable
internal fun SponsorTimestampEditor(
    initialStartMs: Long,
    initialEndMs: Long,
    durationMs: Long,
    onDismiss: () -> Unit,
    onSave: (SponsorSpan) -> Unit,
) {
    var startText by remember(initialStartMs) { mutableStateOf(formatTimestamp(initialStartMs)) }
    var endText by remember(initialEndMs) { mutableStateOf(formatTimestamp(initialEndMs)) }
    val startMs = parseTimestamp(startText)
    val endMs = parseTimestamp(endText)
    val valid =
        startMs != null &&
            endMs != null &&
            isValidSponsorTimestampRange(startMs, endMs, durationMs)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sponsor_training_edit_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text(stringResource(R.string.sb_submit_start_time)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text(stringResource(R.string.sb_submit_end_time)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(SponsorSpan(checkNotNull(startMs), checkNotNull(endMs))) },
            ) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
}

internal fun formatTimestamp(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

internal fun parseTimestamp(value: String): Long? {
    val parts = value.trim().split(':')
    if (parts.size !in 1..3) return null
    val seconds = parts.last().toLongOrNull() ?: return null
    val minutes = parts.getOrNull(parts.lastIndex - 1)?.toLongOrNull() ?: 0
    val hours = parts.getOrNull(parts.lastIndex - 2)?.toLongOrNull() ?: 0
    if (seconds !in 0..59 || minutes !in 0..59 || hours < 0) return null
    return (hours * 3600 + minutes * 60 + seconds) * 1000
}

internal fun isValidSponsorTimestampRange(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
): Boolean = startMs >= 0 && endMs > startMs && (durationMs <= 0 || endMs <= durationMs)
