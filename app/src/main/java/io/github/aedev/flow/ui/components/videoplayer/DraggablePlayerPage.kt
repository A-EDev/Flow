package io.github.aedev.flow.ui.components.videoplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.offset
import io.github.aedev.flow.ui.components.videoplayer.motion.BODY_CONTENT_MAX_EXPAND_FRACTION
import io.github.aedev.flow.ui.components.videoplayer.motion.BODY_SLIDE_PX
import io.github.aedev.flow.ui.components.videoplayer.motion.OpenOriginRect
import io.github.aedev.flow.ui.components.videoplayer.motion.RoundRectClipShape
import io.github.aedev.flow.ui.components.videoplayer.motion.openBodyAlpha
import io.github.aedev.flow.ui.components.videoplayer.motion.openGroundCornerRadius
import io.github.aedev.flow.ui.components.videoplayer.motion.openGroundRect
import kotlin.math.roundToInt

/**
 * The page under the video: placed below it, slid and faded with the sheet in the layout and draw
 * phases only, and parked off screen once the sheet is mini.
 */
@Composable
internal fun DraggablePlayerPage(
    state: PlayerDraggableState,
    isTwoPaneWindow: Boolean,
    statusBarHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    videoHeightProvider: () -> Float,
    portraitFsFraction: () -> Float,
    portraitFsTravel: () -> Float,
    openRect: () -> OpenOriginRect?,
    nestedScrollConnection: NestedScrollConnection,
    layoutDirection: LayoutDirection,
    bodyContent: @Composable (alpha: () -> Float, videoHeightPx: () -> Float) -> Unit,
) {
    val bodyAlphaProvider =
        remember(state) {
            {
                if (state.openOrigin != null) {
                    openBodyAlpha(state.expandFraction.value)
                } else {
                    (1f - state.expandFraction.value / BODY_CONTENT_MAX_EXPAND_FRACTION)
                        .coerceIn(0f, 1f)
                }
            }
        }
    val videoHeightPlaceholderProvider =
        remember(isTwoPaneWindow, videoHeightProvider) {
            if (isTwoPaneWindow) videoHeightProvider else ({ 0f })
        }
    val bodyPaddingTopProvider =
        remember(isTwoPaneWindow, statusBarHeight, videoHeightProvider) {
            if (isTwoPaneWindow) {
                ({ statusBarHeight })
            } else {
                ({ videoHeightProvider() + statusBarHeight })
            }
        }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
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
                                    fraction * BODY_SLIDE_PX + portraitFsFraction() * portraitFsTravel()
                                }
                            placeable.place(0, topPad + slide.roundToInt())
                        }
                    }.graphicsLayer {
                        alpha = bodyAlphaProvider() * (1f - portraitFsFraction())
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        // While an open grows out of a card the page is cut to the
                        // growing ground, so none of it floats over the page below.
                        val origin = openRect()
                        clip = origin != null
                        if (origin != null) {
                            val fraction = state.expandFraction.value
                            val bodyTop = bodyPaddingTopProvider().roundToInt() + (fraction * BODY_SLIDE_PX).roundToInt()
                            shape =
                                RoundRectClipShape(
                                    openGroundRect(origin, fraction, screenWidth, screenHeight)
                                        .translate(0f, -bodyTop.toFloat()),
                                    openGroundCornerRadius(origin, fraction),
                                )
                        }
                    }.nestedScroll(nestedScrollConnection),
        ) {
            bodyContent(bodyAlphaProvider, videoHeightPlaceholderProvider)
        }
    }
}
