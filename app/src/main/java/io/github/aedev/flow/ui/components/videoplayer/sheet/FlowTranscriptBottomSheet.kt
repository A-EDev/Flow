package io.github.aedev.flow.ui.components.videoplayer.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.transcript.TranscriptCue
import io.github.aedev.flow.player.state.SubtitleOption
import io.github.aedev.flow.ui.components.shared.FlowBottomSheet
import io.github.aedev.flow.ui.components.shared.FlowDropdownFilterChip
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowSearchField
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.MediaArtworkTint
import io.github.aedev.flow.ui.components.shared.defaultSheetExpandedHeight
import io.github.aedev.flow.ui.components.shared.rememberFlowBottomSheetState
import io.github.aedev.flow.ui.components.shared.rememberMediaArtworkTint
import io.github.aedev.flow.utils.formatDurationMillis

private val RowVerticalPadding = 12.dp
private val ListHorizontalPadding = 16.dp
private val EmptyStateHeight = 160.dp
private val HeadingTopPadding = 20.dp
private val HeadingBottomPadding = 4.dp
private val TimestampShape = RoundedCornerShape(50)
private val TimestampHorizontalPadding = 8.dp
private val TimestampVerticalPadding = 3.dp

/** Space between a timestamp and the line it stamps, held open by the timestamp's own slot. */
private val TimestampGap = 10.dp

/** Room under the last line for the sync pill that floats over the bottom of the list. */
private val ListBottomPadding = 64.dp
private val SyncPillPadding = 12.dp

/** How far up the list the line being spoken is parked when the transcript follows playback. */
private const val FOLLOW_OFFSET_ITEMS = 2

/** Id of the inline slot each line opens for its timestamp. */
private const val TIMESTAMP_SLOT = "timestamp"

/**
 * The video's captions as a readable, seekable list that follows playback.
 *
 * The position arrives as a lambda and is read inside `derivedStateOf`, so a clock that ticks every
 * second only recomposes the rows when the line being spoken actually changes.
 *
 * Scrolling hands the list to the reader for good: playback stops dragging it around and the sync
 * pill offers the way back, rather than the line being spoken yanking them off whatever they were
 * reading a moment after they let go.
 */
@Composable
fun FlowTranscriptBottomSheet(
    cues: List<TranscriptCue>,
    isLoading: Boolean,
    currentPositionMs: () -> Long,
    artworkUrl: String?,
    onSeekMs: (Long) -> Unit,
    onDismiss: () -> Unit,
    chapters: List<TranscriptChapter> = emptyList(),
    tracks: List<SubtitleOption> = emptyList(),
    selectedTrackUrl: String? = null,
    onTrackSelected: (String) -> Unit = {},
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

    val items =
        remember(cues, chapters, query) {
            val matches =
                if (query.isBlank()) cues else cues.filter { it.text.contains(query, ignoreCase = true) }
            transcriptItems(matches, chapters)
        }

    val activeStartMs by remember(cues) {
        derivedStateOf {
            val position = currentPositionMs()
            cues.lastOrNull { it.startMs <= position }?.startMs
        }
    }

    // One instance for the lifetime of the sheet, so the scroll connection below cannot be left
    // writing to a state object that a track change has already replaced.
    val following = remember { mutableStateOf(true) }
    LaunchedEffect(cues) { following.value = true }

    // Any scroll the reader starts — a drag, the fling off one, a wheel — hands them the list.
    // Nested scroll is what tells theirs from ours: the follow's own animateScrollToItem moves the
    // list without dispatching here at all, so it cannot cancel the very thing that started it.
    val readerScroll =
        remember(following) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput && available.y != 0f) {
                        following.value = false
                    }
                    return Offset.Zero
                }
            }
        }

    val isFollowing = following.value && query.isBlank()

    LaunchedEffect(activeStartMs, isFollowing, items) {
        if (!isFollowing) return@LaunchedEffect
        val index = items.indexOfFirst { it is TranscriptItem.Line && it.cue.startMs == activeStartMs }
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
        // As on every other player sheet: a tap on the video reaches the player instead of closing
        // what is being read over it.
        dismissOnOutsideTap = false,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        onProgressChange = onSheetProgressChange,
        header = { dragModifier ->
            Column(modifier = dragModifier) {
                FlowSheetHeader(
                    title = stringResource(R.string.transcript),
                    onClose = { sheetState.dismiss() },
                )
                if (cues.isNotEmpty() || tracks.size > 1) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ListHorizontalPadding)
                                .padding(top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (cues.isNotEmpty()) {
                            FlowSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = stringResource(R.string.transcript_search_hint),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (tracks.size > 1) {
                            TranscriptLanguageMenu(
                                tracks = tracks,
                                selectedTrackUrl = selectedTrackUrl,
                                onTrackSelected = onTrackSelected,
                            )
                        }
                    }
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

            items.isEmpty() -> {
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
                TranscriptList(
                    items = items,
                    listState = listState,
                    activeStartMs = activeStartMs,
                    // A search is its own way of leaving the current line, and its own way back, so
                    // the pill stays out of it.
                    showSyncPill = !following.value && query.isBlank() && activeStartMs != null,
                    tint = tint,
                    onSeekMs = onSeekMs,
                    onSync = { following.value = true },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .nestedScroll(readerScroll),
                )
            }
        }
    }
}

/**
 * The lines themselves, with the sync pill floating over their bottom edge.
 *
 * A [SelectionContainer] wraps the list so a transcript can be read out of the app: long-pressing
 * a line opens the platform's own selection handles and copy action, and a tap still seeks.
 */
