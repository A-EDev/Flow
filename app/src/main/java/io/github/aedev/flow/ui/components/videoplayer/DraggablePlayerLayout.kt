package io.github.aedev.flow.ui.components.videoplayer

import android.content.res.Configuration
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import io.github.aedev.flow.ui.components.videoplayer.motion.BODY_CONTENT_MAX_EXPAND_FRACTION
import io.github.aedev.flow.ui.components.videoplayer.motion.BODY_SLIDE_PX
import io.github.aedev.flow.ui.components.videoplayer.motion.DraggablePlayerGestureHandler
import io.github.aedev.flow.ui.components.videoplayer.motion.DraggablePlayerGestureMetrics
import io.github.aedev.flow.ui.components.videoplayer.motion.MINI_RESNAP_DEBOUNCE_MS
import io.github.aedev.flow.ui.components.videoplayer.motion.MiniPlayerPinchGestureHandler
import io.github.aedev.flow.ui.components.videoplayer.motion.MiniPlayerResnapTargets
import io.github.aedev.flow.ui.components.videoplayer.motion.PlayerBodyNestedScrollConnection
import io.github.aedev.flow.ui.components.videoplayer.motion.computeDraggablePlayerGeometry
import io.github.aedev.flow.ui.components.videoplayer.motion.draggablePlayerGestures
import io.github.aedev.flow.ui.components.videoplayer.motion.lerpClamped
import io.github.aedev.flow.ui.components.videoplayer.motion.miniPlayerPinchGesture
import io.github.aedev.flow.ui.components.videoplayer.motion.portraitFullscreenSettleSpec
import io.github.aedev.flow.ui.components.videoplayer.motion.resnapMiniPlayer
import io.github.aedev.flow.ui.components.videoplayer.motion.resnapTargets
import io.github.aedev.flow.ui.components.videoplayer.motion.update
import io.github.aedev.flow.ui.theme.PlayerGround
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Corner radius of the floating mini player, authored pre-scale: the video box is clipped by the
 * PlayerView outline at `radius / miniVisualScale` so the morph's graphicsLayer scale brings it
 * back to this on screen.
 */
const val MINI_PLAYER_CORNER_RADIUS_DP = 12f

private val MiniPlayerMargin = 8.dp
private val PortraitFullscreenActivation = 28.dp
private val MiniPlayerShadowElevation = 8.dp

/** A collapse lands this much below its corner and lifts back up: the height of the nav bar it settles behind. */
private val MiniPlayerSettleDip = 48.dp

