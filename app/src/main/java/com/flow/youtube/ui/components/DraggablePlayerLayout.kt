package com.flow.youtube.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.flow.youtube.player.GlobalPlayerState

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)

enum class PlayerSheetValue { Expanded, Collapsed }
enum class MiniPlayerCorner { TopLeft, TopRight, BottomLeft, BottomRight }

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

class PlayerDraggableState(
    val offsetX: Animatable<Float, AnimationVector1D>,
    val offsetY: Animatable<Float, AnimationVector1D>,
    val expandFraction: Animatable<Float, AnimationVector1D>,
    val scope: CoroutineScope
) {
    var corner by mutableStateOf(MiniPlayerCorner.BottomRight)
    var isDragging by mutableStateOf(false)
    val dragScale = Animatable(1f)

    var cachedTargetX by mutableFloatStateOf(0f)
    var cachedTargetY by mutableFloatStateOf(0f)

    val currentValue: PlayerSheetValue
        get() = if (expandFraction.targetValue > 0.5f) PlayerSheetValue.Collapsed else PlayerSheetValue.Expanded

    val fraction: Float get() = expandFraction.value

    /** Animate to fully expanded / full-screen. */
    fun expand() {
        corner = MiniPlayerCorner.BottomRight
        scope.launch {
            launch { expandFraction.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 250f)) }
            launch { offsetX.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 250f)) }
            launch { offsetY.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 250f)) }
        }
    }

    /** Animate to the tracked mini-player corner. Coordinates calculated in layout. */
    fun collapse() {
        scope.launch {
            expandFraction.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 250f))
        }
    }

    /** Instantly snap to a target value (e.g. on orientation change). */
    fun snapTo(target: PlayerSheetValue) {
        scope.launch {
            val targetF = if (target == PlayerSheetValue.Collapsed) 1f else 0f
            expandFraction.snapTo(targetF)
            if (target == PlayerSheetValue.Expanded) {
                offsetX.snapTo(0f)
                offsetY.snapTo(0f)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Remember helper
// ---------------------------------------------------------------------------

@Composable
fun rememberPlayerDraggableState(): PlayerDraggableState {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val expandFraction = remember { Animatable(1f) } // Default minimized

    return remember {
        PlayerDraggableState(offsetX, offsetY, expandFraction, scope)
    }
}

// ---------------------------------------------------------------------------
// Main composable
// ---------------------------------------------------------------------------

@Composable
fun DraggablePlayerLayout(
    state: PlayerDraggableState,
    videoContent: @Composable (Modifier) -> Unit,
    bodyContent: @Composable (Float, androidx.compose.ui.unit.Dp) -> Unit,
    miniControls: @Composable (Float) -> Unit,
    progress: Float,
    isFullscreen: Boolean,
    bottomPadding: Dp = 0.dp,
    miniPlayerScale: Float = 0.45f,
    onDismiss: () -> Unit = {},
    onCollapseGesture: (() -> Unit)? = null,
    videoAspectRatio: Float = 16f / 9f
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = config.smallestScreenWidthDp >= 600

    var playerHeightFraction by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(videoAspectRatio) { playerHeightFraction = 1f }

    val statusBarHeight = WindowInsets.statusBars.getTop(density).toFloat()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth  = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        // ── 1. Immersive Fullscreen ───────────────────────────────────────────
        val showImmersiveFullscreen = state.currentValue == PlayerSheetValue.Expanded &&
                (isFullscreen || (isLandscape && !isTablet))

        if (showImmersiveFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                videoContent(Modifier.fillMaxSize())
            }
            return@BoxWithConstraints
        }

        // ── 2. Derive dimensions ──────────────────────────────────────────────
        val isSplitLayout = isLandscape && isTablet

        // Mini-player target size based on user preference scale
        val targetMiniWidth = screenWidth * miniPlayerScale
        val miniWidth  = if (isTablet) targetMiniWidth.coerceAtMost(400f) else targetMiniWidth
        val miniHeight = miniWidth * (9f / 16f)
        val margin     = with(density) { 12.dp.toPx() }
        val bottomNavPad = with(density) { bottomPadding.toPx() }

        // Expanded video size
        val expandedVideoWidth  = if (isSplitLayout) screenWidth * 0.65f else screenWidth
        val baseVideoHeight     = expandedVideoWidth * (9f / 16f)
        val clampedAspect       = videoAspectRatio.coerceAtMost(2.0f)
        val fullVideoHeight     = expandedVideoWidth / clampedAspect
        val expandedVideoHeight = fullVideoHeight

        // 4 corners
        val minX = margin
        val maxX = screenWidth - miniWidth - margin
        val minY = statusBarHeight + margin
        val maxY = screenHeight - miniHeight - bottomNavPad - margin

        val targetMiniX = when (state.corner) {
            MiniPlayerCorner.TopLeft, MiniPlayerCorner.BottomLeft -> minX
            MiniPlayerCorner.TopRight, MiniPlayerCorner.BottomRight -> maxX
        }
        val targetMiniY = when (state.corner) {
            MiniPlayerCorner.TopLeft, MiniPlayerCorner.TopRight -> minY
            MiniPlayerCorner.BottomLeft, MiniPlayerCorner.BottomRight -> maxY
        }

        SideEffect {
            state.cachedTargetX = targetMiniX
            state.cachedTargetY = targetMiniY
        }

        // Animate continuously to the corner when collapsed
        // (This handles dynamic bottom nav popping in and out)
        LaunchedEffect(state.expandFraction.targetValue, targetMiniX, targetMiniY, state.isDragging) {
            if (state.expandFraction.targetValue > 0.5f && !state.isDragging &&
                !state.offsetX.isRunning && !state.offsetY.isRunning) {
                launch { state.offsetX.animateTo(targetMiniX, spring(dampingRatio = 0.72f, stiffness = 450f)) }
                launch { state.offsetY.animateTo(targetMiniY, spring(dampingRatio = 0.72f, stiffness = 450f)) }
            }
        }

        // ── 3. Nested scroll for in-video aspect-ratio resizing ───────────────
        val nestedScrollConnection = remember(fullVideoHeight, baseVideoHeight) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    val playerDelta = fullVideoHeight - baseVideoHeight
                    if (delta < 0 && playerHeightFraction > 0f && playerDelta > 1f) {
                        val maxConsumable = playerHeightFraction * playerDelta
                        val consumed = maxOf(delta, -maxConsumable)
                        playerHeightFraction =
                            (playerHeightFraction + consumed / playerDelta).coerceIn(0f, 1f)
                        return Offset(0f, consumed)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    val delta = available.y
                    val playerDelta = fullVideoHeight - baseVideoHeight
                    if (delta > 0 && playerHeightFraction < 1f && playerDelta > 1f) {
                        val maxConsumable = (1f - playerHeightFraction) * playerDelta
                        val consumable = minOf(delta, maxConsumable)
                        playerHeightFraction =
                            (playerHeightFraction + consumable / playerDelta).coerceIn(0f, 1f)
                        return Offset(0f, consumable)
                    }
                    return Offset.Zero
                }
            }
        }

        // ── 4. Background scrim ───────────────────────────────────────────────
        val expandedScrimAlpha by remember {
            derivedStateOf { (1f - state.expandFraction.value).coerceIn(0f, 1f) }
        }

        if (expandedScrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(expandedScrimAlpha)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { statusBarHeight.toDp() })
                        .background(Color.Black)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = with(density) { statusBarHeight.toDp() })
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }

        // ── 5. Body content (info, comments, related) ─────────────────────────
        val bodyAlpha by remember { derivedStateOf { (1f - state.expandFraction.value * 1.25f).coerceIn(0f, 1f) } }

        if (bodyAlpha > 0f) {
            val videoHeightPlaceholder =
                if (isSplitLayout) with(density) { expandedVideoHeight.toDp() } else 0.dp

            val bodyPaddingTop =
                if (isSplitLayout) statusBarHeight
                else expandedVideoHeight + statusBarHeight

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = with(density) { bodyPaddingTop.toDp() })
                    .graphicsLayer {
                        alpha = bodyAlpha
                        translationY = state.expandFraction.value * 80f
                    }
                    .nestedScroll(nestedScrollConnection)
            ) {
                bodyContent(bodyAlpha, videoHeightPlaceholder)
            }
        }

        // ── 6. Draggable Video Player ─────────────────────────────────────────
        val _minX = rememberUpdatedState(minX)
        val _maxX = rememberUpdatedState(maxX)
        val _minY = rememberUpdatedState(minY)
        val _maxY = rememberUpdatedState(maxY)
        val _statusBarH = rememberUpdatedState(statusBarHeight)
        val _targetMiniX = rememberUpdatedState(targetMiniX)
        val _targetMiniY = rememberUpdatedState(targetMiniY)
        val _screenWidth = rememberUpdatedState(screenWidth)
        val _miniWidth = rememberUpdatedState(miniWidth)
        val _margin = rememberUpdatedState(margin)
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val fraction = state.expandFraction.value
                    val targetW = lerpFloat(expandedVideoWidth, miniWidth, fraction).toInt()
                        .coerceIn(1, constraints.maxWidth)
                    val targetH = lerpFloat(expandedVideoHeight, miniHeight, fraction).toInt()
                        .coerceIn(1, constraints.maxHeight)
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth  = targetW, maxWidth  = targetW,
                            minHeight = targetH, maxHeight = targetH
                        )
                    )
                    layout(targetW, targetH) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val fraction = state.expandFraction.value

                    val rawX = lerpFloat(
                        0f, // Expanded X is always 0
                        state.offsetX.value, // Collapsed X is dragged position
                        fraction
                    )
                    translationX = rawX

                    val topPadExpanded = statusBarHeight
                    val rawY = lerpFloat(
                        topPadExpanded, 
                        state.offsetY.value, 
                        fraction
                    )
                    translationY = rawY

                    val miniScale = if (fraction > 0.6f) state.dragScale.value else 1f
                    scaleX = miniScale
                    scaleY = miniScale
                    shadowElevation  = if (fraction > 0f) with(density) { 20.dp.toPx() } else 0f
                    shape            = RoundedCornerShape(if (fraction > 0.1f) 12.dp else 0.dp)
                    clip             = true
                }
                .background(Color.Black)
                // ── Drag gesture (handles both mini-player corner drag AND collapse-from-expanded) ──
                .pointerInput(Unit) {
                    val velocityTracker = VelocityTracker()
                    var snapJob: Job? = null

                    awaitEachGesture {
                        val minX          = _minX.value
                        val maxX          = _maxX.value
                        val minY          = _minY.value
                        val maxY          = _maxY.value
                        val statusBarHeight = _statusBarH.value
                        val targetMiniX   = _targetMiniX.value
                        val targetMiniY   = _targetMiniY.value
                        val screenWidth   = _screenWidth.value
                        val miniWidth     = _miniWidth.value
                        val margin        = _margin.value

                        val down = awaitFirstDown(requireUnconsumed = false)

                        val isCollapseDrag = state.expandFraction.value < 0.4f
                        val isMiniDrag     = state.expandFraction.value > 0.8f

                        if (isCollapseDrag) {
                            down.consume()
                        }

                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        snapJob?.cancel(); snapJob = null

                        if (isCollapseDrag) {
                            state.scope.launch {
                                state.expandFraction.stop()
                                state.offsetX.stop()
                                state.offsetY.stop()
                                state.offsetX.snapTo(targetMiniX)
                                state.offsetY.snapTo(targetMiniY)
                            }
                        } else if (isMiniDrag) {
                            state.scope.launch {
                                state.dragScale.animateTo(0.96f, spring(dampingRatio = 0.6f, stiffness = 700f))
                            }
                        }

                        state.isDragging = true
                        var cumulativeDragY = 0f
                        val startFraction   = state.expandFraction.value
                        val collapseTravel  = (targetMiniY - statusBarHeight).coerceAtLeast(1f)
                        try {
                            drag(down.id) { change ->
                                val delta = change.positionChange()
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)

                                if (isCollapseDrag) {
                                    cumulativeDragY += delta.y
                                    val rawFraction =
                                        (startFraction + cumulativeDragY / collapseTravel).coerceIn(0f, 1f)
                                    snapJob?.cancel()
                                    snapJob = state.scope.launch {
                                        state.expandFraction.snapTo(rawFraction)
                                    }
                                } else if (isMiniDrag) {
                                    val newY = (state.offsetY.value + delta.y).coerceIn(minY, maxY)
                                    val newX = (state.offsetX.value + delta.x).coerceIn(minX, maxX)
                                    snapJob?.cancel()
                                    snapJob = state.scope.launch {
                                        state.offsetY.snapTo(newY)
                                        state.offsetX.snapTo(newX)
                                    }
                                }
                            }
                        } finally {
                            // ── Pointer lifted OR gesture cancelled ───────────────────────────
                            snapJob?.cancel(); snapJob = null
                            state.isDragging = false
                            state.scope.launch {
                                state.dragScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
                            }
                        }

                        if (isCollapseDrag) {
                            val velY = velocityTracker.calculateVelocity().y
                            val shouldCollapse = state.expandFraction.value > 0.4f || velY > 1000f
                            if (shouldCollapse) {
                                onCollapseGesture?.invoke()
                                GlobalPlayerState.showMiniPlayer()
                                state.collapse()
                            } else {
                                state.expand()
                            }
                            return@awaitEachGesture
                        }

                        if (!isMiniDrag) return@awaitEachGesture

                        val velocity = velocityTracker.calculateVelocity()
                        val velY     = velocity.y
                        val velX     = velocity.x
                        val currentX = state.offsetX.value
                        val currentY = state.offsetY.value

                        val projectedX = currentX + velX * 0.25f
                        val projectedY = currentY + velY * 0.25f
                        val midX = (minX + maxX) / 2f
                        val midY = (minY + maxY) / 2f

                        val goLeft = if (abs(velX) > 700f && abs(velX) > abs(velY)) velX < 0
                                     else projectedX < midX
                        val goTop  = if (abs(velY) > 700f && abs(velY) > abs(velX)) velY < 0
                                     else projectedY < midY

                        val newCorner = when {
                            goLeft && goTop   -> MiniPlayerCorner.TopLeft
                            goLeft && !goTop  -> MiniPlayerCorner.BottomLeft
                            !goLeft && goTop  -> MiniPlayerCorner.TopRight
                            else              -> MiniPlayerCorner.BottomRight
                        }

                        val isHorizontalFling = abs(velX) > abs(velY) * 1.5f
                        if (isHorizontalFling &&
                            ((!goLeft && velX > 1200f) || (goLeft && velX < -1200f))) {
                            val offScreenX = if (!goLeft) screenWidth + miniWidth else -(miniWidth + margin)
                            state.scope.launch {
                                launch { state.offsetX.animateTo(offScreenX, spring(dampingRatio = 0.9f, stiffness = 300f)) }
                                kotlinx.coroutines.delay(200)
                                onDismiss()
                            }
                        } else {
                            state.corner = newCorner
                            state.scope.launch {
                                launch { state.offsetX.animateTo(if (goLeft) minX else maxX, spring(dampingRatio = 0.72f, stiffness = 450f)) }
                                launch { state.offsetY.animateTo(if (goTop) minY else maxY, spring(dampingRatio = 0.72f, stiffness = 450f)) }
                            }
                        }
                    }
                }
                .then(
                    if (state.expandFraction.value > 0.6f) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { state.expand() }
                        }
                    } else Modifier
                )
        ) {
            // ── Video surface
            videoContent(Modifier.fillMaxSize())

            // ── Mini controls overlay
            val fraction by remember { derivedStateOf { state.expandFraction.value } }
            if (fraction > 0.6f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f * fraction))
                        .alpha(fraction)
                ) {
                    miniControls(fraction)
                }
            }

            // ── Progress bar (mini only)
            if (fraction > 0.8f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color.Red,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}