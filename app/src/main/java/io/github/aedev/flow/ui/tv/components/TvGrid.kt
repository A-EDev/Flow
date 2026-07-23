package io.github.aedev.flow.ui.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.tv.focus.ProvideTvColumnPivot
import io.github.aedev.flow.ui.tv.focus.tvRowFocus
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/** Column count every TV video grid shares (home, subs, library, playlists). */
const val TV_GRID_COLUMNS = 3

/**
 * Standard TV content grid: three fixed columns (matching every other video
 * grid's card size), overscan padding, focus restoration, pivot scrolling,
 * and stable keys.
 */
@Composable
fun <T> TvMediaGrid(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    columns: GridCells = GridCells.Fixed(TV_GRID_COLUMNS),
    contentPadding: PaddingValues? = null,
    card: @Composable (T) -> Unit,
) {
    val dimens = LocalTvDimens.current
    ProvideTvColumnPivot {
        LazyVerticalGrid(
            columns = columns,
            modifier = modifier
                .fillMaxSize()
                .tvRowFocus(),
            horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
            contentPadding = contentPadding ?: PaddingValues(
                start = dimens.overscanHorizontal,
                end = dimens.overscanHorizontal,
                top = 12.dp,
                bottom = dimens.overscanVertical,
            ),
        ) {
            items(items = items, key = key) { item -> card(item) }
        }
    }
}
