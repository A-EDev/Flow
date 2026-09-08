package io.github.aedev.flow.ui.components.videoplayer

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import coil3.compose.AsyncImage
import io.github.aedev.flow.player.sanitizeDisplayAspectRatio
import io.github.aedev.flow.ui.components.videoplayer.motion.BODY_CONTENT_MAX_EXPAND_FRACTION
import io.github.aedev.flow.ui.components.videoplayer.motion.DraggablePlayerGestureHandler
import io.github.aedev.flow.ui.components.videoplayer.motion.DraggablePlayerGestureMetrics
import io.github.aedev.flow.ui.components.videoplayer.motion.MINI_RESNAP_DEBOUNCE_MS
import io.github.aedev.flow.ui.components.videoplayer.motion.MiniPlayerPinchGestureHandler
import io.github.aedev.flow.ui.components.videoplayer.motion.cornerTargetX
import io.github.aedev.flow.ui.components.videoplayer.motion.cornerTargetY
import io.github.aedev.flow.ui.components.videoplayer.motion.draggablePlayerGestures
import io.github.aedev.flow.ui.components.videoplayer.motion.lerpClamped
import io.github.aedev.flow.ui.components.videoplayer.motion.miniPlayerPinchGesture
import io.github.aedev.flow.ui.components.videoplayer.motion.miniPlayerTapGestures
import io.github.aedev.flow.ui.components.videoplayer.motion.miniSnapSpringSpec
import io.github.aedev.flow.ui.components.videoplayer.motion.portraitFullscreenSettleSpec
import io.github.aedev.flow.ui.theme.PlayerGround
import io.github.aedev.flow.ui.theme.PlayerMiniProgress
import io.github.aedev.flow.ui.theme.PlayerScrimImmersiveBackdrop
import io.github.aedev.flow.ui.utils.TABLET_SMALLEST_WIDTH_DP
import io.github.aedev.flow.ui.utils.isTabletFormFactor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Corner radius of the floating mini player, authored pre-scale: the video box is clipped by the
 * PlayerView outline at `radius / miniVisualScale` so the morph's graphicsLayer scale brings it
 * back to this on screen.
 */
const val MINI_PLAYER_CORNER_RADIUS_DP = 12f

/**
 * Publishes the expanded player's bottom edge to the host from its own recomposition scope.
 * The host sizes media sheets from it, so it must be state, but rounding to whole dp keeps the
 * host from recomposing on every pixel of the adaptive-height shrink.
 */
@Composable
private fun ReportExpandedPlayerBottom(
    statusBarHeight: Float,
    videoHeightProvider: () -> Float,
    onChanged: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    val bottom by remember(statusBarHeight, density, videoHeightProvider) {
        derivedStateOf {
            with(density) { (statusBarHeight + videoHeightProvider()).toDp() }
                .value
                .roundToInt()
                .dp
        }
    }
    val currentOnChanged by rememberUpdatedState(onChanged)
    SideEffect { currentOnChanged(bottom) }
}

// ---------------------------------------------------------------------------
// Main composable
// ---------------------------------------------------------------------------

