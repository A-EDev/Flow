package io.github.aedev.flow.ui.components

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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import io.github.aedev.flow.player.GlobalPlayerState

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

    /**
     * Scale multiplier for the mini player's width while it stays collapsed (floating).
     * 1f = normal mini size. Values > 1 expand it toward full screen width.
     * At ~2.22x (1/0.45) the mini player fills the full screen width.
     */
    val miniSizeScale = Animatable(1f)

    /** True while the floating mini player is in wide (full-width) mode. */
    val isInlineMode: Boolean get() = miniSizeScale.value > 1.5f

    val currentValue: PlayerSheetValue
        get() = if (expandFraction.targetValue > 0.5f) PlayerSheetValue.Collapsed else PlayerSheetValue.Expanded

    val fraction: Float get() = expandFraction.value

    /** Animate to fully expanded / full-screen video detail view. */
    fun expand() {
        corner = MiniPlayerCorner.BottomRight
        scope.launch {
            launch { miniSizeScale.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 350f)) }
            launch { expandFraction.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 350f)) }
            launch { offsetX.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 400f)) }
            launch { offsetY.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 400f)) }
        }
    }

    /**
     * Expand the floating mini player to full-width while keeping it floating
     * (expandFraction stays at 1). The player scales to fill the screen width
     * and can still be dragged up/down freely.
     */
    fun expandWide() {
        scope.launch {
            launch { miniSizeScale.animateTo(2.5f, spring(dampingRatio = 0.65f, stiffness = 200f)) }
            launch { offsetX.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 200f)) }
        }
    }

    /** Animate to the tracked mini-player corner. Coordinates calculated in layout. */
    fun collapse() {
        scope.launch {
            launch { miniSizeScale.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 200f)) }
        }
        scope.launch {
            if (cachedTargetX == 0f && cachedTargetY == 0f) {
                expandFraction.snapTo(1f)
            } else {
                launch { expandFraction.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 350f)) }
                launch { offsetX.animateTo(cachedTargetX, spring(dampingRatio = 0.85f, stiffness = 600f)) }
                launch { offsetY.animateTo(cachedTargetY, spring(dampingRatio = 0.85f, stiffness = 600f)) }
            }
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
    thumbnailUrl: String? = null,
    topPadding: Dp = 56.dp,
    bottomPadding: Dp = 0.dp,
    miniPlayerScale: Float = 0.45f,
    tapToExpand: Boolean = true,
    onDismiss: () -> Unit = {},
    onCollapseGesture: (() -> Unit)? = null,
    onFullscreenGesture: (() -> Unit)? = null,
    videoAspectRatio: Float = 16f / 9f
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = config.smallestScreenWidthDp >= 600

    var playerHeightFraction by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(videoAspectRatio) { playerHeightFraction = 1f }

    val statusBarHeight = WindowInsets.statusBars.getTop(density).toFloat()
    // Capture the system layout direction (e.g. RTL for Arabic) before we override it.
    // The override is needed so the mini-player Box is always placed from the physical
    // left edge, making graphicsLayer translationX values correct in RTL locales.
    val systemLayoutDirection = LocalLayoutDirection.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth  = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        // ── 1. Immersive Fullscreen ───────────────────────────────────────────
        val showImmersiveFullscreen = state.currentValue == PlayerSheetValue.Expanded &&
                (isFullscreen || (isLandscape && !isTablet))

        // ── 2. Derive dimensions ──────────────────────────────────────────────
        val isSplitLayout = isLandscape && isTablet

        val targetMiniWidth = screenWidth * miniPlayerScale
        val baseMiniWidth  = if (isTablet) targetMiniWidth.coerceAtMost(400f) else targetMiniWidth
        // Live-scale the mini width based on pinch/wide-mode; clamp to screen width
        val currentSizeScale by remember { derivedStateOf { state.miniSizeScale.value } }
        val margin     = with(density) { 8.dp.toPx() }
        val maxWideWidth = screenWidth - (margin * 2)
        val miniWidth  = (baseMiniWidth * currentSizeScale).coerceAtMost(maxWideWidth)
        val miniHeight = miniWidth * (9f / 16f)
        val bottomNavPad = with(density) { bottomPadding.toPx() }
        val topBarPad    = with(density) { topPadding.toPx() }
        // When in wide mode, lock X to 0 (centered); otherwise normal corner positioning
        val isWideMode = currentSizeScale > 1.5f

        val expandedVideoWidth  = if (isSplitLayout) screenWidth * 0.65f else screenWidth
        val baseVideoHeight     = expandedVideoWidth * (9f / 16f)
        val clampedAspect       = videoAspectRatio.coerceAtMost(2.0f)
        val fullVideoHeight     = expandedVideoWidth / clampedAspect
        val expandedVideoHeight = fullVideoHeight

        val minX = margin 
        val maxX = (screenWidth - miniWidth - margin).coerceAtLeast(margin)
        val minY = statusBarHeight + topBarPad + margin
        val maxY = screenHeight - miniHeight - bottomNavPad - margin
        LaunchedEffect(minX, maxX, minY, maxY) {
           val overshootMargin = with(density) { 8.dp.toPx() }
           state.offsetX.updateBounds(
               lowerBound = minX - overshootMargin,
               upperBound = maxX + overshootMargin
           )
           state.offsetY.updateBounds(
               lowerBound = minY - overshootMargin,
               upperBound = maxY + overshootMargin  
           )
       }
        val targetMiniX = if (isWideMode) margin else when (state.corner) {
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

        LaunchedEffect(state.expandFraction.targetValue, targetMiniX, targetMiniY, state.isDragging, isWideMode) {
            if (state.expandFraction.targetValue > 0.5f && !state.isDragging) {
                if (isWideMode) {
                    // In wide mode only snap X to margin; Y is freely draggable
                    launch { state.offsetX.animateTo(margin, spring(dampingRatio = 0.75f, stiffness = 400f)) }
                } else {
                    val needsSnap = state.offsetX.value == 0f && state.offsetY.value == 0f
                        && targetMiniX > 0f && targetMiniY > 0f
                    if (needsSnap) {
                        state.offsetX.snapTo(targetMiniX)
                        state.offsetY.snapTo(targetMiniY)
                    } else {
                        launch { state.offsetX.animateTo(targetMiniX, spring(dampingRatio = 0.75f, stiffness = 400f)) }
                        launch { state.offsetY.animateTo(targetMiniY, spring(dampingRatio = 0.75f, stiffness = 400f)) }
                    }
                }
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

        // ── 4. Fullscreen blurred background (drawn first = behind video) ─────
        if (showImmersiveFullscreen) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            if (!thumbnailUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(60.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.65f
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            }
        }

        // ── 5. Background scrim (non-fullscreen only) ─────────────────────────
        val expandedScrimAlpha by remember {
            derivedStateOf { (1f - state.expandFraction.value).coerceIn(0f, 1f) }
        }

        if (!showImmersiveFullscreen && expandedScrimAlpha > 0f && !state.isInlineMode) {
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

        // ── 6. Body content (info, comments, related — non-fullscreen only) ────
        val bodyAlpha by remember { derivedStateOf { (1f - state.expandFraction.value * 1.25f).coerceIn(0f, 1f) } }

        if (!showImmersiveFullscreen && bodyAlpha > 0f && !state.isInlineMode) {
            val videoHeightPlaceholder =
                if (isSplitLayout) with(density) { expandedVideoHeight.toDp() } else 0.dp

            val bodyPaddingTop =
                if (isSplitLayout) statusBarHeight
                else expandedVideoHeight + statusBarHeight

            // Restore the original system layout direction for body content (comments,
            // related videos, etc.) so RTL language users see proper text direction.
            CompositionLocalProvider(LocalLayoutDirection provides systemLayoutDirection) {
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
            } // end restore system layout direction
        }

        // ── 7. Video player box ───────────────────────────────────────────────
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
        val _tapToExpand = rememberUpdatedState(tapToExpand)
        val _onFullscreenGesture = rememberUpdatedState(onFullscreenGesture)
        val _isLandscape = rememberUpdatedState(isLandscape)
        val _isFullscreen = rememberUpdatedState(isFullscreen)

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = if (showImmersiveFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
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
                            0f,
                            state.offsetX.value,
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
                        shadowElevation = if (fraction > 0.95f) with(density) { 8.dp.toPx() } else 0f
                        shape = RoundedCornerShape(if (fraction > 0.1f) 12.dp else 0.dp)
                        clip = true
                    }
                    .background(Color.Black)
                    // ── Live pinch-to-resize gesture ──────────────────────────
                    .pointerInput("pinch") {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            // Check if a second finger appears
                            val evt = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                            val pressed = evt.changes.filter { it.pressed }
                            if (pressed.size < 2) return@awaitEachGesture
                            // Only activate for mini player
                            if (state.expandFraction.value < 0.8f) return@awaitEachGesture

                            val ptr1Id = pressed[0].id
                            val ptr2Id = pressed[1].id
                            val initialDist = (pressed[0].position - pressed[1].position)
                                .getDistance().coerceAtLeast(1f)
                            val startScale = state.miniSizeScale.value
                            // maxScale = 1/miniPlayerScale brings baseMiniWidth to full screenWidth
                            val maxScale = (1f / miniPlayerScale).coerceAtLeast(1f)

                            // Track spread / pinch live
                            while (true) {
                                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                                val p1 = e.changes.firstOrNull { it.id == ptr1Id } ?: break
                                val p2 = e.changes.firstOrNull { it.id == ptr2Id } ?: break
                                if (!p1.pressed || !p2.pressed) {
                                    // On release, spring to nearest size: wide or normal
                                    val targetScale = if (state.miniSizeScale.value > 1.5f) maxScale else 1f
                                    state.scope.launch {
                                        state.miniSizeScale.animateTo(
                                            targetScale,
                                            spring(dampingRatio = 0.65f, stiffness = 200f)
                                        )
                                        if (targetScale <= 1f) {
                                            // Return to corner after collapsing
                                            state.scope.launch {
                                                state.offsetX.animateTo(state.cachedTargetX, spring(dampingRatio = 0.65f, stiffness = 200f))
                                                state.offsetY.animateTo(state.cachedTargetY, spring(dampingRatio = 0.65f, stiffness = 200f))
                                            }
                                        } else {
                                            state.scope.launch { state.offsetX.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 200f)) }
                                        }
                                    }
                                    break
                                }
                                p1.consume(); p2.consume()
                                val currentDist = (p1.position - p2.position).getDistance()
                                val gestureScale = currentDist / initialDist
                                val newScale = (startScale * gestureScale).coerceIn(1f, maxScale)
                                state.scope.launch { state.miniSizeScale.snapTo(newScale) }
                                // If going wide, snap X to 0
                                if (newScale > 1.5f) {
                                    state.scope.launch { state.offsetX.snapTo(margin) }
                                }
                            }
                        }
                    }
                    // ── Drag gesture ──────────────────────────────────────────
                    .pointerInput(Unit) {
                        val velocityTracker = VelocityTracker()
                        var snapJob: Job? = null
                        var lastTapTime = 0L
                        var singleTapJob: Job? = null
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
                            val downConsumedByChild = down.isConsumed

                            val isCollapseDrag = state.expandFraction.value < 0.4f
                            val isMiniDrag     = state.expandFraction.value > 0.8f

                            val canSwipeToFullscreen = isCollapseDrag &&
                                !_isLandscape.value &&
                                !_isFullscreen.value &&
                                _onFullscreenGesture.value != null

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
                                    state.dragScale.animateTo(0.97f, spring(dampingRatio = 0.7f, stiffness = 600f))
                                }
                            }

                            var dragPointerId = down.id
                            var hasCrossedSlop = !isCollapseDrag
                            var startDragY = 0f
                            // 0 = undecided, 1 = down (collapse), -1 = up (fullscreen)
                            var detectedDirection = 0

                            if (isCollapseDrag) {
                                val slop = viewConfiguration.touchSlop
                                while (!hasCrossedSlop) {
                                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == dragPointerId }
                                    if (change == null || !change.pressed || change.isConsumed) break
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)

                                    val delta = change.position - down.position
                                    if (delta.y > slop && delta.y > kotlin.math.abs(delta.x)) {
                                        // Swipe DOWN → collapse
                                        hasCrossedSlop = true
                                        startDragY = delta.y
                                        detectedDirection = 1
                                        change.consume()
                                    } else if (canSwipeToFullscreen &&
                                        delta.y < -slop &&
                                        kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x)
                                    ) {
                                        // Swipe UP → fullscreen (portrait only)
                                        hasCrossedSlop = true
                                        startDragY = delta.y
                                        detectedDirection = -1
                                        change.consume()
                                    } else if (kotlin.math.abs(delta.x) > slop) {
                                        break
                                    }
                                }
                            }

                            state.isDragging = true
                            var cumulativeDragY = startDragY
                            var totalMovement = 0f
                            val startFraction   = state.expandFraction.value
                            val collapseTravel  = (targetMiniY - statusBarHeight).coerceAtLeast(1f)
                            var totalUpwardDrag = 0f

                            if (hasCrossedSlop) {
                                try {
                                    drag(dragPointerId) { change ->
                                        val delta = change.positionChange()
                                        totalMovement += delta.getDistance()
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                                        if (isCollapseDrag && detectedDirection == 1) {
                                            change.consume()
                                            cumulativeDragY += delta.y
                                            val rawFraction =
                                                (startFraction + cumulativeDragY / collapseTravel).coerceIn(0f, 1f)
                                            snapJob?.cancel()
                                            snapJob = state.scope.launch {
                                                state.expandFraction.snapTo(rawFraction)
                                            }
                                        } else if (isCollapseDrag && detectedDirection == -1) {
                                            // Track upward drag distance for fullscreen gesture
                                            change.consume()
                                            totalUpwardDrag += -delta.y
                                        } else if (isMiniDrag) {
                                            if (totalMovement > viewConfiguration.touchSlop * 0.5f) {
                                                change.consume()
                                                val overDragFactor = 0.3f 
                                                val rawX = state.offsetX.value + delta.x
                                                val rawY = state.offsetY.value + delta.y

                                                val newX = when {
                                                    rawX < minX -> minX + (rawX - minX) * overDragFactor
                                                    rawX > maxX -> maxX + (rawX - maxX) * overDragFactor
                                                    else -> rawX
                                                }
                                                val newY = when {
                                                    rawY < minY -> minY + (rawY - minY) * overDragFactor
                                                    rawY > maxY -> maxY + (rawY - maxY) * overDragFactor
                                                    else -> rawY
                                                }
                                                snapJob = state.scope.launch {
                                                    state.offsetY.snapTo(newY)
                                                    state.offsetX.snapTo(newX)
                                                }
                                            }
                                        }
                                    }
                                } finally {
                                    snapJob?.cancel(); snapJob = null
                                    state.isDragging = false
                                    state.scope.launch {
                                        state.dragScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
                                    }
                                }
                            } else {
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                                        if (event.changes.all { !it.pressed }) break
                                    }
                                } finally {
                                    state.isDragging = false
                                }
                            }

                            if (isMiniDrag && totalMovement < 24f) {
                                if (!downConsumedByChild && _tapToExpand.value) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTime < 300L) {
                                        // Double-tap: toggle inline mode
                                        singleTapJob?.cancel()
                                        lastTapTime = 0L
                                        if (state.isInlineMode) state.collapse() else state.expandWide()
                                    } else {
                                        // Single-tap: wait briefly in case a second tap follows
                                        lastTapTime = now
                                        singleTapJob = state.scope.launch {
                                            kotlinx.coroutines.delay(300L)
                                            state.expand()
                                        }
                                    }
                                }
                                return@awaitEachGesture
                            }

                            // Resolve swipe-up to fullscreen gesture
                            if (isCollapseDrag && detectedDirection == -1) {
                                val velY = velocityTracker.calculateVelocity().y
                                val shouldFullscreen = totalUpwardDrag > 80f || velY < -800f
                                if (shouldFullscreen) {
                                    _onFullscreenGesture.value?.invoke()
                                }
                                return@awaitEachGesture
                            }

                            if (isCollapseDrag) {
                                val velY = velocityTracker.calculateVelocity().y
                                val shouldCollapse = state.expandFraction.value > 0.1f || velY > 300f ||
                                (velY > 200f && state.expandFraction.value > 0.05f)
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

                            val originX = when (state.corner) {                            
                              MiniPlayerCorner.TopLeft, MiniPlayerCorner.BottomLeft -> minX
                              MiniPlayerCorner.TopRight, MiniPlayerCorner.BottomRight -> maxX
                            }
                            val originY = when (state.corner) {                            
                              MiniPlayerCorner.TopLeft, MiniPlayerCorner.TopRight -> minY
                              MiniPlayerCorner.BottomLeft, MiniPlayerCorner.BottomRight -> maxY
                            }

                            val deltaFromOriginX = currentX - originX
                            val deltaFromOriginY = currentY - originY

                            val totalTravelX = (maxX - minX).coerceAtLeast(1f)
                            val totalTravelY = (maxY - minY).coerceAtLeast(1f)

                            val switchThresholdX = totalTravelX * 0.15f
                            val switchThresholdY = totalTravelY * 0.15f

                            val projectedDeltaX = deltaFromOriginX + velX * 0.3f
                            val projectedDeltaY = deltaFromOriginY + velY * 0.3f

                            val wasLeft = state.corner == MiniPlayerCorner.TopLeft ||
                                          state.corner == MiniPlayerCorner.BottomLeft
                            val wasTop  = state.corner == MiniPlayerCorner.TopLeft ||
                                          state.corner == MiniPlayerCorner.TopRight

                            val goLeft = when {
                                abs(velX) > 400f && abs(velX) > abs(velY) * 0.8f -> velX < 0
                                wasLeft && projectedDeltaX > switchThresholdX -> false
                                !wasLeft && projectedDeltaX < -switchThresholdX -> true
                                else -> wasLeft
                            }

                            val goTop = when {
                                abs(velY) > 400f && abs(velY) > abs(velX) * 0.8f -> velY < 0
                                wasTop && projectedDeltaY > switchThresholdY -> false
                                !wasTop && projectedDeltaY < -switchThresholdY -> true
                                else -> wasTop
                            }

                            val newCorner = when {
                                goLeft && goTop   -> MiniPlayerCorner.TopLeft
                                goLeft && !goTop  -> MiniPlayerCorner.BottomLeft
                                !goLeft && goTop  -> MiniPlayerCorner.TopRight
                                else              -> MiniPlayerCorner.BottomRight
                            }

                            val isHorizontalFling = abs(velX) > abs(velY) * 3f
                            val isNearRightEdge = currentX > maxX * 0.6f
                            val isNearLeftEdge = currentX < minX + (maxX - minX) * 0.4f
                            val canDismissRight = !goLeft && velX > 2000f && isNearRightEdge
                            val canDismissLeft = goLeft && velX < -2000f && isNearLeftEdge
                            if (isHorizontalFling &&
                                (canDismissRight || canDismissLeft)) {
                                val offScreenX = if (!goLeft) screenWidth + miniWidth else -(miniWidth + margin)
                                state.scope.launch {
                                    launch { state.offsetX.animateTo(offScreenX, spring(dampingRatio = 0.9f, stiffness = 300f), initialVelocity = velX) }
                                    kotlinx.coroutines.delay(200)
                                    onDismiss()
                                }
                            } else {
                                state.corner = newCorner
                                state.scope.launch {
                                    launch { state.offsetX.animateTo(if (goLeft) minX else maxX, spring(dampingRatio = 0.75f, stiffness = 400f), initialVelocity = velX) }
                                    launch { state.offsetY.animateTo(if (goTop) minY else maxY, spring(dampingRatio = 0.75f, stiffness = 400f), initialVelocity = velY) }
                                }
                            }
                        }
                    }
            }
        ) {
            // ── Video surface
            videoContent(Modifier.fillMaxSize())

            // ── Mini controls overlay
            val fraction by remember { derivedStateOf { state.expandFraction.value } }
            if (!showImmersiveFullscreen && fraction > 0.6f) {
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
            if (!showImmersiveFullscreen && fraction > 0.8f) {
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
    } // end CompositionLocalProvider(LayoutDirection.Ltr)
}