@Composable
private fun TranscriptList(
    items: List<TranscriptItem>,
    listState: LazyListState,
    activeStartMs: Long?,
    showSyncPill: Boolean,
    tint: MediaArtworkTint,
    onSeekMs: (Long) -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timestampStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
    val slot = rememberTimestampSlot(items = items, style = timestampStyle)

    Box(modifier = modifier) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = ListBottomPadding),
            ) {
                items(items = items, key = { it.key }) { item ->
                    when (item) {
                        is TranscriptItem.Heading -> {
                            TranscriptHeading(title = item.chapter.title)
                        }

                        is TranscriptItem.Line -> {
                            TranscriptLine(
                                cue = item.cue,
                                isActive = item.cue.startMs == activeStartMs,
                                tint = tint,
                                slot = slot,
                                timestampStyle = timestampStyle,
                                onClick = { onSeekMs(item.cue.startMs) },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSyncPill,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = SyncPillPadding),
        ) {
            SyncToVideoPill(tint = tint, onClick = onSync)
        }
    }
}

/** How much room a line opens for its timestamp, and how tall that slot is. */
private data class TimestampSlot(
    val width: TextUnit,
    val height: TextUnit,
)

/**
 * Sizes the inline slot from the longest timestamp in the transcript — the last line's, since they
 * only grow — so the whole list is measured once and every line starts at the same place.
 */
@Composable
private fun rememberTimestampSlot(
    items: List<TranscriptItem>,
    style: TextStyle,
): TimestampSlot {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(items, style, density) {
        val longest =
            items
                .filterIsInstance<TranscriptItem.Line>()
                .lastOrNull()
                ?.cue
                ?.startMs
                ?: 0L
        val measured = measurer.measure(formatDurationMillis(longest), style).size
        with(density) {
            TimestampSlot(
                width = (measured.width + (TimestampHorizontalPadding * 2 + TimestampGap).toPx()).toSp(),
                height = (measured.height + TimestampVerticalPadding.toPx() * 2).toSp(),
            )
        }
    }
}

/**
 * One spoken line, stamped with the time it was said.
 *
 * The timestamp takes an inline slot rather than a column of its own, so a line that wraps uses the
 * full width of the sheet instead of leaving a stripe of empty space down the left.
 */
@Composable
private fun TranscriptLine(
    cue: TranscriptCue,
    isActive: Boolean,
    tint: MediaArtworkTint,
    slot: TimestampSlot,
    timestampStyle: TextStyle,
    onClick: () -> Unit,
) {
    val label = remember(cue.startMs) { formatDurationMillis(cue.startMs) }
    val text =
        remember(label, cue.text) {
            buildAnnotatedString {
                // The stamp doubles as the slot's alternate text, so a line copied out of the
                // transcript — or read aloud — still says when it was said.
                appendInlineContent(TIMESTAMP_SLOT, "$label ")
                append(cue.text)
            }
        }
    val inlineContent =
        remember(slot, isActive, tint, timestampStyle) {
            mapOf(
                TIMESTAMP_SLOT to
                    InlineTextContent(
                        Placeholder(
                            width = slot.width,
                            height = slot.height,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) { stamp ->
                        TranscriptTimestamp(
                            label = stamp.trim(),
                            isActive = isActive,
                            tint = tint,
                            style = timestampStyle,
                        )
                    },
            )
        }

    Text(
        text = text,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = ListHorizontalPadding, vertical = RowVerticalPadding),
    )
}

/**
 * The timestamp badge inside a line's inline slot. The slot is as wide as the longest stamp plus
 * the gap, so the badge is aligned to its start and keeps its own width.
 */
@Composable
private fun TranscriptTimestamp(
    label: String,
    isActive: Boolean,
    tint: MediaArtworkTint,
    style: TextStyle,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(TimestampShape)
                    .background(if (isActive) tint.accent else tint.container)
                    .padding(
                        horizontal = TimestampHorizontalPadding,
                        vertical = TimestampVerticalPadding,
                    ),
        ) {
            Text(
                text = label,
                style = style,
                color = if (isActive) tint.container else tint.onContainer,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TranscriptHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = ListHorizontalPadding,
                    end = ListHorizontalPadding,
                    top = HeadingTopPadding,
                    bottom = HeadingBottomPadding,
                ),
    )
}

@Composable
private fun SyncToVideoPill(
    tint: MediaArtworkTint,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = tint.accent,
                contentColor = tint.container,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Sync,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.transcript_resync),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Picks which caption track the transcript is read from, without touching the video's subtitles. */
@Composable
private fun TranscriptLanguageMenu(
    tracks: List<SubtitleOption>,
    selectedTrackUrl: String?,
    onTrackSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val automaticLabel = stringResource(R.string.quality_auto)
    val translatedLabel = stringResource(R.string.subtitle_translated)
    val selected = tracks.firstOrNull { it.url == selectedTrackUrl }

    Box {
        FlowDropdownFilterChip(
            label = selected?.label ?: stringResource(R.string.transcript_language),
            selected = selected != null,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text =
                                when {
                                    track.isTranslated -> {
                                        stringResource(
                                            R.string.subtitle_auto_generated_template,
                                            track.label,
                                            translatedLabel,
                                        )
                                    }

                                    track.isAutoGenerated -> {
                                        stringResource(
                                            R.string.subtitle_auto_generated_template,
                                            track.label,
                                            automaticLabel,
                                        )
                                    }

                                    else -> {
                                        track.label
                                    }
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onTrackSelected(track.url)
                    },
                    leadingIcon =
                        if (track.url == selectedTrackUrl) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
}
