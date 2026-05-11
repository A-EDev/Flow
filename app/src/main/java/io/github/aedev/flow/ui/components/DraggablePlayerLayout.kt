package io.github.aedev.flow.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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

private fun playerExpandSpring() = spring<Float>(dampingRatio = 0.86f, stiffness = 520f)
private fun miniSnapSpring() = spring<Float>(dampingRatio = 0.82f, stiffness = 500f)
private fun miniResizeSpring() = spring<Float>(dampingRatio = 0.72f, stiffness = 280f)
private fun miniDismissSpring() = spring<Float>(dampingRatio = 0.9f, stiffness = 340f)

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

    val miniSizeScale = Animatable(1f)

    /** True while the floating mini player is in wide (enlarged) mode. */
    val isInlineMode: Boolean get() = miniSizeScale.value > 1.5f

    val currentValue: PlayerSheetValue
        get() = if (expandFraction.targetValue > 0.5f) PlayerSheetValue.Collapsed
                else PlayerSheetValue.Expanded

    val fraction: Float get() = expandFraction.value

    fun expand() {
        corner = MiniPlayerCorner.BottomRight
        scope.launch {
            val anim = playerExpandSpring()
            launch { miniSizeScale.animateTo(1f, anim) }
            launch { expandFraction.animateTo(0f, anim) }
            launch { offsetX.animateTo(0f, anim) }
            launch { offsetY.animateTo(0f, anim) }
        }
    }

    /**
     * Expand the floating mini player to wide mode.
     */
    fun expandWide(
        miniPlayerScale: Float = 0.45f,
        screenWidth: Float = 0f,
        margin: Float = 0f,
        baseMiniWidth: Float = 0f,
        isTablet: Boolean = false,
        isFoldable: Boolean = false
    ) {
        val maxWideFraction = when {
            isFoldable -> 0.55f
            isTablet   -> 0.60f
            else       -> 1.00f   
        }
        val maxWideWidth  = ((screenWidth * maxWideFraction) - (margin * 2f))
            .coerceAtLeast(baseMiniWidth)
        val effectiveBase = baseMiniWidth.coerceAtLeast(1f)
        val targetScale   = (maxWideWidth / effectiveBase).coerceAtLeast(1f)

        val isLargeScreen = isTablet || isFoldable
        val targetX = if (isLargeScreen) {
            val newMaxX = (screenWidth - (effectiveBase * targetScale) - margin)
                .coerceAtLeast(margin)
            offsetX.value.coerceIn(margin, newMaxX)
        } else {
            ((screenWidth - (effectiveBase * targetScale)) / 2f).coerceAtLeast(margin)
        }

        scope.launch {
            launch {
                miniSizeScale.animateTo(
                    targetScale,
                    miniResizeSpring()
                )
            }
            launch {
                offsetX.animateTo(
                    targetX,
                    miniResizeSpring()
                )
            }
        }
    }

    fun collapse() {
        scope.launch {
            val anim = playerExpandSpring()
            if (cachedTargetX == 0f && cachedTargetY == 0f) {
                expandFraction.snapTo(1f)
            } else {
                launch { expandFraction.animateTo(1f, anim) }
                launch { offsetX.animateTo(cachedTargetX, anim) }
                launch { offsetY.animateTo(cachedTargetY, anim) }
            }
            launch { miniSizeScale.animateTo(1f, anim) }
        }
    }

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
    val expandFraction = remember { Animatable(1f) }

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
    onExpandedPlayerBottomChanged: (Dp) -> Unit = {},
    videoAspectRatio: Float = 16f / 9f,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val config  = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet    = config.smallestScreenWidthDp >= 600
    val isFoldable  = remember(config) {
        config.smallestScreenWidthDp in 480..599
    }
    val isLargeScreen = isTablet || isFoldable

    var playerHeightFraction by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(videoAspectRatio) { playerHeightFraction = 1f }

    val statusBarHeight = WindowInsets.statusBars.getTop(density).toFloat()
    val systemLayoutDirection = LocalLayoutDirection.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val screenWidth  = constraints.maxWidth.toFloat()
            val screenHeight = constraints.maxHeight.toFloat()

            // 1. Immersive fullscreen
            val showImmersiveFullscreen = state.currentValue == PlayerSheetValue.Expanded &&
                    (isFullscreen || (isLandscape && !isTablet))

            // 2. Dimensions
            val isSplitLayout = isLandscape && isTablet

            val effectiveMiniScale: Float = when {
                isTablet -> when {
                    config.smallestScreenWidthDp >= 840 -> 0.32f
                    config.smallestScreenWidthDp >= 720 -> 0.35f
                    else                                -> 0.38f
                }
                isFoldable -> 0.42f
                else       -> miniPlayerScale
            }

            val baseMiniWidth = screenWidth * effectiveMiniScale
            val currentSizeScale by remember { derivedStateOf { state.miniSizeScale.value } }
            val margin = with(density) { 8.dp.toPx() }

            val maxWideFraction = when {
                isFoldable -> 0.55f
                isTablet   -> 0.60f
                else       -> 1.00f
            }
            val maxWideWidth = ((screenWidth * maxWideFraction) - (margin * 2f))
                .coerceAtLeast(baseMiniWidth)

            val miniWidth  = (baseMiniWidth * currentSizeScale).coerceAtMost(maxWideWidth)
            val miniHeight = miniWidth * (9f / 16f)
            val bottomNavPad = with(density) { bottomPadding.toPx() }
            val topBarPad    = with(density) { topPadding.toPx() }

            val isWideMode = currentSizeScale > 1.5f

            val phoneCenteredX = ((screenWidth - miniWidth) / 2f).coerceAtLeast(margin)

            val expandedVideoWidth  = if (isSplitLayout) screenWidth * 0.65f else screenWidth
            val baseVideoHeight     = expandedVideoWidth * (9f / 16f)
            val clampedAspect       = videoAspectRatio.coerceAtMost(2.0f)
            val expandedVideoHeight = expandedVideoWidth / clampedAspect
            val currentExpandedVideoHeight =
                if (expandedVideoHeight > baseVideoHeight) {
                    lerpFloat(baseVideoHeight, expandedVideoHeight, playerHeightFraction)
                } else {
                    expandedVideoHeight
                }
            val expandedPlayerBottom = with(density) {
                (statusBarHeight + currentExpandedVideoHeight).toDp()
            }

            SideEffect {
                onExpandedPlayerBottomChanged(expandedPlayerBottom)
            }

            val minX = margin
            val maxX = (screenWidth - miniWidth - margin).coerceAtLeast(margin)
            val minY = statusBarHeight + topBarPad + margin
            val maxY = (screenHeight - miniHeight - bottomNavPad - margin).coerceAtLeast(minY)

            LaunchedEffect(minX, maxX, minY, maxY) {
                state.offsetX.updateBounds(lowerBound = minX, upperBound = maxX)
                state.offsetY.updateBounds(lowerBound = minY, upperBound = maxY)
            }

            val targetMiniX = when {
                isWideMode && !isLargeScreen -> phoneCenteredX
                isWideMode && isLargeScreen  ->
                    state.offsetX.value.coerceIn(minX, maxX)
                else -> when (state.corner) {
                    MiniPlayerCorner.TopLeft,
                    MiniPlayerCorner.BottomLeft  -> minX
                    MiniPlayerCorner.TopRight,
                    MiniPlayerCorner.BottomRight -> maxX
                }
            }
            val targetMiniY = when (state.corner) {
                MiniPlayerCorner.TopLeft,
                MiniPlayerCorner.TopRight    -> minY
                MiniPlayerCorner.BottomLeft,
                MiniPlayerCorner.BottomRight -> maxY
            }

            SideEffect {
                state.cachedTargetX = targetMiniX
                state.cachedTargetY = targetMiniY
            }

            LaunchedEffect(
                state.expandFraction.targetValue,
                targetMiniX, targetMiniY,
                isWideMode, isLargeScreen, phoneCenteredX
            ) {
                if (state.expandFraction.targetValue > 0.5f && !state.isDragging) {
                    kotlinx.coroutines.delay(50)
                    if (state.isDragging) return@LaunchedEffect
                    if (isWideMode && !isLargeScreen) {
                        launch {
                            state.offsetX.animateTo(
                                phoneCenteredX,
                                miniSnapSpring()
                            )
                        }
                    } else if (isWideMode && isLargeScreen) {
                        val clampedX = state.offsetX.value.coerceIn(minX, maxX)
                        if (kotlin.math.abs(state.offsetX.value - clampedX) > 1f) {
                            launch {
                                state.offsetX.animateTo(
                                    clampedX,
                                    miniSnapSpring()
                                )
                            }
                        }
                    } else {
                        val needsSnap = state.offsetX.value == 0f &&
                            state.offsetY.value == 0f &&
                            targetMiniX > 0f && targetMiniY > 0f
                        if (needsSnap) {
                            state.offsetX.snapTo(targetMiniX)
                            state.offsetY.snapTo(targetMiniY)
                        } else {
                            launch {
                                state.offsetX.animateTo(
                                    targetMiniX,
                                    miniSnapSpring()
                                )
                            }
                            launch {
                                state.offsetY.animateTo(
                                    targetMiniY,
                                    miniSnapSpring()
                                )
                            }
                        }
                    }
                }
            }

            // 3. Nested scroll
            val nestedScrollConnection = remember(expandedVideoHeight, baseVideoHeight) {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        val delta       = available.y
                        val playerDelta = expandedVideoHeight - baseVideoHeight
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
                        val delta       = available.y
                        val playerDelta = expandedVideoHeight - baseVideoHeight
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

            // 4. Immersive fullscreen background
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                }
            }

            // 5. Background scrim 
            val expandedScrimAlpha by remember {
                derivedStateOf { (1f - state.expandFraction.value).coerceIn(0f, 1f) }
            }
            if (!showImmersiveFullscreen && expandedScrimAlpha > 0f && !state.isInlineMode) {
                Box(modifier = Modifier.fillMaxSize().alpha(expandedScrimAlpha)) {
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

            // 6. Body content 
            val bodyAlpha by remember {
                derivedStateOf { (1f - state.expandFraction.value * 1.25f).coerceIn(0f, 1f) }
            }
            if (!showImmersiveFullscreen && bodyAlpha > 0f && !state.isInlineMode) {
                val videoHeightPlaceholder =
                    if (isSplitLayout) with(density) { currentExpandedVideoHeight.toDp() } else 0.dp
                val bodyPaddingTop =
                    if (isSplitLayout) statusBarHeight else currentExpandedVideoHeight + statusBarHeight

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
                }
            }

            //  7. Video player box 
            val _minX            = rememberUpdatedState(minX)
            val _maxX            = rememberUpdatedState(maxX)
            val _minY            = rememberUpdatedState(minY)
            val _maxY            = rememberUpdatedState(maxY)
            val _statusBarH      = rememberUpdatedState(statusBarHeight)
            val _targetMiniX     = rememberUpdatedState(targetMiniX)
            val _targetMiniY     = rememberUpdatedState(targetMiniY)
            val _screenWidth     = rememberUpdatedState(screenWidth)
            val _miniWidth       = rememberUpdatedState(miniWidth)
            val _margin          = rememberUpdatedState(margin)
            val _phoneCenteredX  = rememberUpdatedState(phoneCenteredX)
            val _tapToExpand     = rememberUpdatedState(tapToExpand)
            val _onFullscreenGesture = rememberUpdatedState(onFullscreenGesture)
            val _isLandscape     = rememberUpdatedState(isLandscape)
            val _isFullscreen    = rememberUpdatedState(isFullscreen)
            val _miniPlayerScale = rememberUpdatedState(effectiveMiniScale)
            val _baseMiniWidth   = rememberUpdatedState(baseMiniWidth)
            val _isTablet        = rememberUpdatedState(isTablet)
            val _isFoldable      = rememberUpdatedState(isFoldable)
            val _isLargeScreen   = rememberUpdatedState(isLargeScreen)
            val _maxWideWidth    = rememberUpdatedState(maxWideWidth)

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = if (showImmersiveFullscreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .layout { measurable, constraints ->
                                val fraction = state.expandFraction.value
                                val targetW =
                                    lerpFloat(expandedVideoWidth, miniWidth, fraction).toInt()
                                        .coerceIn(1, constraints.maxWidth)
                                val targetH =
                                    lerpFloat(currentExpandedVideoHeight, miniHeight, fraction).toInt()
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
                                translationX =
                                    lerpFloat(0f, state.offsetX.value, fraction)
                                translationY =
                                    lerpFloat(statusBarHeight, state.offsetY.value, fraction)
                                val miniScale =
                                    if (fraction > 0.6f) state.dragScale.value else 1f
                                scaleX = miniScale
                                scaleY = miniScale
                                shadowElevation =
                                    if (fraction > 0.95f) with(density) { 8.dp.toPx() } else 0f
                                shape = RoundedCornerShape(
                                    if (fraction > 0.1f) 12.dp else 0.dp
                                )
                                clip = true
                            }
                            .background(Color.Black)
                            //  Pinch-to-resize 
                            .pointerInput("pinch") {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    val evt = awaitPointerEvent(
                                        androidx.compose.ui.input.pointer.PointerEventPass.Main
                                    )
                                    val pressed = evt.changes.filter { it.pressed }
                                    if (pressed.size < 2) return@awaitEachGesture
                                    if (state.expandFraction.value < 0.8f) return@awaitEachGesture

                                    val ptr1Id      = pressed[0].id
                                    val ptr2Id      = pressed[1].id
                                    val initialDist =
                                        (pressed[0].position - pressed[1].position)
                                            .getDistance().coerceAtLeast(1f)
                                    val startScale  = state.miniSizeScale.value
                                    val wideCapWidth = _maxWideWidth.value
                                    val maxScale =
                                        (wideCapWidth / _baseMiniWidth.value).coerceAtLeast(1f)
                                    var resizeSnapJob: Job? = null

                                    while (true) {
                                        val e  = awaitPointerEvent(
                                            androidx.compose.ui.input.pointer.PointerEventPass.Main
                                        )
                                        val p1 =
                                            e.changes.firstOrNull { it.id == ptr1Id } ?: break
                                        val p2 =
                                            e.changes.firstOrNull { it.id == ptr2Id } ?: break
                                        if (!p1.pressed || !p2.pressed) {
                                            resizeSnapJob?.cancel()
                                            val targetScale =
                                                if (state.miniSizeScale.value > 1.5f) maxScale
                                                else 1f
                                            state.scope.launch {
                                                state.miniSizeScale.animateTo(
                                                    targetScale,
                                                    miniResizeSpring()
                                                )
                                                if (targetScale <= 1f) {
                                                    launch {
                                                        state.offsetX.animateTo(
                                                            state.cachedTargetX,
                                                            miniResizeSpring()
                                                        )
                                                        state.offsetY.animateTo(
                                                            state.cachedTargetY,
                                                            miniResizeSpring()
                                                        )
                                                    }
                                                } else {
                                                    if (_isLargeScreen.value) {
                                                        val newMiniW =
                                                            (_baseMiniWidth.value * targetScale)
                                                                .coerceAtMost(wideCapWidth)
                                                        val newMaxX =
                                                            (_screenWidth.value - newMiniW - _margin.value)
                                                                .coerceAtLeast(_margin.value)
                                                        val clampedX = state.offsetX.value
                                                            .coerceIn(_margin.value, newMaxX)
                                                        launch {
                                                            state.offsetX.animateTo(
                                                                clampedX,
                                                                miniResizeSpring()
                                                            )
                                                        }
                                                    } else {
                                                        launch {
                                                            state.offsetX.animateTo(
                                                                _phoneCenteredX.value,
                                                                miniResizeSpring()
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            break
                                        }
                                        p1.consume(); p2.consume()
                                        val currentDist  =
                                            (p1.position - p2.position).getDistance()
                                        val gestureScale = currentDist / initialDist
                                        val newScale     =
                                            (startScale * gestureScale).coerceIn(1f, maxScale)
                                        resizeSnapJob?.cancel()
                                        resizeSnapJob = state.scope.launch {
                                            state.miniSizeScale.snapTo(newScale)
                                            val newMiniW =
                                                (_baseMiniWidth.value * newScale)
                                                    .coerceAtMost(wideCapWidth)
                                            val newMiniH = newMiniW * (9f / 16f)
                                            val newMaxX  =
                                                (_screenWidth.value - newMiniW - _margin.value)
                                                    .coerceAtLeast(_margin.value)
                                            val newMaxY  =
                                                (screenHeight - newMiniH - bottomNavPad - _margin.value)
                                                    .coerceAtLeast(minY)
                                            val clampedX = when {
                                                _isLargeScreen.value ->
                                                    state.offsetX.value.coerceIn(_margin.value, newMaxX)
                                                newScale > 1.5f ->
                                                    _phoneCenteredX.value
                                                else ->
                                                    state.offsetX.value.coerceIn(minX, newMaxX)
                                            }
                                            val clampedY =
                                                state.offsetY.value.coerceIn(minY, newMaxY)
                                            state.offsetX.snapTo(clampedX)
                                            state.offsetY.snapTo(clampedY)
                                        }
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                val velocityTracker = VelocityTracker()
                                var snapJob: Job? = null
                                var lastTapTime = 0L
                                var singleTapJob: Job? = null
                                awaitEachGesture {
                                    val minX        = _minX.value
                                    val maxX        = _maxX.value
                                    val minY        = _minY.value
                                    val maxY        = _maxY.value
                                    val statusBarH  = _statusBarH.value
                                    val targetMiniX = _targetMiniX.value
                                    val targetMiniY = _targetMiniY.value
                                    val screenWidth = _screenWidth.value
                                    val miniWidth   = _miniWidth.value
                                    val margin      = _margin.value
                                    val isLargeScreen = _isLargeScreen.value
                                    val phoneCenteredX = _phoneCenteredX.value

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
                                            state.dragScale.animateTo(
                                                0.97f,
                                                spring(dampingRatio = 0.7f, stiffness = 600f)
                                            )
                                        }
                                    }

                                    var dragPointerId     = down.id
                                    var hasCrossedSlop    = !isCollapseDrag
                                    var startDragY        = 0f
                                    var detectedDirection = 0

                                    if (isCollapseDrag) {
                                        val slop = viewConfiguration.touchSlop
                                        while (!hasCrossedSlop) {
                                            val event = awaitPointerEvent(
                                                androidx.compose.ui.input.pointer.PointerEventPass.Main
                                            )
                                            val change = event.changes
                                                .firstOrNull { it.id == dragPointerId }
                                            if (change == null || !change.pressed ||
                                                change.isConsumed) break
                                            velocityTracker.addPosition(
                                                change.uptimeMillis, change.position
                                            )
                                            val delta = change.position - down.position
                                            if (delta.y > slop &&
                                                delta.y > kotlin.math.abs(delta.x)) {
                                                hasCrossedSlop    = true
                                                startDragY        = delta.y
                                                detectedDirection = 1
                                                change.consume()
                                            } else if (canSwipeToFullscreen &&
                                                delta.y < -slop &&
                                                kotlin.math.abs(delta.y) >
                                                    kotlin.math.abs(delta.x)
                                            ) {
                                                hasCrossedSlop    = true
                                                startDragY        = delta.y
                                                detectedDirection = -1
                                                change.consume()
                                            } else if (kotlin.math.abs(delta.x) > slop) {
                                                break
                                            }
                                        }
                                    }

                                    state.isDragging = true
                                    var cumulativeDragY = startDragY
                                    var totalMovement   = 0f
                                    val startFraction   = state.expandFraction.value
                                    val collapseTravel  =
                                        (targetMiniY - statusBarH).coerceAtLeast(1f)
                                    var totalUpwardDrag = 0f

                                    if (hasCrossedSlop) {
                                        try {
                                            drag(dragPointerId) { change ->
                                                val delta = change.positionChange()
                                                totalMovement += delta.getDistance()
                                                velocityTracker.addPosition(
                                                    change.uptimeMillis, change.position
                                                )

                                                if (isCollapseDrag && detectedDirection == 1) {
                                                    change.consume()
                                                    cumulativeDragY += delta.y
                                                    val rawFraction =
                                                        (startFraction +
                                                            cumulativeDragY / collapseTravel)
                                                            .coerceIn(0f, 1f)
                                                    snapJob?.cancel()
                                                    snapJob = state.scope.launch {
                                                        state.expandFraction.snapTo(rawFraction)
                                                    }
                                                } else if (isCollapseDrag &&
                                                    detectedDirection == -1) {
                                                    change.consume()
                                                    totalUpwardDrag += -delta.y
                                                } else if (isMiniDrag) {
                                                    if (totalMovement >
                                                        viewConfiguration.touchSlop * 0.5f) {
                                                        change.consume()
                                                        val rawY     = state.offsetY.value + delta.y
                                                        val clampedY = rawY.coerceIn(minY, maxY)

                                                        when {
                                                            state.isInlineMode && !isLargeScreen -> {
                                                                snapJob?.cancel()
                                                                snapJob = state.scope.launch {
                                                                    state.offsetY.snapTo(clampedY)
                                                                    state.offsetX.snapTo(phoneCenteredX)
                                                                }
                                                            }
                                                            else -> {
                                                                val rawX     =
                                                                    state.offsetX.value + delta.x
                                                                val clampedX =
                                                                    rawX.coerceIn(minX, maxX)
                                                                snapJob?.cancel()
                                                                snapJob = state.scope.launch {
                                                                    state.offsetX.snapTo(clampedX)
                                                                    state.offsetY.snapTo(clampedY)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } finally {
                                            snapJob?.cancel(); snapJob = null
                                            state.isDragging = false
                                            state.scope.launch {
                                                state.dragScale.animateTo(
                                                    1f,
                                                    spring(dampingRatio = 0.55f, stiffness = 500f)
                                                )
                                            }
                                        }
                                    } else {
                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent(
                                                    androidx.compose.ui.input.pointer.PointerEventPass.Main
                                                )
                                                if (event.changes.all { !it.pressed }) break
                                            }
                                        } finally {
                                            state.isDragging = false
                                        }
                                    }

                                    if (isMiniDrag && totalMovement < 24f) {
                                        if (!downConsumedByChild && _tapToExpand.value) {
                                            val now = down.uptimeMillis
                                            if (now - lastTapTime < 300L) {
                                                singleTapJob?.cancel()
                                                lastTapTime = 0L
                                                if (state.isInlineMode) {
                                                    state.collapse()
                                                } else {
                                                    state.expandWide(
                                                        miniPlayerScale = _miniPlayerScale.value,
                                                        screenWidth     = screenWidth,
                                                        margin          = margin,
                                                        baseMiniWidth   = _baseMiniWidth.value,
                                                        isTablet        = _isTablet.value,
                                                        isFoldable      = _isFoldable.value
                                                    )
                                                }
                                            } else {
                                                lastTapTime  = now
                                                singleTapJob = state.scope.launch {
                                                    kotlinx.coroutines.delay(300L)
                                                    state.expand()
                                                }
                                            }
                                        }
                                        return@awaitEachGesture
                                    }

                                    if (isCollapseDrag && detectedDirection == -1) {
                                        val velY = velocityTracker.calculateVelocity().y
                                        if (totalUpwardDrag > 80f || velY < -800f) {
                                            _onFullscreenGesture.value?.invoke()
                                        }
                                        return@awaitEachGesture
                                    }

                                    if (isCollapseDrag) {
                                        val velY = velocityTracker.calculateVelocity().y
                                        val shouldCollapse =
                                            state.expandFraction.value > 0.1f ||
                                            velY > 300f ||
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
                                        MiniPlayerCorner.TopLeft,
                                        MiniPlayerCorner.BottomLeft  -> minX
                                        MiniPlayerCorner.TopRight,
                                        MiniPlayerCorner.BottomRight -> maxX
                                    }
                                    val originY = when (state.corner) {
                                        MiniPlayerCorner.TopLeft,
                                        MiniPlayerCorner.TopRight    -> minY
                                        MiniPlayerCorner.BottomLeft,
                                        MiniPlayerCorner.BottomRight -> maxY
                                    }

                                    val deltaFromOriginX = currentX - originX
                                    val deltaFromOriginY = currentY - originY
                                    val totalTravelX = (maxX - minX).coerceAtLeast(1f)
                                    val totalTravelY = (maxY - minY).coerceAtLeast(1f)
                                    val switchThresholdX = totalTravelX * 0.15f
                                    val switchThresholdY = totalTravelY * 0.15f
                                    val projectedDeltaX  = deltaFromOriginX + velX * 0.3f
                                    val projectedDeltaY  = deltaFromOriginY + velY * 0.3f

                                    val wasLeft =
                                        state.corner == MiniPlayerCorner.TopLeft ||
                                        state.corner == MiniPlayerCorner.BottomLeft
                                    val wasTop =
                                        state.corner == MiniPlayerCorner.TopLeft ||
                                        state.corner == MiniPlayerCorner.TopRight

                                    val goLeft = when {
                                        abs(velX) > 400f &&
                                            abs(velX) > abs(velY) * 0.8f -> velX < 0
                                        wasLeft &&
                                            projectedDeltaX > switchThresholdX  -> false
                                        !wasLeft &&
                                            projectedDeltaX < -switchThresholdX -> true
                                        else -> wasLeft
                                    }
                                    val goTop = when {
                                        abs(velY) > 400f &&
                                            abs(velY) > abs(velX) * 0.8f -> velY < 0
                                        wasTop &&
                                            projectedDeltaY > switchThresholdY  -> false
                                        !wasTop &&
                                            projectedDeltaY < -switchThresholdY -> true
                                        else -> wasTop
                                    }

                                    val newCorner = when {
                                        goLeft && goTop   -> MiniPlayerCorner.TopLeft
                                        goLeft && !goTop  -> MiniPlayerCorner.BottomLeft
                                        !goLeft && goTop  -> MiniPlayerCorner.TopRight
                                        else              -> MiniPlayerCorner.BottomRight
                                    }

                                    if (state.isInlineMode) {
                                        state.corner = newCorner
                                        if (isLargeScreen) {
                                            state.scope.launch {
                                                launch {
                                                    state.offsetX.animateTo(
                                                        if (goLeft) minX else maxX,
                                                        miniSnapSpring(),
                                                        initialVelocity = velX
                                                    )
                                                }
                                                launch {
                                                    state.offsetY.animateTo(
                                                        if (goTop) minY else maxY,
                                                        miniSnapSpring(),
                                                        initialVelocity = velY
                                                    )
                                                }
                                            }
                                        } else {
                                            state.scope.launch {
                                                launch {
                                                    state.offsetX.animateTo(
                                                        phoneCenteredX,
                                                        miniSnapSpring()
                                                    )
                                                }
                                                launch {
                                                    state.offsetY.animateTo(
                                                        if (goTop) minY else maxY,
                                                        miniSnapSpring(),
                                                        initialVelocity = velY
                                                    )
                                                }
                                            }
                                        }
                                        return@awaitEachGesture
                                    }

                                    val centerX         = (minX + maxX) / 2f
                                    val isNearRightEdge = currentX > centerX
                                    val isNearLeftEdge  = currentX < centerX
                                    val isHorizontalFling = abs(velX) > abs(velY) * 3f
                                    val canDismissRight =
                                        !goLeft && velX > 2000f && isNearRightEdge
                                    val canDismissLeft  =
                                        goLeft && velX < -2000f && isNearLeftEdge

                                    if (isHorizontalFling &&
                                        (canDismissRight || canDismissLeft)) {
                                        val offScreenX =
                                            if (!goLeft) screenWidth + miniWidth
                                            else -(miniWidth + margin)
                                        state.scope.launch {
                                            launch {
                                                state.offsetX.animateTo(
                                                    offScreenX,
                                                    miniDismissSpring(),
                                                    initialVelocity = velX
                                                )
                                            }
                                            kotlinx.coroutines.delay(200)
                                            onDismiss()
                                        }
                                    } else {
                                        state.corner = newCorner
                                        state.scope.launch {
                                            launch {
                                                state.offsetX.animateTo(
                                                    if (goLeft) minX else maxX,
                                                    miniSnapSpring(),
                                                    initialVelocity = velX
                                                )
                                            }
                                            launch {
                                                state.offsetY.animateTo(
                                                    if (goTop) minY else maxY,
                                                    miniSnapSpring(),
                                                    initialVelocity = velY
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                    }
                ) {
                    videoContent(Modifier.fillMaxSize())

                    val fraction by remember { derivedStateOf { state.expandFraction.value } }
                    if (!showImmersiveFullscreen && fraction > 0.6f) {
                        val controlsProgress = ((fraction - 0.6f) / 0.25f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = controlsProgress
                                    scaleX = lerpFloat(0.96f, 1f, controlsProgress)
                                    scaleY = lerpFloat(0.96f, 1f, controlsProgress)
                                }
                        ) {
                            miniControls(fraction)
                        }
                    }

                    if (!showImmersiveFullscreen && fraction > 0.6f) {
                        val progressAlpha = ((fraction - 0.72f) / 0.18f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.dp)
                                .alpha(progressAlpha),
                            color = Color.Red,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        }
    }
}
