package io.github.aedev.flow.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamSegment

@OptIn(ExperimentalMaterial3Api::class)
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
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val reduceMotion = rememberFlowReduceMotion()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestOnSheetProgressChange by rememberUpdatedState(onSheetProgressChange)
    val sheetExpandedHeight = expandedHeight ?: (configuration.screenHeightDp.dp * 0.75f)
    val expandedHeightPx = with(density) { sheetExpandedHeight.toPx() }
    val collapsedHeightPx = with(density) { collapsedHeight.toPx() }.coerceIn(0f, expandedHeightPx)
    val sheetProgressRangePx = (expandedHeightPx - collapsedHeightPx).coerceAtLeast(1f)
    val dismissThresholdPx = collapsedHeightPx + sheetProgressRangePx * 0.55f
    val sheetHeightPx = remember { Animatable(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }

    LaunchedEffect(sheetHeightPx, collapsedHeightPx, sheetProgressRangePx) {
        snapshotFlow {
            ((sheetHeightPx.value - collapsedHeightPx) / sheetProgressRangePx).coerceIn(0f, 1f)
        }.distinctUntilChanged().collect { progress ->
            latestOnSheetProgressChange(progress)
        }
    }

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

    fun animateToExpanded() {
        if (!enableVerticalDismiss || reduceMotion) {
            coroutineScope.launch { sheetHeightPx.snapTo(expandedHeightPx) }
            return
        }
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = expandedHeightPx,
                animationSpec =
                    tween(
                        durationMillis = FlowMotion.CONTENT_DURATION_MILLIS,
                        easing = FlowMotion.EnterEasing,
                    ),
            )
        }
    }

    fun animateToDismiss() {
        if (isAnimatingOut) return
        if (!enableVerticalDismiss) {
            latestOnDismiss()
            return
        }
        isAnimatingOut = true
        coroutineScope.launch {
            if (reduceMotion) {
                sheetHeightPx.snapTo(collapsedHeightPx)
            } else {
                sheetHeightPx.animateTo(
                    targetValue = collapsedHeightPx,
                    animationSpec =
                        tween(
                            durationMillis = FlowMotion.EXIT_DURATION_MILLIS,
                            easing = FlowMotion.ExitEasing,
                        ),
                )
            }
            latestOnDismiss()
        }
    }

    LaunchedEffect(expandedHeightPx, collapsedHeightPx, enableVerticalDismiss, reduceMotion) {
        isAnimatingOut = false
        sheetHeightPx.updateBounds(lowerBound = collapsedHeightPx, upperBound = expandedHeightPx)
        if (!enableVerticalDismiss || reduceMotion) {
            sheetHeightPx.snapTo(expandedHeightPx)
            return@LaunchedEffect
        }
        if (sheetHeightPx.value == 0f || sheetHeightPx.value < collapsedHeightPx) {
            sheetHeightPx.snapTo(collapsedHeightPx)
        }
        sheetHeightPx.animateTo(
            targetValue = expandedHeightPx,
            animationSpec =
                tween(
                    durationMillis = FlowMotion.ENTER_DURATION_MILLIS,
                    easing = FlowMotion.EnterEasing,
                ),
        )
    }

    LaunchedEffect(chapters, initialActiveChapterIndex) {
        if (chapters.isNotEmpty()) {
            chaptersListState.scrollToItem(initialActiveChapterIndex)
        }
    }

    BackHandler(onBack = ::animateToDismiss)

    val headerDragModifier =
        if (enableVerticalDismiss) {
            Modifier.pointerInput(expandedHeightPx, collapsedHeightPx, dismissThresholdPx, isAnimatingOut) {
                val velocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (isAnimatingOut) return@detectVerticalDragGestures
                        velocityTracker.addPointerInputChange(change)
                        coroutineScope.launch {
                            val nextValue = (sheetHeightPx.value - dragAmount).coerceIn(collapsedHeightPx, expandedHeightPx)
                            sheetHeightPx.snapTo(nextValue)
                        }
                    },
                    onDragCancel = {
                        velocityTracker.resetTracking()
                        if (!isAnimatingOut) animateToExpanded()
                    },
                    onDragEnd = {
                        val velocityY = velocityTracker.calculateVelocity().y
                        velocityTracker.resetTracking()
                        when {
                            velocityY > 1200f || sheetHeightPx.value < dismissThresholdPx -> animateToDismiss()
                            else -> animateToExpanded()
                        }
                    },
                )
            }
        } else {
            Modifier
        }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val height =
                            sheetHeightPx.value
                                .toInt()
                                .coerceIn(constraints.minHeight, constraints.maxHeight)
                        val placeable =
                            measurable.measure(
                                constraints.copy(
                                    minHeight = height,
                                    maxHeight = height,
                                ),
                            )
                        layout(placeable.width, height) {
                            placeable.placeRelative(0, 0)
                        }
                    },
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .then(headerDragModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomSheetDefaults.DragHandle()
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(headerDragModifier)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.in_this_video),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.chapters),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = ::animateToDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                        key = { _, chapter ->
                            "${chapter.startTimeSeconds}_${chapter.title}"
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
    }
}

@Composable
fun ChapterItem(
    chapter: StreamSegment,
    isCurrent: Boolean,
    progress: Float,
    durationLabel: String?,
    thumbnailUrl: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            if (isCurrent) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        contentColor =
            if (isCurrent) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val thumbnailWidth = (maxWidth * 0.42f).coerceIn(72.dp, 146.dp)
            val thumbnailHeight = thumbnailWidth * (82f / 146f)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChapterThumbnail(
                    thumbnailUrl = thumbnailUrl,
                    isCurrent = isCurrent,
                    progress = progress,
                    width = thumbnailWidth,
                    height = thumbnailHeight,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                ) {
                    if (isCurrent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatChapterTime(chapter.startTimeSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    } else {
                        Text(
                            text = formatChapterTime(chapter.startTimeSeconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (durationLabel != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = durationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterThumbnail(
    thumbnailUrl: String,
    isCurrent: Boolean,
    progress: Float,
    width: Dp,
    height: Dp,
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .height(height)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(
                            alpha = if (isCurrent) 0.16f else 0.26f,
                        ),
                    ),
        )

        if (isCurrent) {
            LinearProgressIndicator(
                progress = { progress },
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

private fun formatChapterTime(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
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
