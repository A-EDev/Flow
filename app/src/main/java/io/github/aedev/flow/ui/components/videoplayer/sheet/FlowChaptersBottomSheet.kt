package io.github.aedev.flow.ui.components.videoplayer.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
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
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val sheetExpandedHeight = expandedHeight ?: (configuration.screenHeightDp.dp * 0.75f)
    val expandedHeightPx = with(density) { sheetExpandedHeight.toPx() }
    val collapsedHeightPx = with(density) { collapsedHeight.toPx() }.coerceIn(0f, expandedHeightPx)
    val sheetProgressRangePx = (expandedHeightPx - collapsedHeightPx).coerceAtLeast(1f)
    val dismissThresholdPx = collapsedHeightPx + sheetProgressRangePx * 0.55f
    val sheetHeightPx = remember { Animatable(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }
    val sheetProgress =
        if (expandedHeightPx > 0f) {
            ((sheetHeightPx.value - collapsedHeightPx) / sheetProgressRangePx).coerceIn(0f, 1f)
        } else {
            0f
        }
    SideEffect {
        onSheetProgressChange(sheetProgress)
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
        if (!enableVerticalDismiss) {
            coroutineScope.launch { sheetHeightPx.snapTo(expandedHeightPx) }
            return
        }
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = expandedHeightPx,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow,
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
            sheetHeightPx.animateTo(
                targetValue = collapsedHeightPx,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
            )
            latestOnDismiss()
        }
    }

    LaunchedEffect(expandedHeightPx, collapsedHeightPx) {
        isAnimatingOut = false
        sheetHeightPx.updateBounds(lowerBound = collapsedHeightPx, upperBound = expandedHeightPx)
        if (!enableVerticalDismiss) {
            sheetHeightPx.snapTo(expandedHeightPx)
            return@LaunchedEffect
        }
        if (sheetHeightPx.value == 0f || sheetHeightPx.value < collapsedHeightPx) {
            sheetHeightPx.snapTo(collapsedHeightPx)
        }
        sheetHeightPx.animateTo(
            targetValue = expandedHeightPx,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow,
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
                    .height(with(density) { sheetHeightPx.value.toDp() }),
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
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.chapters),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = ::animateToDismiss,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))

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
