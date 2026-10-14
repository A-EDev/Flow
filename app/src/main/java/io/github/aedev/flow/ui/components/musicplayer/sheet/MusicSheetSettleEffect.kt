package io.github.aedev.flow.ui.components.musicplayer.sheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.aedev.flow.ui.components.musicplayer.motion.MusicSheetMotionController
import io.github.aedev.flow.ui.components.musicplayer.motion.musicSheetSettleSpring
import kotlinx.coroutines.launch

/** Moves the sheet to whatever anchor it was last asked for, with the overshoot or squash of that move. */
@Composable
internal fun MusicSheetSettleEffect(
    state: MusicPlayerSheetState,
    motionController: MusicSheetMotionController,
    positionInitialized: Boolean,
    collapsedY: () -> Float,
    hiddenY: Float,
    overshootScaleY: Animatable<Float, AnimationVector1D>,
    defaultSpring: AnimationSpec<Float>,
) {
    LaunchedEffect(state.anchor, state.settleRequestId) {
        if (!positionInitialized) return@LaunchedEffect
        val (velocity, damping, squash) = state.consumePendingSettle()
        val fromFraction = state.expansionFraction.value
        when {
            state.isExpanded -> {
                launch {
                    motionController.animateTo(
                        targetExpanded = true,
                        collapsedY = collapsedY(),
                        animationSpec = defaultSpring,
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
                        collapsedY = collapsedY(),
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
}
