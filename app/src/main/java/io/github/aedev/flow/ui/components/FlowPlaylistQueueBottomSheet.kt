package io.github.aedev.flow.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private class QueueDisplayItem(
    val key: String,
    val video: Video,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowPlaylistQueueBottomSheet(
    queueVideos: List<Video>,
    currentQueueIndex: Int,
    playlistTitle: String?,
    isLooping: Boolean,
    isShuffled: Boolean,
    onLoopToggle: (Boolean) -> Unit,
    onShuffleToggle: (Boolean) -> Unit,
    onPlayVideoAtIndex: (Int) -> Unit,
    onRemoveVideoAtIndex: (Int) -> Unit,
    onMoveVideoAtIndex: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
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
    val listState = rememberLazyListState()

    LaunchedEffect(sheetHeightPx, collapsedHeightPx, sheetProgressRangePx) {
        snapshotFlow {
            ((sheetHeightPx.value - collapsedHeightPx) / sheetProgressRangePx).coerceIn(0f, 1f)
        }.distinctUntilChanged().collect { progress ->
            latestOnSheetProgressChange(progress)
        }
    }

    val displayItems =
        remember(queueVideos) {
            queueVideos
                .mapIndexed { index, video ->
                    QueueDisplayItem(
                        key = "$index:${video.id}",
                        video = video,
                    )
                }.toMutableStateList()
        }
    val currentDisplayItem =
        remember(queueVideos, currentQueueIndex) {
            displayItems.getOrNull(currentQueueIndex)
        }
    var pendingMoveFrom by remember(queueVideos) { mutableStateOf<Int?>(null) }
    var pendingMoveTo by remember(queueVideos) { mutableStateOf<Int?>(null) }
    val reorderState =
        rememberReorderableLazyListState(
            listState = listState,
            onMove = { fromIndex, toIndex ->
                if (pendingMoveFrom == null) {
                    pendingMoveFrom = fromIndex
                }
                pendingMoveTo = toIndex
                displayItems.add(toIndex, displayItems.removeAt(fromIndex))
            },
            onDragStopped = {
                val fromIndex = pendingMoveFrom
                val toIndex = pendingMoveTo
                pendingMoveFrom = null
                pendingMoveTo = null
                if (fromIndex != null && toIndex != null) {
                    onMoveVideoAtIndex(fromIndex, toIndex)
                }
            },
        )

    fun animateToExpanded() {
        coroutineScope.launch {
            if (reduceMotion) {
                sheetHeightPx.snapTo(expandedHeightPx)
            } else {
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
    }

    fun animateToDismiss() {
        if (isAnimatingOut) return
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

    LaunchedEffect(expandedHeightPx, collapsedHeightPx, reduceMotion) {
        isAnimatingOut = false
        sheetHeightPx.updateBounds(lowerBound = collapsedHeightPx, upperBound = expandedHeightPx)
        if (sheetHeightPx.value == 0f || sheetHeightPx.value < collapsedHeightPx) {
            sheetHeightPx.snapTo(collapsedHeightPx)
        }
        if (reduceMotion) {
            sheetHeightPx.snapTo(expandedHeightPx)
        } else {
            sheetHeightPx.animateTo(
                targetValue = expandedHeightPx,
                animationSpec =
                    tween(
                        durationMillis = FlowMotion.ENTER_DURATION_MILLIS,
                        easing = FlowMotion.EnterEasing,
                    ),
            )
        }
    }

    LaunchedEffect(currentQueueIndex, reduceMotion) {
        if (currentQueueIndex >= 0 && currentQueueIndex < queueVideos.size) {
            if (reduceMotion) {
                listState.scrollToItem(currentQueueIndex)
            } else {
                listState.animateScrollToItem(currentQueueIndex)
            }
        }
    }

    BackHandler(onBack = ::animateToDismiss)

    val headerDragModifier =
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
                        velocityY > 1_200f || sheetHeightPx.value < dismissThresholdPx -> animateToDismiss()
                        else -> animateToExpanded()
                    }
                },
            )
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
                                .roundToInt()
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
            shape = BottomSheetDefaults.ExpandedShape,
            color = BottomSheetDefaults.ContainerColor,
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlistTitle ?: stringResource(R.string.playlist_queue),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.queue_position_template,
                                    currentQueueIndex + 1,
                                    queueVideos.size,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    QueueModeIconButton(
                        selected = isShuffled,
                        onClick = { onShuffleToggle(!isShuffled) },
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                    )
                    QueueModeIconButton(
                        selected = isLooping,
                        onClick = { onLoopToggle(!isLooping) },
                        imageVector = Icons.Default.Repeat,
                        contentDescription = stringResource(R.string.repeat),
                    )
                    IconButton(onClick = ::animateToDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                ) {
                    itemsIndexed(displayItems, key = { _, item -> item.key }) { index, item ->
                        val isPlaying = item === currentDisplayItem
                        PlaylistQueueItem(
                            video = item.video,
                            isPlaying = isPlaying,
                            reorderModifier = reorderState.itemModifier(index),
                            dragHandleModifier = reorderState.handleModifier(index),
                            onClick = { onPlayVideoAtIndex(index) },
                            onRemove =
                                if (isPlaying) {
                                    null
                                } else {
                                    { onRemoveVideoAtIndex(index) }
                                },
                            onMoveUp =
                                if (index > 0) {
                                    { onMoveVideoAtIndex(index, index - 1) }
                                } else {
                                    null
                                },
                            onMoveDown =
                                if (index < displayItems.lastIndex) {
                                    { onMoveVideoAtIndex(index, index + 1) }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }
        }
    }
}
