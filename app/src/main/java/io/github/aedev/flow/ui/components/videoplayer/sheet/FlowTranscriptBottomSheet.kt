package io.github.aedev.flow.ui.components.videoplayer.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.aedev.flow.ui.components.shared.FlowSearchField
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.MediaArtworkTint
import io.github.aedev.flow.ui.components.shared.defaultSheetExpandedHeight
import io.github.aedev.flow.ui.components.shared.rememberFlowBottomSheetState
import io.github.aedev.flow.ui.components.shared.rememberMediaArtworkTint
import io.github.aedev.flow.utils.formatDurationMillis

private val TimestampWidth = 52.dp
private val RowVerticalPadding = 10.dp
private val ListHorizontalPadding = 16.dp
private val EmptyStateHeight = 160.dp
private val TimestampShape = RoundedCornerShape(50)

/** How far up the list the line being spoken is parked when the transcript follows playback. */
private const val FOLLOW_OFFSET_ITEMS = 2

/**
 * The video's captions as a readable, seekable list that follows playback.
 *
 * The position arrives as a lambda and is read inside `derivedStateOf`, so a clock that ticks every
 * second only recomposes the rows when the line being spoken actually changes.
 */
@Composable
fun FlowTranscriptBottomSheet(
    cues: List<TranscriptCue>,
    isLoading: Boolean,
    currentPositionMs: () -> Long,
    artworkUrl: String?,
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
    val tint = rememberMediaArtworkTint(artworkUrl)
    var query by remember { mutableStateOf("") }

    val visibleCues =
        remember(cues, query) {
            if (query.isBlank()) cues else cues.filter { it.text.contains(query, ignoreCase = true) }
        }

    val activeStartMs by remember(cues) {
        derivedStateOf {
            val position = currentPositionMs()
            cues.lastOrNull { it.startMs <= position }?.startMs
        }
    }

    // The list follows playback until the reader takes over: a search, or a scroll of their own,
    // means they are looking for something else and must not be dragged away from it.
    val following by remember {
        derivedStateOf { query.isBlank() && !listState.isScrollInProgress }
    }

    LaunchedEffect(activeStartMs, following, visibleCues) {
        if (!following) return@LaunchedEffect
        val index = visibleCues.indexOfFirst { it.startMs == activeStartMs }
        if (index >= 0) {
            listState.animateScrollToItem((index - FOLLOW_OFFSET_ITEMS).coerceAtLeast(0))
        }
    }

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
            Column(modifier = dragModifier) {
                FlowSheetHeader(
                    title = stringResource(R.string.transcript),
                    onClose = { sheetState.dismiss() },
                    dividerAlpha = null,
                )
                if (cues.isNotEmpty()) {
                    FlowSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.transcript_search_hint),
                        onClear = { query = "" },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ListHorizontalPadding)
                                .padding(bottom = 8.dp),
                    )
                }
            }
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

            visibleCues.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(EmptyStateHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            stringResource(
                                if (cues.isEmpty()) R.string.transcript_unavailable else R.string.transcript_no_matches,
                            ),
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
                    items(items = visibleCues, key = { it.startMs }) { cue ->
                        TranscriptRow(
                            cue = cue,
                            isActive = cue.startMs == activeStartMs,
                            tint = tint,
                            onClick = { onSeekMs(cue.startMs) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptRow(
    cue: TranscriptCue,
    isActive: Boolean,
    tint: MediaArtworkTint,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = ListHorizontalPadding, vertical = RowVerticalPadding),
    ) {
        Box(
            modifier =
                Modifier
                    .width(TimestampWidth)
                    .clip(TimestampShape)
                    .background(if (isActive) tint.accent else tint.container)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatDurationMillis(cue.startMs),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (isActive) tint.container else tint.onContainer,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = cue.text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
