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
    val overscanVertical: Dp = 27.dp,
    val railCollapsedWidth: Dp = 96.dp,
    val feedCardMinWidth: Dp = 184.dp,
    val feedMaxColumns: Int = 3,
    val sidePanelWidth: Dp = 400.dp,
)

val LocalTvDimens = staticCompositionLocalOf { TvDimens() }
