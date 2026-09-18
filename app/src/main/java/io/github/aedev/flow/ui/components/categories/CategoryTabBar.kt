package io.github.aedev.flow.ui.components.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.innertube.pages.explore.ExploreDestination
import io.github.aedev.flow.ui.components.shared.FlowFilterChip
import io.github.aedev.flow.ui.screens.categories.CATEGORY_TABS
import io.github.aedev.flow.ui.screens.categories.CategorySubTab

/** The destination chips, on the same pill the search and channel filter bars use. */
@Composable
internal fun CategoryTabBar(
    selected: ExploreDestination,
    onSelect: (ExploreDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        contentPadding = PaddingValues(horizontal = RowHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(CATEGORY_TABS, key = { it.destination.name }) { tab ->
            FlowFilterChip(
                label = stringResource(tab.labelRes),
                selected = selected == tab.destination,
                onClick = { onSelect(tab.destination) },
            )
        }
    }
}

/** A destination's own categories — News ships seven of these, every other destination none. */
@Composable
internal fun CategorySubTabBar(
    subTabs: List<CategorySubTab>,
    selected: String?,
    onSelect: (CategorySubTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (subTabs.size < 2) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        contentPadding = PaddingValues(horizontal = RowHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(subTabs, key = { it.title }) { subTab ->
            FlowFilterChip(
                label = subTab.title,
                selected = selected == subTab.title,
                onClick = { onSelect(subTab) },
            )
        }
    }
}

private val ChipSpacing = 8.dp
private val RowHorizontalPadding = 12.dp
