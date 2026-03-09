package com.flow.youtube.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlinx.coroutines.launch
import kotlin.math.abs

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

    /** True when the player is fully or mostly collapsed into mini-player mode. */
    val currentValue: PlayerSheetValue
        get() = if (expandFraction.targetValue > 0.5f) PlayerSheetValue.Collapsed else PlayerSheetValue.Expanded

    val fraction: Float get() = expandFraction.value

    /** Animate to fully expanded / full-screen. */
    fun expand() {
        corner = MiniPlayerCorner.BottomRight // Reset corner so next collapse goes to default position
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
            // Note: X and Y are animated continuously in DraggablePlayerLayout's LaunchedEffect
            // when it detects expandFraction goes to 1f, to ensure accurate dynamic inset tracking.
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

        // Animate continuously to the corner when collapsed
        // (This handles dynamic bottom nav popping in and out)
        LaunchedEffect(state.expandFraction.targetValue, targetMiniX, targetMiniY) {
            if (state.expandFraction.targetValue > 0.5f && !state.offsetX.isRunning && !state.offsetY.isRunning) {
                launch { state.offsetX.animateTo(targetMiniX, spring(dampingRatio = 0.85f, stiffness = 250f)) }
                launch { state.offsetY.animateTo(targetMiniY, spring(dampingRatio = 0.85f, stiffness = 250f)) }
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

                    shadowElevation  = if (fraction > 0f) with(density) { 16.dp.toPx() } else 0f
                    shape            = RoundedCornerShape(if (fraction > 0.1f) 12.dp else 0.dp)
                    clip             = true
                }
                .background(Color.Black)
                // ── Drag gesture ──────────────────────────────────────────────
                .pointerInput(minX, maxX, minY, maxY) {
                    val velocityTracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = { velocityTracker.resetTracking() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(
                                change.uptimeMillis,
                                change.position
                            )

                            // Only allow dragging when mostly collapsed
                            if (state.expandFraction.value > 0.8f) {
                                val newY = (state.offsetY.value + dragAmount.y).coerceIn(minY, maxY)
                                val newX = (state.offsetX.value + dragAmount.x).coerceIn(minX, maxX)

                                state.scope.launch {
                                    state.offsetY.snapTo(newY)
                                    state.offsetX.snapTo(newX)
                                }
                            }
                        },
                        onDragEnd = {
                            if (state.expandFraction.value <= 0.8f) return@detectDragGestures

                            val velocity  = velocityTracker.calculateVelocity()
                            val velY      = velocity.y
                            val velX      = velocity.x

                            val dismissThreshold = 1200f
                            val currentX = state.offsetX.value
                            val currentY = state.offsetY.value

                            // Determine closest corner based on current position and velocity
                            val projectedX = currentX + (velX / 5f)
                            val projectedY = currentY + (velY / 5f)

                            val goLeft = projectedX < screenWidth / 2f
                            val goTop = projectedY < screenHeight / 2f

                            val newCorner = when {
                                goLeft && goTop -> MiniPlayerCorner.TopLeft
                                goLeft && !goTop -> MiniPlayerCorner.BottomLeft
                                !goLeft && goTop -> MiniPlayerCorner.TopRight
                                else -> MiniPlayerCorner.BottomRight
                            }

                            state.scope.launch {
                                // Dismiss check:
                                // - on right side & swiped right prominently
                                // - on left side & swiped left prominently
                                // Crucially, ensure horizontal speed dominates vertical speed
                                val isRightSide = !goLeft
                                val isLeftSide = goLeft
                                val isHorizontalFling = abs(velX) > abs(velY) * 1.5f

                                if (isHorizontalFling && 
                                    ((isRightSide && velX > dismissThreshold) || 
                                     (isLeftSide && velX < -dismissThreshold))) {
                                    
                                    val offScreenX = if (isRightSide) screenWidth + miniWidth
                                                     else -(miniWidth + margin)
                                    launch {
                                        state.offsetX.animateTo(
                                            offScreenX,
                                            spring(dampingRatio = 0.85f, stiffness = 200f)
                                        )
                                    }
                                    kotlinx.coroutines.delay(250)
                                    onDismiss()
                                } else {
                                    // Snap to closest corner
                                    state.corner = newCorner
                                    val snapX = if (goLeft) minX else maxX
                                    val snapY = if (goTop) minY else maxY
                                    
                                    launch { state.offsetX.animateTo(snapX, spring(dampingRatio = 0.85f, stiffness = 250f)) }
                                    launch { state.offsetY.animateTo(snapY, spring(dampingRatio = 0.85f, stiffness = 250f)) }
                                }
                            }
                        },
                        onDragCancel = {
                            if (state.expandFraction.value > 0.8f) {
                                val snapX = if (state.corner == MiniPlayerCorner.TopLeft || state.corner == MiniPlayerCorner.BottomLeft) minX else maxX
                                val snapY = if (state.corner == MiniPlayerCorner.TopLeft || state.corner == MiniPlayerCorner.TopRight) minY else maxY
                                state.scope.launch {
                                    launch { state.offsetX.animateTo(snapX, spring(dampingRatio = 0.85f, stiffness = 250f)) }
                                    launch { state.offsetY.animateTo(snapY, spring(dampingRatio = 0.85f, stiffness = 250f)) }
                                }
                            }
                        }
                    )
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