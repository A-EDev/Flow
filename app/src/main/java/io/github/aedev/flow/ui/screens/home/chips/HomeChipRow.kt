package io.github.aedev.flow.ui.screens.home.chips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowFilterChip
import kotlin.math.roundToInt

private val ChipRowHeight = 48.dp
private val ChipRowPadding = 12.dp
private val ChipSpacing = 8.dp

/**
 * The chips above the feed. [scrollState] is driven by the feed's enter-always scroll behaviour:
 * the row slides away as the feed scrolls down and comes back on the first scroll up. Its offset
 * is read in the layout phase, so scrolling never recomposes the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeChipRow(
    state: HomeChipsState,
    scrollState: TopAppBarState,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val heightPx = with(LocalDensity.current) { ChipRowHeight.toPx() }
    SideEffect {
        if (scrollState.heightOffsetLimit != -heightPx) scrollState.heightOffsetLimit = -heightPx
    }
    LazyRow(
        modifier =
            modifier
                .fillMaxWidth()
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val offset = scrollState.heightOffset.roundToInt()
                    layout(placeable.width, (placeable.height + offset).coerceAtLeast(0)) { placeable.place(0, offset) }
                }.height(ChipRowHeight),
        contentPadding = PaddingValues(horizontal = ChipRowPadding),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(state.chips, key = { it.key }) { chip ->
            val label = chip.label()
            val description = stringResource(R.string.home_chip_description, label)
            FlowFilterChip(
                label = label,
                selected = chip.key == state.selected,
                onClick = { onSelect(chip.key) },
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun HomeChip.label(): String =
    when (this) {
        HomeChip.All -> stringResource(R.string.home_chip_all)
        is HomeChip.Interest -> interest.label
        HomeChip.NewToYou -> stringResource(R.string.home_chip_new_to_you)
        HomeChip.RecentlyUploaded -> stringResource(R.string.home_chip_recently_uploaded)
        HomeChip.Mixes -> stringResource(R.string.home_chip_mixes)
        HomeChip.Live -> stringResource(R.string.home_chip_live)
        HomeChip.Watched -> stringResource(R.string.home_chip_watched)
    }
