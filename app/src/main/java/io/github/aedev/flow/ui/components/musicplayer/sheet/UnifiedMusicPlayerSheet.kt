package io.github.aedev.flow.ui.components.musicplayer.sheet

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.components.musicplayer.common.rememberMusicPlayerColorScheme
import io.github.aedev.flow.ui.components.musicplayer.full.FullMusicPlayerContent
import io.github.aedev.flow.ui.components.musicplayer.mini.MiniPlayerContent
import io.github.aedev.flow.ui.components.musicplayer.motion.MusicSheetDragGestureHandler
import io.github.aedev.flow.ui.components.musicplayer.motion.MusicSheetMotionController
import io.github.aedev.flow.ui.components.musicplayer.motion.miniPlayerDismissHorizontalGesture
import io.github.aedev.flow.ui.components.musicplayer.motion.musicSheetSettleSpring
import io.github.aedev.flow.ui.components.musicplayer.motion.musicSheetVerticalDragGesture
import io.github.aedev.flow.ui.components.musicplayer.motion.rememberMiniPlayerDismissGestureHandler
import io.github.aedev.flow.ui.components.shared.rememberMediaPalette
import io.github.aedev.flow.ui.utils.LocalWindowSizeClass
import io.github.aedev.flow.ui.utils.isMediumWidth
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt

val MusicMiniPlayerHeight = 64.dp
val MusicMiniPlayerBottomSpacer = 8.dp
private val CollapsedCornerRadius = 32.dp

private val SheetDefaultSpring =
    spring<Float>(
        dampingRatio = 0.85f,
        stiffness = 380f,
    )

