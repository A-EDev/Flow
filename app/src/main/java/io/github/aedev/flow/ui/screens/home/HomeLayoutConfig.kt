package io.github.aedev.flow.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.rememberFeedGridLayout

internal data class HomeLayoutConfig(
    val columns: Int,
    val contentPadding: Dp,
    val cardSpacing: Dp,
    val shortsShelfAfterIndex: Int,
    val shimmerColumns: Int,
)

@Composable
internal fun rememberHomeLayoutConfig(maxWidth: Dp): HomeLayoutConfig {
    val base = rememberFeedGridLayout(maxWidth)
    return remember(base, maxWidth) {
        val shortsShelfAfterIndex =
            when {
                maxWidth < 480.dp -> 1
                maxWidth < 700.dp -> 2
                maxWidth < 900.dp -> 2
                maxWidth < 1200.dp -> 3
                else -> 4
            }
        HomeLayoutConfig(
            columns = base.columns,
            contentPadding = base.contentPadding,
            cardSpacing = base.cardSpacing,
            shortsShelfAfterIndex = shortsShelfAfterIndex,
            shimmerColumns = base.columns,
        )
    }
}
