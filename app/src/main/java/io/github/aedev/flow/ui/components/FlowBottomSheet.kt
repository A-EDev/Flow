package io.github.aedev.flow.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private fun sheetSpring() =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

private const val DISMISS_PROGRESS_THRESHOLD = 0.55f
private const val DISMISS_VELOCITY_THRESHOLD = 1_200f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp? = null,
    dismissOnOutsideTap: Boolean = true,
    onVisibleHeightChange: (Float) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestOnVisibleHeightChange by rememberUpdatedState(onVisibleHeightChange)

    val progress = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var isAnimatingOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        progress.animateTo(targetValue = 1f, animationSpec = sheetSpring())
    }

    LaunchedEffect(Unit) {
        snapshotFlow { sheetHeightPx * progress.value }
            .collect { visibleHeight -> latestOnVisibleHeightChange(visibleHeight) }
    }

    fun animateIn() {
        scope.launch { progress.animateTo(targetValue = 1f, animationSpec = sheetSpring()) }
    }

    fun animateOut() {
        if (isAnimatingOut) return
        isAnimatingOut = true
        scope.launch {
            progress.animateTo(targetValue = 0f, animationSpec = sheetSpring())
            latestOnDismiss()
        }
    }

    BackHandler(onBack = ::animateOut)

    val dragHandleModifier =
        Modifier.pointerInput(sheetHeightPx, isAnimatingOut) {
            if (sheetHeightPx <= 0) return@pointerInput
            val velocityTracker = VelocityTracker()
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    if (isAnimatingOut) return@detectVerticalDragGestures
                    velocityTracker.addPointerInputChange(change)
                    scope.launch {
                        progress.snapTo((progress.value - dragAmount / sheetHeightPx).coerceIn(0f, 1f))
                    }
                },
                onDragCancel = {
                    velocityTracker.resetTracking()
                    if (!isAnimatingOut) animateIn()
                },
                onDragEnd = {
                    val velocityY = velocityTracker.calculateVelocity().y
                    velocityTracker.resetTracking()
                    when {
                        velocityY > DISMISS_VELOCITY_THRESHOLD ||
                            progress.value < DISMISS_PROGRESS_THRESHOLD -> animateOut()

                        else -> animateIn()
                    }
                },
            )
        }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (dismissOnOutsideTap) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { animateOut() }
                        },
            )
        }
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
                    .onSizeChanged { size -> sheetHeightPx = size.height }
                    .graphicsLayer { translationY = size.height * (1f - progress.value) },
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
                            .then(dragHandleModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomSheetDefaults.DragHandle()
                }
                content()
            }
        }
    }
}
