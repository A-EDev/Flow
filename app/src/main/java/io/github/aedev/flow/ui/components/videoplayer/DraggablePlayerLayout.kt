package io.github.aedev.flow.ui.components.videoplayer

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.sanitizeDisplayAspectRatio
import io.github.aedev.flow.ui.components.videoplayer.motion.BODY_CONTENT_MAX_EXPAND_FRACTION
import io.github.aedev.flow.ui.components.videoplayer.motion.MINI_DISMISS_TEARDOWN_DELAY_MS
import io.github.aedev.flow.ui.components.videoplayer.motion.MINI_RESNAP_DEBOUNCE_MS
import io.github.aedev.flow.ui.components.videoplayer.motion.MiniPlayerBounds
import io.github.aedev.flow.ui.components.videoplayer.motion.cornerTargetX
import io.github.aedev.flow.ui.components.videoplayer.motion.cornerTargetY
import io.github.aedev.flow.ui.components.videoplayer.motion.dragPressSpringSpec
import io.github.aedev.flow.ui.components.videoplayer.motion.dragReleaseSpringSpec
import io.github.aedev.flow.ui.components.videoplayer.motion.expandDragZoomFor
import io.github.aedev.flow.ui.components.videoplayer.motion.miniDismissSpringSpec
import io.github.aedev.flow.ui.components.videoplayer.motion.miniResizeSpringSpec
import io.github.aedev.flow.ui.components.videoplayer.motion.miniSnapSpringSpec
import io.github.aedev.flow.ui.components.videoplayer.motion.portraitFullscreenSettleSpec
import io.github.aedev.flow.ui.components.videoplayer.motion.resolveMiniPlayerCorner
import io.github.aedev.flow.ui.components.videoplayer.motion.resolveMiniPlayerDismissOffset
import io.github.aedev.flow.ui.components.videoplayer.motion.shouldCollapseOnRelease
import io.github.aedev.flow.ui.components.videoplayer.motion.shouldEnterFullscreenFromSwipe
import io.github.aedev.flow.ui.utils.TABLET_SMALLEST_WIDTH_DP
import io.github.aedev.flow.ui.utils.isTabletFormFactor
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun lerpFloat(
    start: Float,
    stop: Float,
    fraction: Float,
): Float = start + (stop - start) * fraction.coerceIn(0f, 1f)

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
                            lerpFloat(baseVideoHeight, expandedVideoHeight, fraction)
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
                        launch {
                            state.offsetX.animateTo(
                                stablePhoneCenteredX,
                                miniSnapSpringSpec,
                            )
                        }
                        launch {
                            state.offsetY.animateTo(
                                stableWideTargetY,
                                miniSnapSpringSpec,
                            )
                        }
                    } else if (isWideMode && isLargeScreen) {
                        val clampedX = state.offsetX.value.coerceIn(minX, maxX)
                        if (kotlin.math.abs(state.offsetX.value - clampedX) > 1f) {
                            launch {
                                state.offsetX.animateTo(
                                    clampedX,
                                    miniSnapSpringSpec,
                                )
                            }
                        }
                        val clampedY = state.offsetY.value.coerceIn(minY, stableWideMaxY)
                        if (kotlin.math.abs(state.offsetY.value - clampedY) > 1f) {
                            launch {
                                state.offsetY.animateTo(
                                    clampedY,
                                    miniSnapSpringSpec,
                                )
                            }
                        }
                    } else {
                        val needsSnap =
                            state.offsetX.value == 0f &&
                                state.offsetY.value == 0f &&
                                targetMiniX > 0f && targetMiniY > 0f
                        if (needsSnap) {
                            state.offsetX.snapTo(targetMiniX)
                            state.offsetY.snapTo(targetMiniY)
                        } else {
                            launch {
                                state.offsetX.animateTo(
                                    targetMiniX,
                                    miniSnapSpringSpec,
                                )
                            }
                            launch {
                                state.offsetY.animateTo(
                                    targetMiniY,
                                    miniSnapSpringSpec,
                                )
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
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
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
                                .background(Color.Black.copy(alpha = 0.45f)),
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
                                .background(Color.Black),
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
            val minXState = rememberUpdatedState(minX)
            val maxXState = rememberUpdatedState(maxX)
            val minYState = rememberUpdatedState(minY)
            val maxYState = rememberUpdatedState(maxY)
            val statusBarHState = rememberUpdatedState(statusBarHeight)
            val targetMiniXState = rememberUpdatedState(targetMiniX)
            val targetMiniYState = rememberUpdatedState(targetMiniY)
            val screenWidthState = rememberUpdatedState(screenWidth)
            val miniWidthState = rememberUpdatedState(miniWidth)
            val marginState = rememberUpdatedState(margin)
            val stablePhoneCenteredXState = rememberUpdatedState(stablePhoneCenteredX)
            val tapToExpandState = rememberUpdatedState(tapToExpand)
            val onFullscreenGestureState = rememberUpdatedState(onFullscreenGesture)
            val isLandscapeState = rememberUpdatedState(isLandscape)
            val isFullscreenState = rememberUpdatedState(isFullscreen)
            val baseMiniWidthState = rememberUpdatedState(baseMiniWidth)
            val isTabletState = rememberUpdatedState(isTablet)
            val isFoldableState = rememberUpdatedState(isFoldable)
            val isLargeScreenState = rememberUpdatedState(isLargeScreen)
            val maxWideWidthState = rememberUpdatedState(maxWideWidth)
            val screenHeightState = rememberUpdatedState(screenHeight)
            val bottomNavPadState = rememberUpdatedState(bottomNavPad)
            val liveGestureScaleState =
                rememberUpdatedState<() -> Float>(
                    {
                        lerpFloat(
                            1f,
                            miniBoxWidth(baseMiniWidth * state.miniSizeScale.value)
                                .coerceAtMost(maxWideWidth) / expandedVideoWidth.coerceAtLeast(1f),
                            state.expandFraction.value,
                        )
                    },
                )

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier =
                        if (showImmersiveFullscreen) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .layout { measurable, constraints ->
                                    val grownHeight =
                                        lerpFloat(currentExpandedVideoHeightProvider(), screenHeight, portraitFsFraction)
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
                                        lerpFloat(
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
                                    val expandedTopY = lerpFloat(statusBarHeight, 0f, portraitFsFraction)
                                    translationX =
                                        lerpFloat(0f, state.offsetX.value, fraction) +
                                        windowW * (1f - drag) / 2f
                                    translationY =
                                        lerpFloat(expandedTopY, state.offsetY.value, fraction) +
                                        windowH * (1f - drag) / 2f
                                    shadowElevation =
                                        if (fraction > 0.95f) {
                                            8.dp.toPx() / visualMiniScale
                                        } else {
                                            0f
                                        }
                                    shape =
                                        RoundedCornerShape(
                                            if (fraction > 0.1f) (12f / visualMiniScale).dp else 0.dp,
                                        )
                                    clip = false
                                }.drawBehind {
                                    val fraction = state.expandFraction.value
                                    val r =
                                        if (fraction > 0.1f) {
                                            (12f / visualMiniScale).dp.toPx()
                                        } else {
                                            0f
                                        }
                                    drawRoundRect(
                                        color = Color.Black,
                                        cornerRadius = CornerRadius(r, r),
                                    )
                                }
                                //  Pinch-to-resize
                                .pointerInput("pinch") {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        val evt =
                                            awaitPointerEvent(
                                                androidx.compose.ui.input.pointer.PointerEventPass.Main,
                                            )
                                        val pressed = evt.changes.filter { it.pressed }
                                        if (pressed.size < 2) return@awaitEachGesture
                                        if (state.expandFraction.value < 0.8f) return@awaitEachGesture

                                        val ptr1Id = pressed[0].id
                                        val ptr2Id = pressed[1].id
                                        val initialDist =
                                            (
                                                (pressed[0].position - pressed[1].position)
                                                    .getDistance() * liveGestureScaleState.value()
                                            ).coerceAtLeast(1f)
                                        val startScale = state.miniSizeScale.value
                                        val wideCapWidth = maxWideWidthState.value
                                        val maxScale =
                                            (wideCapWidth / baseMiniWidthState.value).coerceAtLeast(1f)
                                        val snapSignal = Channel<Unit>(Channel.CONFLATED)
                                        var pScale = startScale
                                        var pX = state.offsetX.value
                                        var pY = state.offsetY.value
                                        val pinchDriver =
                                            state.scope.launch {
                                                for (ignored in snapSignal) {
                                                    state.miniSizeScale.snapTo(pScale)
                                                    state.offsetX.snapTo(pX)
                                                    state.offsetY.snapTo(pY)
                                                }
                                            }

                                        try {
                                            while (true) {
                                                val e =
                                                    awaitPointerEvent(
                                                        androidx.compose.ui.input.pointer.PointerEventPass.Main,
                                                    )
                                                val p1 =
                                                    e.changes.firstOrNull { it.id == ptr1Id } ?: break
                                                val p2 =
                                                    e.changes.firstOrNull { it.id == ptr2Id } ?: break
                                                if (!p1.pressed || !p2.pressed) {
                                                    snapSignal.close()
                                                    pinchDriver.cancel()
                                                    val targetScale =
                                                        if (state.miniSizeScale.value > 1.5f) {
                                                            maxScale
                                                        } else {
                                                            1f
                                                        }
                                                    state.scope.launch {
                                                        state.miniSizeScale.animateTo(
                                                            targetScale,
                                                            miniResizeSpringSpec,
                                                        )
                                                        if (targetScale <= 1f) {
                                                            launch {
                                                                state.offsetX.animateTo(
                                                                    state.cachedTargetX,
                                                                    miniResizeSpringSpec,
                                                                )
                                                                state.offsetY.animateTo(
                                                                    state.cachedTargetY,
                                                                    miniResizeSpringSpec,
                                                                )
                                                            }
                                                        } else {
                                                            if (isLargeScreenState.value) {
                                                                val newMiniW =
                                                                    (baseMiniWidthState.value * targetScale)
                                                                        .coerceAtMost(wideCapWidth)
                                                                val newMaxX =
                                                                    (screenWidthState.value - newMiniW - marginState.value)
                                                                        .coerceAtLeast(marginState.value)
                                                                val clampedX =
                                                                    state.offsetX.value
                                                                        .coerceIn(marginState.value, newMaxX)
                                                                launch {
                                                                    state.offsetX.animateTo(
                                                                        clampedX,
                                                                        miniResizeSpringSpec,
                                                                    )
                                                                }
                                                            } else {
                                                                launch {
                                                                    state.offsetX.animateTo(
                                                                        stablePhoneCenteredXState.value,
                                                                        miniResizeSpringSpec,
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    break
                                                }
                                                p1.consume()
                                                p2.consume()
                                                val currentDist =
                                                    (p1.position - p2.position).getDistance() *
                                                        liveGestureScaleState.value()
                                                val gestureScale = currentDist / initialDist
                                                val newScale =
                                                    (startScale * gestureScale).coerceIn(1f, maxScale)
                                                val newMiniW =
                                                    (baseMiniWidthState.value * newScale)
                                                        .coerceAtMost(wideCapWidth)
                                                val newMiniH = newMiniW * (9f / 16f)
                                                val newMaxX =
                                                    (screenWidthState.value - newMiniW - marginState.value)
                                                        .coerceAtLeast(marginState.value)
                                                val newMaxY =
                                                    (screenHeight - newMiniH - bottomNavPad - marginState.value)
                                                        .coerceAtLeast(minY)
                                                val clampedX =
                                                    when {
                                                        isLargeScreenState.value -> {
                                                            state.offsetX.value.coerceIn(marginState.value, newMaxX)
                                                        }

                                                        newScale > 1.5f -> {
                                                            stablePhoneCenteredXState.value
                                                        }

                                                        else -> {
                                                            state.offsetX.value.coerceIn(minX, newMaxX)
                                                        }
                                                    }
                                                val clampedY =
                                                    state.offsetY.value.coerceIn(minY, newMaxY)
                                                pScale = newScale
                                                pX = clampedX
                                                pY = clampedY
                                                snapSignal.trySend(Unit)
                                            }
                                        } finally {
                                            snapSignal.close()
                                            pinchDriver.cancel()
                                        }
                                    }
                                }.pointerInput(Unit) {
                                    val velocityTracker = VelocityTracker()
                                    var lastTapTime = 0L
                                    var singleTapJob: Job? = null
                                    awaitEachGesture {
                                        val gestureTargetMiniX = targetMiniXState.value
                                        val gestureTargetMiniY = targetMiniYState.value

                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val downConsumedByChild = down.isConsumed

                                        val isCollapseDrag = state.expandFraction.value < 0.4f
                                        val isMiniDrag = state.expandFraction.value > 0.8f

                                        val canSwipeToFullscreen =
                                            isCollapseDrag &&
                                                !isLandscapeState.value &&
                                                !isFullscreenState.value &&
                                                onFullscreenGestureState.value != null

                                        velocityTracker.resetTracking()
                                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                                        if (isCollapseDrag) {
                                            state.scope.launch {
                                                state.expandFraction.stop()
                                                state.offsetX.stop()
                                                state.offsetY.stop()
                                                state.offsetX.snapTo(gestureTargetMiniX)
                                                state.offsetY.snapTo(gestureTargetMiniY)
                                            }
                                        } else if (isMiniDrag) {
                                            state.scope.launch {
                                                state.offsetX.stop()
                                                state.offsetY.stop()
                                                state.dragScale.animateTo(
                                                    0.97f,
                                                    dragPressSpringSpec,
                                                )
                                            }
                                        }

                                        var dragPointerId = down.id
                                        var hasCrossedSlop = !isCollapseDrag
                                        var startDragY = 0f
                                        var detectedDirection = 0

                                        if (isCollapseDrag) {
                                            val slop = viewConfiguration.touchSlop
                                            while (!hasCrossedSlop) {
                                                val event =
                                                    awaitPointerEvent(
                                                        androidx.compose.ui.input.pointer.PointerEventPass.Main,
                                                    )
                                                val change =
                                                    event.changes
                                                        .firstOrNull { it.id == dragPointerId }
                                                if (change == null || !change.pressed ||
                                                    change.isConsumed
                                                ) {
                                                    break
                                                }
                                                velocityTracker.addPosition(
                                                    change.uptimeMillis,
                                                    change.position,
                                                )
                                                val delta =
                                                    (change.position - down.position) *
                                                        liveGestureScaleState.value()
                                                if (delta.y > slop &&
                                                    delta.y > kotlin.math.abs(delta.x)
                                                ) {
                                                    hasCrossedSlop = true
                                                    startDragY = delta.y
                                                    detectedDirection = 1
                                                    change.consume()
                                                } else if (canSwipeToFullscreen &&
                                                    delta.y < -slop &&
                                                    kotlin.math.abs(delta.y) >
                                                    kotlin.math.abs(delta.x)
                                                ) {
                                                    hasCrossedSlop = true
                                                    startDragY = delta.y
                                                    detectedDirection = -1
                                                    change.consume()
                                                } else if (kotlin.math.abs(delta.x) > slop) {
                                                    break
                                                }
                                            }
                                        }

                                        var cumulativeDragY = startDragY
                                        var totalMovement = 0f
                                        val startFraction = state.expandFraction.value
                                        var totalUpwardDrag = 0f

                                        if (hasCrossedSlop) {
                                            state.isDragging = true
                                            val snapSignal = Channel<Unit>(Channel.CONFLATED)
                                            var pendingFraction = state.expandFraction.value
                                            var pendingX = state.offsetX.value
                                            var pendingY = state.offsetY.value
                                            var pendingMode = 0
                                            var pendingExpandScale = 1f
                                            val snapDriver =
                                                state.scope.launch {
                                                    for (ignored in snapSignal) {
                                                        when (pendingMode) {
                                                            0 -> {
                                                                state.expandFraction.snapTo(pendingFraction)
                                                            }

                                                            2 -> {
                                                                state.expandDragScale.snapTo(pendingExpandScale)
                                                            }

                                                            else -> {
                                                                state.offsetX.snapTo(pendingX)
                                                                state.offsetY.snapTo(pendingY)
                                                            }
                                                        }
                                                    }
                                                }
                                            try {
                                                drag(dragPointerId) { change ->
                                                    val delta =
                                                        change.positionChange() *
                                                            liveGestureScaleState.value()
                                                    totalMovement += delta.getDistance()
                                                    velocityTracker.addPosition(
                                                        change.uptimeMillis,
                                                        change.position,
                                                    )

                                                    if (isCollapseDrag && detectedDirection == 1) {
                                                        change.consume()
                                                        cumulativeDragY += delta.y
                                                        val collapseTravel =
                                                            (targetMiniYState.value - statusBarHState.value).coerceAtLeast(1f)
                                                        val rawFraction =
                                                            (
                                                                startFraction +
                                                                    cumulativeDragY / collapseTravel
                                                            ).coerceIn(0f, 1f)
                                                        pendingFraction = rawFraction
                                                        pendingMode = 0
                                                        snapSignal.trySend(Unit)
                                                    } else if (isCollapseDrag &&
                                                        detectedDirection == -1
                                                    ) {
                                                        change.consume()
                                                        totalUpwardDrag += -delta.y
                                                        pendingExpandScale =
                                                            expandDragZoomFor(totalUpwardDrag)
                                                        pendingMode = 2
                                                        snapSignal.trySend(Unit)
                                                    } else if (isMiniDrag) {
                                                        if (totalMovement >
                                                            viewConfiguration.touchSlop * 0.5f
                                                        ) {
                                                            change.consume()
                                                            val currentMinX = minXState.value
                                                            val currentMaxX = maxXState.value
                                                            val currentMinY = minYState.value
                                                            val currentMaxY = maxYState.value
                                                            val rawY = state.offsetY.value + delta.y
                                                            val clampedY = rawY.coerceIn(currentMinY, currentMaxY)

                                                            when {
                                                                state.isInlineMode && !isLargeScreenState.value -> {
                                                                    pendingX = stablePhoneCenteredXState.value
                                                                    pendingY = clampedY
                                                                    pendingMode = 1
                                                                    snapSignal.trySend(Unit)
                                                                }

                                                                else -> {
                                                                    val rawX =
                                                                        state.offsetX.value + delta.x
                                                                    val clampedX =
                                                                        rawX.coerceIn(currentMinX, currentMaxX)
                                                                    pendingX = clampedX
                                                                    pendingY = clampedY
                                                                    pendingMode = 1
                                                                    snapSignal.trySend(Unit)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } finally {
                                                snapSignal.close()
                                                snapDriver.cancel()
                                                state.isDragging = false
                                                state.scope.launch {
                                                    state.dragScale.animateTo(
                                                        1f,
                                                        dragReleaseSpringSpec,
                                                    )
                                                }
                                                state.scope.launch {
                                                    state.expandDragScale.animateTo(
                                                        1f,
                                                        dragReleaseSpringSpec,
                                                    )
                                                }
                                            }
                                        } else {
                                            try {
                                                while (true) {
                                                    val event =
                                                        awaitPointerEvent(
                                                            androidx.compose.ui.input.pointer.PointerEventPass.Main,
                                                        )
                                                    if (event.changes.all { !it.pressed }) break
                                                }
                                            } finally {
                                                state.isDragging = false
                                            }
                                        }

                                        if (isMiniDrag && totalMovement < 24f) {
                                            if (!downConsumedByChild && tapToExpandState.value) {
                                                val now = down.uptimeMillis
                                                if (now - lastTapTime < 300L) {
                                                    singleTapJob?.cancel()
                                                    lastTapTime = 0L
                                                    if (state.isInlineMode) {
                                                        state.shrinkToCorner(
                                                            baseMiniWidth = baseMiniWidthState.value,
                                                            screenWidth = screenWidthState.value,
                                                            margin = marginState.value,
                                                            minY = minYState.value,
                                                            screenHeight = screenHeightState.value,
                                                            bottomNavPad = bottomNavPadState.value,
                                                        )
                                                    } else {
                                                        state.expandWide(
                                                            screenWidth = screenWidthState.value,
                                                            margin = marginState.value,
                                                            baseMiniWidth = baseMiniWidthState.value,
                                                            screenHeight = screenHeightState.value,
                                                            minY = minYState.value,
                                                            bottomNavPad = bottomNavPadState.value,
                                                            isTablet = isTabletState.value,
                                                            isFoldable = isFoldableState.value,
                                                        )
                                                    }
                                                } else {
                                                    lastTapTime = now
                                                    singleTapJob =
                                                        state.scope.launch {
                                                            kotlinx.coroutines.delay(300L)
                                                            state.expand()
                                                        }
                                                }
                                            }
                                            return@awaitEachGesture
                                        }

                                        if (isCollapseDrag && detectedDirection == -1) {
                                            val velY =
                                                velocityTracker.calculateVelocity().y *
                                                    liveGestureScaleState.value()
                                            if (shouldEnterFullscreenFromSwipe(totalUpwardDrag, velY)) {
                                                onFullscreenGestureState.value?.invoke()
                                            }
                                            return@awaitEachGesture
                                        }

                                        if (isCollapseDrag) {
                                            val velY =
                                                velocityTracker.calculateVelocity().y *
                                                    liveGestureScaleState.value()
                                            if (shouldCollapseOnRelease(state.expandFraction.value, velY)) {
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
                                        val velocityScale = liveGestureScaleState.value()
                                        val velY = velocity.y * velocityScale
                                        val velX = velocity.x * velocityScale
                                        val currentX = state.offsetX.value
                                        val currentY = state.offsetY.value
                                        val currentMinX = minXState.value
                                        val currentMaxX = maxXState.value
                                        val currentMinY = minYState.value
                                        val currentMaxY = maxYState.value

                                        val bounds =
                                            MiniPlayerBounds(
                                                minX = currentMinX,
                                                maxX = currentMaxX,
                                                minY = currentMinY,
                                                maxY = currentMaxY,
                                            )
                                        val newCorner =
                                            resolveMiniPlayerCorner(
                                                current = state.corner,
                                                currentX = currentX,
                                                currentY = currentY,
                                                bounds = bounds,
                                                scaledVelocityX = velX,
                                                scaledVelocityY = velY,
                                            )

                                        if (state.isInlineMode) {
                                            state.corner = newCorner
                                            if (isLargeScreenState.value) {
                                                state.scope.launch {
                                                    launch {
                                                        state.offsetX.animateTo(
                                                            cornerTargetX(newCorner, currentMinX, currentMaxX),
                                                            miniSnapSpringSpec,
                                                            initialVelocity = velX,
                                                        )
                                                    }
                                                    launch {
                                                        state.offsetY.animateTo(
                                                            cornerTargetY(newCorner, currentMinY, currentMaxY),
                                                            miniSnapSpringSpec,
                                                            initialVelocity = velY,
                                                        )
                                                    }
                                                }
                                            } else {
                                                state.scope.launch {
                                                    launch {
                                                        state.offsetX.animateTo(
                                                            stablePhoneCenteredXState.value,
                                                            miniSnapSpringSpec,
                                                        )
                                                    }
                                                    launch {
                                                        state.offsetY.animateTo(
                                                            cornerTargetY(newCorner, currentMinY, currentMaxY),
                                                            miniSnapSpringSpec,
                                                            initialVelocity = velY,
                                                        )
                                                    }
                                                }
                                            }
                                            return@awaitEachGesture
                                        }

                                        val dismissOffsetX =
                                            resolveMiniPlayerDismissOffset(
                                                targetCorner = newCorner,
                                                currentX = currentX,
                                                bounds = bounds,
                                                scaledVelocityX = velX,
                                                scaledVelocityY = velY,
                                                screenWidth = screenWidthState.value,
                                                miniWidth = miniWidthState.value,
                                                margin = marginState.value,
                                            )
                                        if (dismissOffsetX != null) {
                                            state.scope.launch {
                                                launch {
                                                    state.offsetX.animateTo(
                                                        dismissOffsetX,
                                                        miniDismissSpringSpec,
                                                        initialVelocity = velX,
                                                    )
                                                }
                                                kotlinx.coroutines.delay(MINI_DISMISS_TEARDOWN_DELAY_MS)
                                                onDismiss()
                                            }
                                        } else {
                                            state.corner = newCorner
                                            state.scope.launch {
                                                launch {
                                                    state.offsetX.animateTo(
                                                        cornerTargetX(newCorner, currentMinX, currentMaxX),
                                                        miniSnapSpringSpec,
                                                        initialVelocity = velX,
                                                    )
                                                }
                                                launch {
                                                    state.offsetY.animateTo(
                                                        cornerTargetY(newCorner, currentMinY, currentMaxY),
                                                        miniSnapSpringSpec,
                                                        initialVelocity = velY,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
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
                                        val pop = lerpFloat(0.96f, 1f, controlsProgress)
                                        scaleX = controlsScale * pop
                                        scaleY = controlsScale * pop
                                        alpha = controlsProgress
                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                        shape = RoundedCornerShape(12.dp)
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
                                color = Color.Red,
                                trackColor = Color.Transparent,
                            )
                        }
                    }
                }
            }
        }
    }
}
