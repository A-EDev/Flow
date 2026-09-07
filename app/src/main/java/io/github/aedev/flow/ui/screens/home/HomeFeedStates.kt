package io.github.aedev.flow.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.ShimmerGridVideoCard
import io.github.aedev.flow.ui.components.shared.ShimmerVideoCardFullWidth
import io.github.aedev.flow.ui.components.shared.ShimmerVideoCardHorizontal

private const val SHIMMER_CARD_COUNT = 12

@Composable
internal fun HomeFeedDisabledState(modifier: Modifier = Modifier) {
    FlowEmptyState(
        title = stringResource(R.string.content_settings_home_feed_disabled_title),
        subtitle = stringResource(R.string.content_settings_home_feed_disabled_body),
        icon = Icons.Outlined.SmartDisplay,
        modifier = modifier,
    )
}

@Composable
internal fun HomeFeedShimmer(
    layoutConfig: HomeLayoutConfig,
    isListView: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = if (isListView) GridCells.Fixed(1) else GridCells.Fixed(layoutConfig.shimmerColumns),
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = if (isListView) 0.dp else layoutConfig.contentPadding,
                end = if (isListView) 0.dp else layoutConfig.contentPadding,
                top = 8.dp,
                bottom = 80.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
        verticalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
        userScrollEnabled = false,
    ) {
        items(SHIMMER_CARD_COUNT) {
            if (isListView) {
                ShimmerVideoCardHorizontal()
            } else if (layoutConfig.shimmerColumns == 1) {
                ShimmerVideoCardFullWidth()
            } else {
                ShimmerGridVideoCard()
            }
        }
    }
}
