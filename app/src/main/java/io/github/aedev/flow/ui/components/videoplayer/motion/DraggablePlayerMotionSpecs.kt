package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.animation.core.spring

// Hand-tuned on device; a MaterialTheme.motionScheme mapping changes the feel and is a design
// decision, not a refactor.
internal val playerExpandSpringSpec = spring<Float>(dampingRatio = 0.86f, stiffness = 520f)
internal val miniSnapSpringSpec = spring<Float>(dampingRatio = 0.82f, stiffness = 500f)
internal val miniResizeSpringSpec = spring<Float>(dampingRatio = 0.72f, stiffness = 280f)
internal val miniDismissSpringSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 340f)
internal val dragPressSpringSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 600f)
internal val dragReleaseSpringSpec = spring<Float>(dampingRatio = 0.55f, stiffness = 500f)
internal val portraitFullscreenSettleSpec = spring<Float>(dampingRatio = 1f, stiffness = 360f)

/** Expansion fraction past which the body panel is fully transparent. */
internal const val BODY_CONTENT_MAX_EXPAND_FRACTION = 0.22f

/** Delay before an off-target mini player is nudged back to its resting corner. */
internal const val MINI_RESNAP_DEBOUNCE_MS = 50L

/** How long the dismiss fling is allowed to travel before the player is torn down. */
internal const val MINI_DISMISS_TEARDOWN_DELAY_MS = 200L

/**
 * Time from the start of a collapse to the moment the mini lifts out of its settle dip: the
 * landing spring is visually done by then, and the lift reads as a bounce off the nav bar.
 */
internal const val MINI_SETTLE_DIP_HOLD_MS = 400L
