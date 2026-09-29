package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.aedev.flow.ui.theme.PlayerScrim

/** Which end of the node the scrim is opaque at; it fades to transparent towards the other. */
internal enum class ScrimEdge {
    Top,
    Bottom,
}

/** Alpha the top gradient behind the player's top bar reaches at the very top. */
internal const val TOP_BAR_SCRIM_ALPHA = 0.38f

/** Alpha the bottom gradient behind the pill row and seek bar reaches at the very bottom. */
internal const val BOTTOM_BAR_SCRIM_ALPHA = 0.44f

/**
 * The vertical fade that keeps white controls legible over arbitrary video.
 *
 * The brush is built in `drawWithCache`, so it is rebuilt when the node is resized rather than on
 * every composition of the bar that carries it — the three call sites each allocated their own on
 * every recomposition of an overlay that recomposes on the playhead tick.
 */
internal fun Modifier.playerEdgeScrim(
    edge: ScrimEdge,
    color: Color = PlayerScrim,
): Modifier =
    drawWithCache {
        val brush =
            when (edge) {
                ScrimEdge.Top -> Brush.verticalGradient(listOf(color, Color.Transparent))
                ScrimEdge.Bottom -> Brush.verticalGradient(listOf(Color.Transparent, color))
            }
        onDrawBehind { drawRect(brush) }
    }