/**
 * The video player as one box that is laid out once at its expanded size and morphed into the
 * floating mini player purely through a graphicsLayer scale and translation. Every animated
 * value is read in the layout or draw phase; composition only sees settled booleans.
 *
 * @param isLargeWindow the window can host one of the player's detail layouts, so the video keeps
 *   the top of the window in landscape instead of going immersive and the mini player is sized
 *   from the window rather than the user's scale.
 * @param isTwoPaneWindow the body puts the detail pane beside the video, so the expanded video
 *   only takes the leading part of the width.
 * @param detailPaneWidth the width the detail pane and its spacer take from the video's row in a
 *   two-pane window; the expanded video gets what is left.
 */
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
    isLargeWindow: Boolean = false,
    startInset: Dp = 0.dp,
    isTwoPaneWindow: Boolean = false,
    detailPaneWidth: Dp = 0.dp,
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

    var playerHeightFraction by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(videoAspectRatio) { playerHeightFraction = 1f }

    var portraitFsFraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen && portraitFsFraction > 0f) {
            animate(
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
            val showImmersiveFullscreen =
                state.currentValue == PlayerSheetValue.Expanded &&
                    (isFullscreen || (isLandscape && !isLargeWindow))

            val geometry =
                computeDraggablePlayerGeometry(
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    statusBarHeight = statusBarHeight,
                    margin = with(density) { MiniPlayerMargin.toPx() },
                    startInset = with(density) { startInset.toPx() },
                    bottomNavPad = with(density) { bottomPadding.toPx() },
                    topBarPad = with(density) { topPadding.toPx() },
                    isLargeWindow = isLargeWindow,
                    isTwoPaneWindow = isTwoPaneWindow,
                    detailPaneWidth = with(density) { detailPaneWidth.toPx() },
                    miniPlayerScale = miniPlayerScale,
                    videoAspectRatio = videoAspectRatio,
                    currentSizeScale = state.miniSizeScale.targetValue,
                    corner = state.corner,
                    isShrinkingToCorner = state.isShrinkingToCorner,
                    cachedTargetX = state.cachedTargetX,
                    offsetXFallback = { state.offsetX.value },
                )
            val expandedVideoWidth = geometry.expandedVideoWidth
            val visualMiniScale = geometry.visualMiniScale

            val heightFractionOverrideState = rememberUpdatedState(expandedPlayerHeightFractionOverride)
            // Read in the layout phase only: the fraction changes on every nested-scroll delta and
            // every media-sheet drag frame, and a composition read here recomposed this whole tree.
            val currentExpandedVideoHeightProvider =
                remember(geometry.baseVideoHeight, geometry.expandedVideoHeight) {
                    {
                        if (geometry.expandedVideoHeight > geometry.baseVideoHeight) {
                            val fraction =
                                heightFractionOverrideState.value?.invoke()?.coerceIn(0f, 1f)
                                    ?: playerHeightFraction
                            lerpClamped(geometry.baseVideoHeight, geometry.expandedVideoHeight, fraction)
                        } else {
                            geometry.expandedVideoHeight
                        }
                    }
                }
            ReportExpandedPlayerBottom(
                statusBarHeight = statusBarHeight,
                videoHeightProvider = currentExpandedVideoHeightProvider,
                onChanged = onExpandedPlayerBottomChanged,
            )

            val settleDipPx = with(density) { MiniPlayerSettleDip.toPx() }
            SideEffect {
                state.miniVisualScale = visualMiniScale
                state.cachedTargetX = geometry.normalTargetX
                state.cachedTargetY = geometry.normalTargetY
                state.settleDipPx = settleDipPx
            }

            val isCollapsedTarget by remember(state) {
                derivedStateOf { state.expandFraction.targetValue > 0.5f }
            }
            LaunchedEffect(isCollapsedTarget) {
                if (isCollapsedTarget) playerHeightFraction = 1f
            }

            MiniPlayerResnapEffect(
                state = state,
                isCollapsedTarget = isCollapsedTarget,
                targets = geometry.resnapTargets(isLargeScreen = isLargeWindow),
            )

            val portraitFsTravel = (screenHeight - geometry.expandedVideoHeight).coerceAtLeast(1f)
            val portraitFsEnabled =
                !isLandscape && !isLargeWindow && !isFullscreen &&
                    onEnterPortraitFullscreen != null
            val portraitFsActivationPx = with(density) { PortraitFullscreenActivation.toPx() }
            val portraitFsTravelState = rememberUpdatedState(portraitFsTravel)
            val portraitFsEnabledState = rememberUpdatedState(portraitFsEnabled)
            val portraitFsActivationState = rememberUpdatedState(portraitFsActivationPx)
            val onEnterPortraitFsState = rememberUpdatedState(onEnterPortraitFullscreen)
            val nestedScrollConnection =
                remember(geometry.expandedVideoHeight, geometry.baseVideoHeight) {
                    PlayerBodyNestedScrollConnection(
                        expandedVideoHeight = geometry.expandedVideoHeight,
                        baseVideoHeight = geometry.baseVideoHeight,
                        playerHeightFraction = { playerHeightFraction },
                        onPlayerHeightFractionChange = { playerHeightFraction = it },
                        portraitFsFraction = { portraitFsFraction },
                        onPortraitFsFractionChange = { portraitFsFraction = it },
                        portraitFsTravel = { portraitFsTravelState.value },
                        portraitFsEnabled = { portraitFsEnabledState.value },
                        portraitFsActivationPx = { portraitFsActivationState.value },
                        expandFraction = { state.expandFraction.value },
                        onEnterPortraitFullscreen = { onEnterPortraitFsState.value },
                    )
                }

            if (showImmersiveFullscreen) {
                ImmersiveFullscreenBackdrop(thumbnailUrl = thumbnailUrl)
            } else {
                CollapsingPlayerScrim(state = state, statusBarHeight = statusBarHeight)
            }

            if (!showImmersiveFullscreen) {
                val bodyAlphaProvider =
                    remember(state) {
                        {
                            (1f - state.expandFraction.value / BODY_CONTENT_MAX_EXPAND_FRACTION)
                                .coerceIn(0f, 1f)
                        }
                    }
                val videoHeightPlaceholderProvider =
                    remember(isTwoPaneWindow, currentExpandedVideoHeightProvider) {
                        if (isTwoPaneWindow) currentExpandedVideoHeightProvider else ({ 0f })
                    }
                val bodyPaddingTopProvider =
                    remember(isTwoPaneWindow, statusBarHeight, currentExpandedVideoHeightProvider) {
                        if (isTwoPaneWindow) {
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
                                        // The slide is a placement offset, not a layer translation: a
                                        // positional layer transform makes Compose re-walk every node
                                        // of this page's subtree on each frame of the sheet motion.
                                        val fraction = state.expandFraction.value
                                        val slide =
                                            if (fraction > 0.999f) {
                                                placeable.height.toFloat()
                                            } else {
                                                fraction * BODY_SLIDE_PX + portraitFsFraction * screenHeight
                                            }
                                        placeable.place(0, topPad + slide.roundToInt())
                                    }
                                }.graphicsLayer {
                                    alpha = bodyAlphaProvider() * (1f - portraitFsFraction)
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                }.nestedScroll(nestedScrollConnection),
                    ) {
                        bodyContent(bodyAlphaProvider, videoHeightPlaceholderProvider)
                    }
                }
            }

            val gestureMetrics = remember(state) { DraggablePlayerGestureMetrics() }
            SideEffect {
                gestureMetrics.update(
                    geometry = geometry,
                    isLargeWindow = isLargeWindow,
                    isLandscape = isLandscape,
                    isFullscreen = isFullscreen,
                    tapToExpand = tapToExpand,
                    onFullscreenGesture = onFullscreenGesture,
                    onCollapseGesture = onCollapseGesture,
                    onDismiss = onDismiss,
                )
            }
            val gestureHandler =
                remember(state, gestureMetrics) { DraggablePlayerGestureHandler(state, gestureMetrics) }
            val pinchHandler =
                remember(state, gestureMetrics) { MiniPlayerPinchGestureHandler(state, gestureMetrics) }

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
                                        geometry
                                            .miniBoxWidth(geometry.baseMiniWidth * state.miniSizeScale.value)
                                            .coerceAtMost(geometry.maxWideWidth)
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
                                        lerpClamped(expandedTopY, state.offsetY.value + state.settleDip.value, fraction) +
                                        windowH * (1f - drag) / 2f
                                    shadowElevation =
                                        if (fraction > 0.95f) {
                                            MiniPlayerShadowElevation.toPx() / visualMiniScale
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
                                // Two pointer nodes with constant keys: the chain must not change
                                // shape while a finger is down or the drag coroutine is reset.
                                .miniPlayerPinchGesture(pinchHandler)
                                .draggablePlayerGestures(gestureHandler)
                        },
                ) {
                    videoContent(Modifier.fillMaxSize())

                    if (!showImmersiveFullscreen) {
                        MiniPlayerControlsLayer(
                            state = state,
                            miniWidth = geometry.miniWidth,
                            miniHeight = geometry.miniHeight,
                            expandedVideoWidth = expandedVideoWidth,
                            progress = progress,
                            miniControls = miniControls,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Nudges a settled mini player back onto its resting corner whenever the bounds change under it
 * (nav bar shown or hidden, rotation, wide mode). Keyed on settled values only, never on a live
 * fraction, so it cannot restart per frame.
 */
@Composable
private fun MiniPlayerResnapEffect(
    state: PlayerDraggableState,
    isCollapsedTarget: Boolean,
    targets: MiniPlayerResnapTargets,
) {
    LaunchedEffect(
        isCollapsedTarget,
        targets.targetMiniX,
        targets.targetMiniY,
        targets.isWideMode,
        targets.isLargeScreen,
    ) {
        if (state.expandFraction.targetValue <= 0.5f || state.isDragging) return@LaunchedEffect
        delay(MINI_RESNAP_DEBOUNCE_MS)
        if (state.isDragging) return@LaunchedEffect
        resnapMiniPlayer(state, targets)
    }
}

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
