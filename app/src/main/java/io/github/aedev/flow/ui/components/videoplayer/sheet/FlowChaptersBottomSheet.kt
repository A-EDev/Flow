package io.github.aedev.flow.ui.components.videoplayer.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowBottomSheet
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.defaultSheetExpandedHeight
import io.github.aedev.flow.ui.components.shared.rememberFlowBottomSheetState
import org.schabi.newpipe.extractor.stream.StreamSegment

@Composable
fun FlowChaptersBottomSheet(
    chapters: List<StreamSegment>,
    currentPosition: Long,
    durationMs: Long = 0L,
    onChapterClick: (Long) -> Unit,
    onDismiss: () -> Unit,
    thumbnailUrl: String = "",
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
    enableVerticalDismiss: Boolean = true,
    onSheetProgressChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberFlowBottomSheetState()
    val initialActiveChapterIndex =
        remember(chapters) {
            chapters
                .indexOfLast { currentPosition >= it.startTimeSeconds.toLong() * 1000L }
                .coerceAtLeast(0)
        }
    val chaptersListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialActiveChapterIndex,
        )

    LaunchedEffect(chapters, initialActiveChapterIndex) {
        if (chapters.isNotEmpty()) {
            chaptersListState.scrollToItem(initialActiveChapterIndex)
        }
    }

    FlowBottomSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        state = sheetState,
        expandedHeight = expandedHeight ?: defaultSheetExpandedHeight(),
        collapsedHeight = collapsedHeight,
        dismissible = enableVerticalDismiss,
        dismissOnOutsideTap = false,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        onProgressChange = onSheetProgressChange,
        header = { dragModifier ->
            FlowSheetHeader(
                title = stringResource(R.string.in_this_video),
                onClose = { sheetState.dismiss() },
                modifier = dragModifier,
                subtitle = stringResource(R.string.chapters),
                titleStyle =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    ),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 10.dp),
                dividerAlpha = 0.18f,
            )
        },
    ) {
        LazyColumn(
            state = chaptersListState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(
                chapters,
                key = { index, chapter ->
                    "${chapter.title}_${chapter.startTimeSeconds}_$index"
                },
            ) { index, chapter ->
                val startTimeMs = chapter.startTimeSeconds.toLong() * 1000L
                val nextChapter = chapters.getOrNull(index + 1)
                val endTimeMs =
                    nextChapter?.startTimeSeconds?.let { it.toLong() * 1000L }
                        ?: durationMs.takeIf { it > startTimeMs }
                val isCurrent = currentPosition >= startTimeMs && (endTimeMs == null || currentPosition < endTimeMs)
                val progress =
                    if (isCurrent && endTimeMs != null && endTimeMs > startTimeMs) {
                        ((currentPosition - startTimeMs).toFloat() / (endTimeMs - startTimeMs).toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                val durationLabel =
                    endTimeMs
                        ?.takeIf { it > startTimeMs }
                        ?.let { formatChapterDuration((it - startTimeMs) / 1000L) }

                ChapterItem(
                    chapter = chapter,
                    isCurrent = isCurrent,
                    progress = progress,
                    durationLabel = durationLabel,
                    thumbnailUrl = chapter.previewUrl?.takeIf { it.isNotBlank() } ?: thumbnailUrl,
                    onClick = {
                        onChapterClick(startTimeMs)
                    },
                )
            }
        }
    }
}

private fun formatChapterDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours ${pluralize("hour", hours)} $minutes ${pluralize("minute", minutes)}"
        hours > 0 -> "$hours ${pluralize("hour", hours)}"
        minutes > 0 -> "$minutes ${pluralize("minute", minutes)}"
        else -> "$seconds ${pluralize("second", seconds)}"
    }
}

private fun pluralize(
    unit: String,
    value: Long,
): String = if (value == 1L) unit else "${unit}s"
