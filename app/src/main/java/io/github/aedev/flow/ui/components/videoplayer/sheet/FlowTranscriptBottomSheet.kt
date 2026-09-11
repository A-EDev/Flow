package io.github.aedev.flow.ui.components.videoplayer.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.transcript.TranscriptCue
import io.github.aedev.flow.ui.components.shared.FlowBottomSheet
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.defaultSheetExpandedHeight
import io.github.aedev.flow.ui.components.shared.rememberFlowBottomSheetState
import io.github.aedev.flow.utils.formatDurationMillis

private val TimestampWidth = 56.dp
private val RowVerticalPadding = 10.dp
private val ListHorizontalPadding = 16.dp
private val EmptyStateHeight = 160.dp

/**
 * The video's captions as a readable, seekable list.
 *
 * Built from the caption track the player already resolved, so a video whose transcript is never
 * opened costs nothing and an open one costs a few KB of text.
 */
@Composable
fun FlowTranscriptBottomSheet(
    cues: List<TranscriptCue>,
    isLoading: Boolean,
    onSeekMs: (Long) -> Unit,
    onDismiss: () -> Unit,
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
    enableVerticalDismiss: Boolean = true,
    onSheetProgressChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberFlowBottomSheetState()
    val listState = rememberLazyListState()

    FlowBottomSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        state = sheetState,
        expandedHeight = expandedHeight ?: defaultSheetExpandedHeight(),
        collapsedHeight = collapsedHeight,
        dismissible = enableVerticalDismiss,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        onProgressChange = onSheetProgressChange,
        header = { dragModifier ->
            FlowSheetHeader(
                title = stringResource(R.string.transcript),
                onClose = { sheetState.dismiss() },
                modifier = dragModifier,
            )
        },
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(EmptyStateHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    FlowLoadingIndicator()
                }
            }

            cues.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(EmptyStateHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.transcript_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = ListHorizontalPadding),
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().weight(1f),
                ) {
                    items(items = cues, key = { it.startMs }) { cue ->
                        TranscriptRow(cue = cue, onClick = { onSeekMs(cue.startMs) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptRow(
    cue: TranscriptCue,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = ListHorizontalPadding, vertical = RowVerticalPadding),
    ) {
        Text(
            text = formatDurationMillis(cue.startMs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(TimestampWidth),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = cue.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
