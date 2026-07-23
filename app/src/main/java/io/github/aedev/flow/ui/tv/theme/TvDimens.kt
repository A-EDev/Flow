package io.github.aedev.flow.ui.tv.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout tokens for the ten-foot UI. Overscan values keep interactive content
 * inside the ~5% TV safe area; the rest sizes cards and spacing for D-pad browsing.
 */
@Immutable
data class TvDimens(
    val overscanHorizontal: Dp = 48.dp,
    val overscanVertical: Dp = 24.dp,
    val railCollapsedWidth: Dp = 72.dp,
    val railExpandedWidth: Dp = 280.dp,
    val videoCardWidth: Dp = 260.dp,
    val musicCardWidth: Dp = 180.dp,
    val rowSpacing: Dp = 36.dp,
    val itemSpacing: Dp = 20.dp,
    val focusScale: Float = 1.08f,
    val focusBorderWidth: Dp = 2.5.dp,
    val sidePanelWidth: Dp = 400.dp,
)

val LocalTvDimens = staticCompositionLocalOf { TvDimens() }