/**
 * The music player as a single morphing card: the mini bar and the full player are two content
 * layers inside one container whose height, padding and corner radius interpolate with the
 * expansion fraction. All per-frame values are read in the layout/draw phase via lambdas so
 * dragging never recomposes the tree.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UnifiedMusicPlayerSheet(
    state: MusicPlayerSheetState,
    containerWidth: Dp,
    containerHeight: Dp,
    miniBounds: MiniPlayerBounds,
    restingBottomPx: () -> Float,
    track: MusicTrack,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val containerHeightPx = with(density) { containerHeight.toPx() }
    val miniHeightPx = with(density) { MusicMiniPlayerHeight.toPx() }
    val miniSpacerPx = with(density) { MusicMiniPlayerBottomSpacer.toPx() }
    val containerWidthPx = with(density) { containerWidth.toPx() }
    val isCompactWidth = !LocalWindowSizeClass.current.isMediumWidth
    val collapsedTargetY = (containerHeightPx - miniHeightPx - miniSpacerPx).coerceAtLeast(0f)
    val hiddenY = containerHeightPx + miniSpacerPx

    val collapsedTargetYState = rememberUpdatedState(collapsedTargetY)
    val densityState = rememberUpdatedState<Density>(density)

    val currentTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()
    val displayTrack = currentTrack ?: track

    val playerPreferences = remember { PlayerPreferences(context) }
    val backgroundStyle by playerPreferences.musicPlayerBackgroundStyle.collectAsState(
        initial = MusicPlayerBackgroundStyle.BLUR_GRADIENT,
    )
    val hideArtwork by playerPreferences.hideMusicPlayerArtwork.collectAsState(initial = false)
    val palette = rememberMediaPalette(displayTrack.highResThumbnailUrl)
    val playerScheme = rememberMusicPlayerColorScheme(palette, backgroundStyle)

    val motionController =
        remember(state) {
            MusicSheetMotionController(
                translationY = state.translationY,
                expansionFraction = state.expansionFraction,
                mutex = state.mutex,
                defaultAnimationSpec = SheetDefaultSpring,
            )
        }
    val overshootScaleY = remember { Animatable(1f) }
    val predictiveBackProgress = remember { Animatable(0f) }
    val dismissOffset = remember { Animatable(0f) }

    var positionInitialized by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(collapsedTargetY, hiddenY) {
        if (!positionInitialized) {
            motionController.snapTo(translationYValue = hiddenY, expansionFractionValue = 0f)
            positionInitialized = true
            if (!state.isDismissed) {
                motionController.animateTo(
                    targetExpanded = state.isExpanded,
                    collapsedY = collapsedTargetY,
                    animationSpec = SheetDefaultSpring,
                )
            }
        } else {
            val targetY =
                when {
                    state.isExpanded -> 0f
                    state.isCollapsed -> collapsedTargetY
                    else -> hiddenY
                }
            motionController.snapTo(
                translationYValue = targetY,
                expansionFractionValue = if (state.isExpanded) 1f else 0f,
            )
        }
    }

    LaunchedEffect(state.anchor, state.settleRequestId) {
        if (!positionInitialized) return@LaunchedEffect
        val (velocity, damping, squash) = state.consumePendingSettle()
        val fromFraction = state.expansionFraction.value
        when {
            state.isExpanded -> {
                launch {
                    motionController.animateTo(
                        targetExpanded = true,
                        collapsedY = collapsedTargetYState.value,
                        animationSpec = SheetDefaultSpring,
                        initialVelocity = velocity,
                    )
                }
                if (fromFraction < 0.95f) {
                    launch {
                        overshootScaleY.snapTo(1f)
                        overshootScaleY.animateTo(
                            targetValue = 1f,
                            animationSpec =
                                keyframes {
                                    durationMillis = 250
                                    1.0f at 0
                                    1.045f at 125
                                    1.0f at 250
                                },
                        )
                    }
                }
            }

            state.isCollapsed -> {
                launch {
                    motionController.animateTo(
                        targetExpanded = false,
                        collapsedY = collapsedTargetYState.value,
                        animationSpec = musicSheetSettleSpring(damping ?: Spring.DampingRatioNoBouncy),
                        initialVelocity = velocity,
                    )
                }
                if (fromFraction > 0.05f) {
                    launch {
                        overshootScaleY.snapTo(squash ?: 0.96f)
                        overshootScaleY.animateTo(
                            targetValue = 1f,
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                        )
                    }
                }
            }

            else -> {
                motionController.animateTo(
                    targetExpanded = false,
                    collapsedY = hiddenY,
                    animationSpec = tween(220),
                )
                state.dismissSettled = true
            }
        }
    }

    PredictiveBackHandler(enabled = state.isExpanded && positionInitialized) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                predictiveBackProgress.snapTo(backEvent.progress)
            }
            val p = predictiveBackProgress.value
            val baseY =
                state.translationY.value * (1f - p) + collapsedTargetYState.value * p
            val baseFraction = state.expansionFraction.value * (1f - p)
            motionController.snapTo(baseY, baseFraction)
            predictiveBackProgress.snapTo(0f)
            state.collapse()
        } catch (_: CancellationException) {
            scope.launch {
                predictiveBackProgress.animateTo(0f, tween(250))
            }
        }
    }

    if (state.isDismissed && state.dismissSettled) return
    if (!positionInitialized) return

    val geometry =
        rememberMusicSheetGeometry(
            state = state,
            predictiveBackProgress = predictiveBackProgress,
            collapsedYPx = collapsedTargetY,
            restingBottomPx = restingBottomPx,
            containerHeightPx = containerHeightPx,
            miniHeightPx = miniHeightPx,
            containerWidthPx = containerWidthPx,
            miniBounds = miniBounds,
            collapsedRadiusPx = with(density) { CollapsedCornerRadius.toPx() },
        )
    val cardShape = remember(geometry) { MusicSheetDynamicShape(geometry::cornerRadiusPx) }

    val miniAppear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (miniAppear.value < 1f) {
            miniAppear.animateTo(1f, tween(260))
        }
    }

    val dragHandler =
        remember(state, motionController) {
            MusicSheetDragGestureHandler(
                scope = scope,
                velocityTracker = VelocityTracker(),
                densityProvider = { densityState.value },
                motionController = motionController,
                expansionFraction = state.expansionFraction,
                translationY = state.translationY,
                expandedYProvider = { 0f },
                collapsedYProvider = { collapsedTargetYState.value },
                miniHeightPxProvider = { miniHeightPx },
                isExpandedProvider = { state.isExpanded },
                onDraggingChange = { isDragging = it },
                onSettle = { targetExpanded, velocity, dampingRatio, squash ->
                    state.settleFromGesture(targetExpanded, velocity, dampingRatio, squash)
                },
            )
        }
    val dismissHandler =
        rememberMiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            hapticFeedback = hapticFeedback,
            offsetAnimatable = dismissOffset,
            screenWidthPx = containerWidthPx,
            onDismiss = {
                state.dismiss()
                onDismiss()
            },
        )

    val cardShadowElevation by remember(state) {
        derivedStateOf {
            if (isDragging || state.expansionFraction.isRunning || state.expansionFraction.value > 0.18f) {
                0.dp
            } else {
                6.dp
            }
        }
    }
    val shouldRenderFullPlayer = rememberShouldRenderFullPlayer(state)

    MaterialTheme(
        colorScheme = playerScheme,
        motionScheme = MotionScheme.expressive(),
    ) {
        val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh

        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val translationY = geometry.visualTranslationY().roundToInt()
                        val overshoot = max(0, -translationY)
                        val targetHeight = constraints.maxHeight + overshoot
                        val placeable =
                            measurable.measure(
                                constraints.copy(minHeight = targetHeight, maxHeight = targetHeight),
                            )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.placeRelative(0, translationY)
                        }
                    },
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            translationX = dismissOffset.value
                            val appearScale = lerp(0.985f, 1f, miniAppear.value)
                            scaleX = appearScale
                            scaleY = overshootScaleY.value * appearScale
                            alpha = miniAppear.value
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }.layout { measurable, constraints ->
                            val targetHeightPx = geometry.cardHeightPx().roundToInt().coerceAtLeast(0)
                            val startPx = geometry.cardStartPx().roundToInt().coerceAtLeast(0)
                            val innerWidth = geometry.cardWidthPx().roundToInt().coerceAtLeast(0)
                            val placeable =
                                measurable.measure(
                                    constraints.copy(
                                        minWidth = innerWidth,
                                        maxWidth = innerWidth,
                                        minHeight = targetHeightPx,
                                        maxHeight = targetHeightPx,
                                    ),
                                )
                            layout(constraints.maxWidth, targetHeightPx) {
                                placeable.placeRelative(startPx, 0)
                            }
                        }.shadow(
                            elevation = cardShadowElevation,
                            shape = cardShape,
                            clip = false,
                        ).background(cardColor, cardShape)
                        .clip(cardShape)
                        .layout { measurable, constraints ->
                            val fullHeightPx = containerHeightPx.roundToInt()
                            val fraction = state.expansionFraction.value
                            val startPx = geometry.cardStartPx().roundToInt()
                            val measureWidth =
                                if (fraction > 0f) geometry.containerWidthPx().roundToInt() else constraints.maxWidth
                            val placeable =
                                measurable.measure(
                                    constraints.copy(
                                        minWidth = measureWidth,
                                        maxWidth = measureWidth,
                                        minHeight = fullHeightPx,
                                        maxHeight = fullHeightPx,
                                    ),
                                )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                val xOffset = if (fraction > 0f) -startPx else 0
                                placeable.placeRelative(xOffset, 0)
                            }
                        }.miniPlayerDismissHorizontalGesture(
                            enabled = state.isCollapsed,
                            handler = dismissHandler,
                        ).musicSheetVerticalDragGesture(
                            enabled = true,
                            handler = dragHandler,
                        ).clickable(
                            enabled = state.isCollapsed,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            state.expand()
                        },
            ) {
                val miniZIndex by remember(state) {
                    derivedStateOf { if (state.expansionFraction.value < 0.5f) 1f else 0f }
                }
                // The mini bar is fully transparent past 0.5 expansion; its waveform, marquee
                // and progress-ring animations stop there instead of animating under the full
                // player for entire listening sessions. derivedStateOf: recomposes only on flip.
                val miniAnimationsEnabled by remember(state) {
                    derivedStateOf { state.expansionFraction.value < 0.5f }
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(MusicMiniPlayerHeight)
                            .graphicsLayer {
                                alpha = (1f - state.expansionFraction.value * 2f).coerceIn(0f, 1f)
                            }.layout { measurable, constraints ->
                                val fraction = state.expansionFraction.value
                                val startPx = geometry.cardStartPx().roundToInt().coerceAtLeast(0)
                                val targetWidth =
                                    if (fraction > 0f) {
                                        geometry.cardWidthPx().roundToInt().coerceAtLeast(0)
                                    } else {
                                        constraints.maxWidth
                                    }
                                val placeable =
                                    measurable.measure(
                                        constraints.copy(minWidth = targetWidth, maxWidth = targetWidth),
                                    )
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.placeRelative(if (fraction > 0f) startPx else 0, 0)
                                }
                            }.zIndex(miniZIndex),
                ) {
                    MiniPlayerContent(
                        track = displayTrack,
                        animationsEnabled = miniAnimationsEnabled,
                        showPrevious = !isCompactWidth,
                    )
                }

                if (shouldRenderFullPlayer) {
                    val fullZIndex by remember(state) {
                        derivedStateOf { if (state.expansionFraction.value >= 0.5f) 1f else 0f }
                    }
                    val fullOffset by remember(state) {
                        derivedStateOf {
                            if (state.expansionFraction.value <= 0.01f) {
                                IntOffset(0, 10_000)
                            } else {
                                IntOffset.Zero
                            }
                        }
                    }
                    val fullEnterOffsetPx = with(density) { 24.dp.toPx() }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .requiredHeight(containerHeight)
                                .graphicsLayer {
                                    val f = geometry.fraction()
                                    val contentAlpha = (f - 0.25f).coerceIn(0f, 0.75f) / 0.75f
                                    alpha = contentAlpha
                                    translationY = fullEnterOffsetPx * (1f - contentAlpha)
                                }.zIndex(fullZIndex)
                                .offset { fullOffset },
                    ) {
                        FullMusicPlayerContent(
                            track = track,
                            isPlayerSheetExpanded = state.isExpanded,
                            palette = palette,
                            backgroundStyle = backgroundStyle,
                            hideArtwork = hideArtwork,
                            onCollapse = state::collapse,
                        )
                    }
                }
            }
        }
    }
}
