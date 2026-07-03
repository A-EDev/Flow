package io.github.aedev.flow.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive layout for the vertical video feed grid, shared by the Home and
 * Subscriptions screens so both render identically-sized full-width cards.
 */
data class FeedGridLayout(
    val columns: Int,
    val contentPadding: Dp,
    val cardSpacing: Dp
)

@Composable
fun rememberFeedGridLayout(maxWidth: Dp): FeedGridLayout = remember(maxWidth) {
    when {
        maxWidth < 480.dp -> FeedGridLayout(columns = 1, contentPadding = 0.dp, cardSpacing = 12.dp)
        maxWidth < 700.dp -> FeedGridLayout(columns = 1, contentPadding = 12.dp, cardSpacing = 14.dp)
        maxWidth < 900.dp -> FeedGridLayout(columns = 2, contentPadding = 16.dp, cardSpacing = 12.dp)
        maxWidth < 1200.dp -> FeedGridLayout(columns = 3, contentPadding = 20.dp, cardSpacing = 14.dp)
        else -> FeedGridLayout(columns = 4, contentPadding = 24.dp, cardSpacing = 16.dp)
    }
}
