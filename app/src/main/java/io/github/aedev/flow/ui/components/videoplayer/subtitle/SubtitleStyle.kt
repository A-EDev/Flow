package io.github.aedev.flow.ui.components.videoplayer.subtitle

import androidx.compose.ui.graphics.Color

/** The outline Media3 can draw around caption text. */
enum class SubtitleEdgeType {
    NONE,
    OUTLINE,
    DROP_SHADOW,
    RAISED,
    DEPRESSED,
}

/**
 * How captions look. [backgroundColor] sits behind the text itself and [windowColor] behind the
 * whole caption. [bottomPadding], [fullscreenBottomPadding] and [verticalFullscreenBottomPadding]
 * are the gap in dp between the bottom of the picture and the lowest line, in the player, in
 * landscape fullscreen and in vertical fullscreen.
 */
data class SubtitleStyle(
    val fontSize: Float = 14f,
    val textColor: Color = Color.White,
    val backgroundColor: Color = Color.Black.copy(alpha = 0.6f),
    val windowColor: Color = Color.Transparent,
    val edgeType: SubtitleEdgeType = SubtitleEdgeType.NONE,
    val edgeColor: Color = Color.Black,
    val isBold: Boolean = true,
    val bottomPadding: Float = DEFAULT_BOTTOM_PADDING,
    val fullscreenBottomPadding: Float = DEFAULT_BOTTOM_PADDING,
    val verticalFullscreenBottomPadding: Float = DEFAULT_BOTTOM_PADDING,
) {
    fun bottomPaddingFor(
        isFullscreen: Boolean,
        isVertical: Boolean,
    ): Float =
        when {
            !isFullscreen -> bottomPadding
            isVertical -> verticalFullscreenBottomPadding
            else -> fullscreenBottomPadding
        }

    companion object {
        const val DEFAULT_BOTTOM_PADDING = 48f
    }
}
