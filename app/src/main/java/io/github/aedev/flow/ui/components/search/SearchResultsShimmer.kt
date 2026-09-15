package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.FeedGridLayout
import io.github.aedev.flow.ui.components.shared.ShimmerGridVideoCard
import io.github.aedev.flow.ui.components.shared.ShimmerVideoCardFullWidth

/** Placeholder cards in the layout the results will land in, so the first page does not jump. */
@Composable
fun SearchResultsShimmer(
    isGridMode: Boolean,
    feedLayout: FeedGridLayout,
    modifier: Modifier = Modifier,
) {
    val compactList = !isGridMode && feedLayout.isCompact
    LazyVerticalGrid(
        columns = feedLayout.cells,
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = feedLayout.contentPadding,
                end = feedLayout.contentPadding,
                top = TopPadding,
                bottom = BottomPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(if (compactList) 0.dp else feedLayout.cardSpacing),
        verticalArrangement = Arrangement.spacedBy(if (compactList) 0.dp else feedLayout.cardSpacing),
    ) {
        items(PLACEHOLDER_KEYS, key = { it }, contentType = { "shimmer" }) {
            if (compactList) ShimmerVideoCardFullWidth() else ShimmerGridVideoCard()
        }
    }
}

private val PLACEHOLDER_KEYS = (0 until 8).map { "shimmer:$it" }
private val TopPadding = 8.dp
private val BottomPadding = 80.dp