@Composable
fun DraggablePlayerLayout(
    state: PlayerDraggableState,
    videoContent: @Composable (Modifier) -> Unit,
    bodyContent: @Composable (alpha: () -> Float, videoHeightPx: () -> Float) -> Unit,
    miniControls: @Composable (() -> Float) -> Unit,
    progress: () -> Float,
    isFullscreen: Boolean,
    thumbnailUrl: String? = null,
    topPadding: Dp = 56.dp,
    bottomPadding: Dp = 0.dp,
    miniPlayerScale: Float = 0.45f,
    tapToExpand: Boolean = true,
    onDismiss: () -> Unit = {},
    onCollapseGesture: (() -> Unit)? = null,
    onFullscreenGesture: (() -> Unit)? = null,
    onEnterPortraitFullscreen: (() -> Unit)? = null,
    onExpandedPlayerBottomChanged: (Dp) -> Unit = {},
    videoAspectRatio: Float = 16f / 9f,
    expandedPlayerHeightFractionOverride: (() -> Float)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = config.isTabletFormFactor
    val isFoldable =
        remember(config) {
            config.smallestScreenWidthDp in 480 until TABLET_SMALLEST_WIDTH_DP
        }
    val isLargeScreen = isTablet || isFoldable

    var playerHeightFraction by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(videoAspectRatio) { playerHeightFraction = 1f }

    var portraitFsFraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen && portraitFsFraction > 0f) {
            androidx.compose.animation.core.animate(
                initialValue = portraitFsFraction,
                targetValue = 0f,
                animationSpec = portraitFullscreenSettleSpec,
            ) { value, _ -> portraitFsFraction = value }
        }
    }

    val statusBarHeight = WindowInsets.statusBars.getTop(density).toFloat()
    val systemLayoutDirection = LocalLayoutDirection.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val screenWidth = constraints.maxWidth.toFloat()
            val screenHeight = constraints.maxHeight.toFloat()

            // 1. Immersive fullscreen
            val showImmersiveFullscreen =
                state.currentValue == PlayerSheetValue.Expanded &&
                    (isFullscreen || (isLandscape && !isTablet))

            // 2. Dimensions
            val isSplitLayout = isLandscape && isTablet

            val effectiveMiniScale: Float =
                when {
                    isTablet -> {
                        when {
                            config.smallestScreenWidthDp >= 840 -> 0.32f
                            config.smallestScreenWidthDp >= 720 -> 0.35f
                            else -> 0.38f
                        }
                    }

                    isFoldable -> {
                        0.42f
                    }

                    else -> {
                        miniPlayerScale
                    }
                }

            val baseMiniWidth = screenWidth * effectiveMiniScale
            val currentSizeScale = state.miniSizeScale.targetValue
            val margin = with(density) { 8.dp.toPx() }

            val maxWideFraction =
                when {
                    isFoldable -> 0.55f
                    isTablet -> 0.60f
                    else -> 1.00f
                }
            val maxWideWidth =
                ((screenWidth * maxWideFraction) - (margin * 2f))
                    .coerceAtLeast(baseMiniWidth)

            val clampedAspect = sanitizeDisplayAspectRatio(videoAspectRatio)

            fun miniBoxWidth(envelopeSide: Float) = if (clampedAspect >= 1f) envelopeSide else envelopeSide * clampedAspect

            val miniWidth = miniBoxWidth(baseMiniWidth * currentSizeScale).coerceAtMost(maxWideWidth)
            val miniHeight = miniWidth / clampedAspect
            val bottomNavPad = with(density) { bottomPadding.toPx() }
            val topBarPad = with(density) { topPadding.toPx() }

            val isWideMode = currentSizeScale > 1.5f

            val expandedVideoWidth = if (isSplitLayout) screenWidth * 0.65f else screenWidth
            val baseVideoHeight = expandedVideoWidth * (9f / 16f)
            val expandedVideoHeight = expandedVideoWidth / clampedAspect
            val heightFractionOverrideState = rememberUpdatedState(expandedPlayerHeightFractionOverride)
            // Read in the layout phase only: the fraction changes on every nested-scroll delta and
            // every media-sheet drag frame, and a composition read here recomposed this whole tree.
            val currentExpandedVideoHeightProvider =
                remember(baseVideoHeight, expandedVideoHeight) {
                    {
                        if (expandedVideoHeight > baseVideoHeight) {
                            val fraction =
                                heightFractionOverrideState.value?.invoke()?.coerceIn(0f, 1f)
                                    ?: playerHeightFraction
                            lerpClamped(baseVideoHeight, expandedVideoHeight, fraction)
                        } else {
                            expandedVideoHeight
                        }
                    }
                }
            ReportExpandedPlayerBottom(
                statusBarHeight = statusBarHeight,
                videoHeightProvider = currentExpandedVideoHeightProvider,
                onChanged = onExpandedPlayerBottomChanged,
            )

            val visualMiniScale =
                (miniWidth / expandedVideoWidth.coerceAtLeast(1f))
                    .coerceIn(0.01f, 1f)

            SideEffect {
                state.miniVisualScale = visualMiniScale
            }

            val isCollapsedTarget by remember {
                derivedStateOf { state.expandFraction.targetValue > 0.5f }
            }
            LaunchedEffect(isCollapsedTarget) {
                if (isCollapsedTarget) playerHeightFraction = 1f
            }

            val minX = margin
            val maxX = (screenWidth - miniWidth - margin).coerceAtLeast(margin)
            val minY = statusBarHeight + topBarPad + margin
            val maxY = (screenHeight - miniHeight - bottomNavPad - margin).coerceAtLeast(minY)

            val normalMiniWidth = miniBoxWidth(baseMiniWidth)
            val normalMiniHeight = normalMiniWidth / clampedAspect
            val normalMaxX = (screenWidth - normalMiniWidth - margin).coerceAtLeast(margin)
            val normalMaxY = (screenHeight - normalMiniHeight - bottomNavPad - margin).coerceAtLeast(minY)
            val normalTargetX = cornerTargetX(state.corner, minX = margin, maxX = normalMaxX)
            val normalTargetY = cornerTargetY(state.corner, minY = minY, maxY = normalMaxY)
            val stableWideWidth = miniBoxWidth(maxWideWidth)
            val stablePhoneCenteredX = ((screenWidth - stableWideWidth) / 2f).coerceAtLeast(margin)
            val stableWideHeight = stableWideWidth / clampedAspect
            val stableWideMaxY = (screenHeight - stableWideHeight - bottomNavPad - margin).coerceAtLeast(minY)
            val stableWideTargetY = cornerTargetY(state.corner, minY = minY, maxY = stableWideMaxY)

            val targetMiniX =
                when {
                    state.isShrinkingToCorner -> normalTargetX

                    isWideMode && !isLargeScreen -> {
                        stablePhoneCenteredX
                    }

                    isWideMode && isLargeScreen -> {
                        state.cachedTargetX.takeIf { it != 0f } ?: state.offsetX.value.coerceIn(minX, maxX)
                    }

                    else -> {
                        normalTargetX
                    }
                }
            val targetMiniY =
                when {
                    isWideMode && !state.isShrinkingToCorner -> stableWideTargetY
                    else -> normalTargetY
                }

            SideEffect {
                state.cachedTargetX = normalTargetX
                state.cachedTargetY = normalTargetY
            }

            LaunchedEffect(
                isCollapsedTarget,
                targetMiniX,
                targetMiniY,
                isWideMode,
                isLargeScreen,
            ) {
                if (state.expandFraction.targetValue > 0.5f && !state.isDragging) {
                    kotlinx.coroutines.delay(MINI_RESNAP_DEBOUNCE_MS)
                    if (state.isDragging) return@LaunchedEffect
                    if (isWideMode && !isLargeScreen) {
                        state.motion.movePosition {
                            launch { state.offsetX.animateTo(stablePhoneCenteredX, miniSnapSpringSpec) }
                            launch { state.offsetY.animateTo(stableWideTargetY, miniSnapSpringSpec) }
                        }
                    } else if (isWideMode && isLargeScreen) {
                        val clampedX = state.offsetX.value.coerceIn(minX, maxX)
                        val clampedY = state.offsetY.value.coerceIn(minY, stableWideMaxY)
                        val moveX = kotlin.math.abs(state.offsetX.value - clampedX) > 1f
                        val moveY = kotlin.math.abs(state.offsetY.value - clampedY) > 1f
                        if (moveX || moveY) {
                            state.motion.movePosition {
                                if (moveX) launch { state.offsetX.animateTo(clampedX, miniSnapSpringSpec) }
                                if (moveY) launch { state.offsetY.animateTo(clampedY, miniSnapSpringSpec) }
                            }
                        }
                    } else {
                        val needsSnap =
                            state.offsetX.value == 0f &&
                                state.offsetY.value == 0f &&
                                targetMiniX > 0f && targetMiniY > 0f
                        if (needsSnap) {
                            state.motion.snapPosition(x = targetMiniX, y = targetMiniY)
                        } else {
                            state.motion.movePosition {
                                launch { state.offsetX.animateTo(targetMiniX, miniSnapSpringSpec) }
                                launch { state.offsetY.animateTo(targetMiniY, miniSnapSpringSpec) }
                            }
                        }
                    }
                }
            }

            // 3. Nested scroll
            val portraitFsTravel = (screenHeight - expandedVideoHeight).coerceAtLeast(1f)
            val portraitFsEnabled =
                !isLandscape && !isTablet && !isFullscreen &&
                    onEnterPortraitFullscreen != null
            val portraitFsActivationPx = with(density) { 28.dp.toPx() }
            val portraitFsTravelState = rememberUpdatedState(portraitFsTravel)
            val portraitFsEnabledState = rememberUpdatedState(portraitFsEnabled)
            val portraitFsActivationState = rememberUpdatedState(portraitFsActivationPx)
            val onEnterPortraitFsState = rememberUpdatedState(onEnterPortraitFullscreen)

            val nestedScrollConnection =
                remember(expandedVideoHeight, baseVideoHeight) {
                    object : NestedScrollConnection {
                        var listScrolledThisGesture = false
                        var pullAccum = 0f

                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {
                            val delta = available.y
                            if (source == NestedScrollSource.UserInput &&
                                delta < 0f && portraitFsFraction > 0f && portraitFsEnabledState.value
                            ) {
                                val travel = portraitFsTravelState.value
                                val maxConsumable = portraitFsFraction * travel
                                val consumed = maxOf(delta, -maxConsumable)
                                portraitFsFraction =
                                    (portraitFsFraction + consumed / travel).coerceIn(0f, 1f)
                                return Offset(0f, consumed)
                            }
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
                            source: NestedScrollSource,
                        ): Offset {
                            if (consumed.y != 0f) listScrolledThisGesture = true
                            val delta = available.y
                            val playerDelta = expandedVideoHeight - baseVideoHeight
                            if (delta > 0 && playerHeightFraction < 1f && playerDelta > 1f) {
                                val maxConsumable = (1f - playerHeightFraction) * playerDelta
                                val consumable = minOf(delta, maxConsumable)
                                playerHeightFraction =
                                    (playerHeightFraction + consumable / playerDelta).coerceIn(0f, 1f)
                                return Offset(0f, consumable)
                            }
                            val canPull =
                                source == NestedScrollSource.UserInput &&
                                    !listScrolledThisGesture &&
                                    portraitFsEnabledState.value &&
                                    state.expandFraction.value < 0.05f
                            if (delta > 0f && portraitFsFraction < 1f && canPull) {
                                pullAccum += delta
                                val past = pullAccum - portraitFsActivationState.value
                                if (past <= 0f) return Offset(0f, delta)
                                val travel = portraitFsTravelState.value
                                val effective = minOf(delta, past)
                                val maxConsumable = (1f - portraitFsFraction) * travel
                                val consumable = minOf(effective, maxConsumable)
                                portraitFsFraction =
                                    (portraitFsFraction + consumable / travel).coerceIn(0f, 1f)
                                return Offset(0f, delta)
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                            val frac = portraitFsFraction
                            listScrolledThisGesture = false
                            pullAccum = 0f
                            if (frac <= 0f || frac >= 1f) return androidx.compose.ui.unit.Velocity.Zero
                            val shouldEnter = frac > 0.4f || available.y > 1400f
                            androidx.compose.animation.core.animate(
                                initialValue = frac,
                                targetValue = if (shouldEnter) 1f else 0f,
                                initialVelocity = available.y,
                                animationSpec = portraitFullscreenSettleSpec,
                            ) { value, _ -> portraitFsFraction = value }
                            if (shouldEnter) onEnterPortraitFsState.value?.invoke()
                            return available
                        }
                    }
                }

            // 4. Immersive fullscreen background
            if (showImmersiveFullscreen) {
                Box(modifier = Modifier.fillMaxSize().background(PlayerGround))
                if (!thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().blur(60.dp),
                        contentScale = ContentScale.Crop,
                        alpha = 0.65f,
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(PlayerScrimImmersiveBackdrop),
                    )
                }
            }

            val scrimVisible by remember {
                derivedStateOf { state.expandFraction.value < 0.999f }
            }
            val inlineMode by remember {
                derivedStateOf { state.miniSizeScale.value > 1.5f }
            }
            if (!showImmersiveFullscreen && scrimVisible && !inlineMode) {
                Box(
                    modifier =
                        Modifier.fillMaxSize().graphicsLayer {
                            alpha = (1f - state.expandFraction.value).coerceIn(0f, 1f)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(with(density) { statusBarHeight.toDp() })
                                .background(PlayerGround),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(top = with(density) { statusBarHeight.toDp() })
                                .background(MaterialTheme.colorScheme.background),
                    )
                }
            }

            if (!showImmersiveFullscreen) {
                val bodyAlphaProvider =
                    remember {
                        {
                            (1f - state.expandFraction.value / BODY_CONTENT_MAX_EXPAND_FRACTION)
                                .coerceIn(0f, 1f)
                        }
                    }
                val videoHeightPlaceholderProvider =
                    remember(isSplitLayout, currentExpandedVideoHeightProvider) {
                        if (isSplitLayout) currentExpandedVideoHeightProvider else ({ 0f })
                    }
                val bodyPaddingTopProvider =
                    remember(isSplitLayout, statusBarHeight, currentExpandedVideoHeightProvider) {
                        if (isSplitLayout) {
                            ({ statusBarHeight })
                        } else {
                            ({ currentExpandedVideoHeightProvider() + statusBarHeight })
                        }
                    }

                CompositionLocalProvider(LocalLayoutDirection provides systemLayoutDirection) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .layout { measurable, constraints ->
                                    val topPad = bodyPaddingTopProvider().roundToInt().coerceAtLeast(0)
                                    val placeable = measurable.measure(constraints.offset(vertical = -topPad))
                                    layout(constraints.maxWidth, constraints.maxHeight) {
                                        placeable.place(0, topPad)
                                    }
                                }.graphicsLayer {
                                    val pf = portraitFsFraction
                                    val fraction = state.expandFraction.value
                                    alpha = bodyAlphaProvider() * (1f - pf)
                                    translationY =
                                        if (fraction > 0.999f) {
                                            size.height
                                        } else {
                                            fraction * 80f + pf * screenHeight
                                        }
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                }.nestedScroll(nestedScrollConnection),
                    ) {
                        bodyContent(bodyAlphaProvider, videoHeightPlaceholderProvider)
                    }
                }
            }

            //  7. Video player box
            val gestureMetrics = remember(state) { DraggablePlayerGestureMetrics() }
            SideEffect {
                gestureMetrics.minX = minX
                gestureMetrics.maxX = maxX
                gestureMetrics.minY = minY
                gestureMetrics.maxY = maxY
                gestureMetrics.statusBarHeight = statusBarHeight
                gestureMetrics.targetMiniX = targetMiniX
                gestureMetrics.targetMiniY = targetMiniY
                gestureMetrics.screenWidth = screenWidth
                gestureMetrics.screenHeight = screenHeight
                gestureMetrics.miniWidth = miniWidth
                gestureMetrics.baseMiniWidth = baseMiniWidth
                gestureMetrics.maxWideWidth = maxWideWidth
                gestureMetrics.expandedVideoWidth = expandedVideoWidth
                gestureMetrics.clampedAspect = clampedAspect
                gestureMetrics.margin = margin
                gestureMetrics.bottomNavPad = bottomNavPad
                gestureMetrics.stablePhoneCenteredX = stablePhoneCenteredX
                gestureMetrics.isTablet = isTablet
                gestureMetrics.isFoldable = isFoldable
                gestureMetrics.isLargeScreen = isLargeScreen
                gestureMetrics.isLandscape = isLandscape
                gestureMetrics.isFullscreen = isFullscreen
                gestureMetrics.tapToExpand = tapToExpand
                gestureMetrics.onFullscreenGesture = onFullscreenGesture
                gestureMetrics.onCollapseGesture = onCollapseGesture
                gestureMetrics.onDismiss = onDismiss
            }
            val gestureHandler =
                remember(state, gestureMetrics) { DraggablePlayerGestureHandler(state, gestureMetrics) }
            val pinchHandler =
                remember(state, gestureMetrics) { MiniPlayerPinchGestureHandler(state, gestureMetrics) }
            val isMiniMode by remember(state) { derivedStateOf { state.expandFraction.value > 0.8f } }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier =
                        if (showImmersiveFullscreen) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .layout { measurable, constraints ->
                                    val grownHeight =
                                        lerpClamped(currentExpandedVideoHeightProvider(), screenHeight, portraitFsFraction)
                                    val targetW =
                                        expandedVideoWidth
                                            .toInt()
                                            .coerceIn(1, constraints.maxWidth.coerceAtLeast(1))
                                    val targetH =
                                        grownHeight
                                            .toInt()
                                            .coerceIn(1, constraints.maxHeight.coerceAtLeast(1))
                                    val placeable =
                                        measurable.measure(
                                            constraints.copy(
                                                minWidth = targetW,
                                                maxWidth = targetW,
                                                minHeight = targetH,
                                                maxHeight = targetH,
                                            ),
                                        )
                                    layout(targetW, targetH) { placeable.place(0, 0) }
                                }.graphicsLayer {
                                    val fraction = state.expandFraction.value
                                    val liveMiniWidth =
                                        miniBoxWidth(baseMiniWidth * state.miniSizeScale.value)
                                            .coerceAtMost(maxWideWidth)
                                    val visualScale =
                                        lerpClamped(
                                            1f,
                                            liveMiniWidth / expandedVideoWidth.coerceAtLeast(1f),
                                            fraction,
                                        )
                                    val drag =
                                        if (fraction > 0.6f) {
                                            state.dragScale.value
                                        } else {
                                            state.expandDragScale.value
                                        }
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    scaleX = visualScale * drag
                                    scaleY = visualScale * drag
                                    val windowW = expandedVideoWidth * visualScale
                                    val windowH = size.height * visualScale
                                    val expandedTopY = lerpClamped(statusBarHeight, 0f, portraitFsFraction)
                                    translationX =
                                        lerpClamped(0f, state.offsetX.value, fraction) +
                                        windowW * (1f - drag) / 2f
                                    translationY =
                                        lerpClamped(expandedTopY, state.offsetY.value, fraction) +
                                        windowH * (1f - drag) / 2f
                                    shadowElevation =
                                        if (fraction > 0.95f) {
                                            8.dp.toPx() / visualMiniScale
                                        } else {
                                            0f
                                        }
                                    shape =
                                        RoundedCornerShape(
                                            if (fraction > 0.1f) (MINI_PLAYER_CORNER_RADIUS_DP / visualMiniScale).dp else 0.dp,
                                        )
                                    clip = false
                                }.drawBehind {
                                    val fraction = state.expandFraction.value
                                    val r =
                                        if (fraction > 0.1f) {
                                            (MINI_PLAYER_CORNER_RADIUS_DP / visualMiniScale).dp.toPx()
                                        } else {
                                            0f
                                        }
                                    drawRoundRect(
                                        color = PlayerGround,
                                        cornerRadius = CornerRadius(r, r),
                                    )
                                }
                                // Outermost so it sees the drag handler consume a move and drop the tap.
                                .miniPlayerTapGestures(
                                    enabled = tapToExpand && isMiniMode,
                                    state = state,
                                    metrics = gestureMetrics,
                                ).miniPlayerPinchGesture(pinchHandler)
                                .draggablePlayerGestures(gestureHandler)
                        },
                ) {
                    videoContent(Modifier.fillMaxSize())

                    val miniControlsVisible by remember {
                        derivedStateOf { state.expandFraction.value > 0.6f }
                    }
                    val fractionProvider = remember { { state.expandFraction.value } }
                    if (!showImmersiveFullscreen && miniControlsVisible) {
                        val controlsScale = expandedVideoWidth / miniWidth.coerceAtLeast(1f)
                        val miniWidthDp = with(density) { miniWidth.toDp() }
                        val miniHeightDp = with(density) { miniHeight.toDp() }
                        Box(
                            modifier =
                                Modifier
                                    .size(miniWidthDp, miniHeightDp)
                                    .graphicsLayer {
                                        val controlsProgress =
                                            ((state.expandFraction.value - 0.6f) / 0.25f).coerceIn(0f, 1f)
                                        transformOrigin = TransformOrigin(0f, 0f)
                                        val pop = lerpClamped(0.96f, 1f, controlsProgress)
                                        scaleX = controlsScale * pop
                                        scaleY = controlsScale * pop
                                        alpha = controlsProgress
                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                        shape = RoundedCornerShape(MINI_PLAYER_CORNER_RADIUS_DP.dp)
                                        clip = true
                                    },
                        ) {
                            miniControls(fractionProvider)

                            LinearProgressIndicator(
                                progress = progress,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .graphicsLayer {
                                            alpha =
                                                ((state.expandFraction.value - 0.72f) / 0.18f)
                                                    .coerceIn(0f, 1f)
                                            compositingStrategy = CompositingStrategy.ModulateAlpha
                                        },
                                color = PlayerMiniProgress,
                                trackColor = Color.Transparent,
                            )
                        }
                    }
                }
            }
        }
    }
}
