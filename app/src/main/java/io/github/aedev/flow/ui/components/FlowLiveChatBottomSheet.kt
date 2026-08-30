package io.github.aedev.flow.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.LiveChatMessage
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val LIVE_CHAT_DISMISS_FRACTION = 0.55f
private const val LIVE_CHAT_DISMISS_VELOCITY = 1_200f

@Composable
fun FlowLiveChatBottomSheet(
    messages: List<LiveChatMessage>,
    isLoading: Boolean,
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
    val dismissThresholdPx = collapsedHeightPx + sheetProgressRangePx * LIVE_CHAT_DISMISS_FRACTION
    val sheetHeightPx = remember { Animatable(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }

    LaunchedEffect(sheetHeightPx, collapsedHeightPx, sheetProgressRangePx) {
        snapshotFlow {
            ((sheetHeightPx.value - collapsedHeightPx) / sheetProgressRangePx).coerceIn(0f, 1f)
        }.distinctUntilChanged()
            .collect(latestOnSheetProgressChange)
    }

    fun animateToExpanded() {
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = expandedHeightPx,
                animationSpec =
                    tween(
                        durationMillis = FlowMotion.durationFor(FlowMotion.EMPHASIZED_DURATION_MILLIS, reduceMotion),
                        easing = FlowMotion.EnterEasing,
                    ),
            )
        }
    }

    fun animateToDismiss() {
        if (isAnimatingOut) return
        isAnimatingOut = true
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = collapsedHeightPx,
                animationSpec =
                    tween(
                        durationMillis = FlowMotion.durationFor(FlowMotion.EXIT_DURATION_MILLIS, reduceMotion),
                        easing = FlowMotion.ExitEasing,
                    ),
            )
            latestOnDismiss()
        }
    }

    LaunchedEffect(expandedHeightPx, collapsedHeightPx, reduceMotion) {
        isAnimatingOut = false
        sheetHeightPx.updateBounds(lowerBound = collapsedHeightPx, upperBound = expandedHeightPx)
        if (sheetHeightPx.value == 0f || sheetHeightPx.value < collapsedHeightPx) {
            sheetHeightPx.snapTo(collapsedHeightPx)
        }
        sheetHeightPx.animateTo(
            targetValue = expandedHeightPx,
            animationSpec =
                tween(
                    durationMillis = FlowMotion.durationFor(FlowMotion.EMPHASIZED_DURATION_MILLIS, reduceMotion),
                    easing = FlowMotion.EnterEasing,
                ),
        )
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
                        val nextValue =
                            (sheetHeightPx.value - dragAmount).coerceIn(collapsedHeightPx, expandedHeightPx)
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
                        velocityY > LIVE_CHAT_DISMISS_VELOCITY || sheetHeightPx.value < dismissThresholdPx -> {
                            animateToDismiss()
                        }

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
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
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
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.live_chat),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = ::animateToDismiss,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LiveChatList(
                    messages = messages,
                    isLoading = isLoading,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                )
            }
        }
    }
}
