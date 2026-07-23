package io.github.aedev.flow.ui.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.tv.focus.ProvideTvRowPivot
import io.github.aedev.flow.ui.tv.focus.tvRowFocus
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * Standard TV content shelf: optional section header + full-bleed LazyRow with
 * overscan content padding, focus restoration, pivot scrolling, and stable keys.
 * Screens compose rows through this — never hand-rolled focus/scroll wiring.
 */
@Composable
fun <T> TvMediaRow(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    title: String? = null,
    card: @Composable (T) -> Unit,
) {
    val dimens = LocalTvDimens.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        title?.let {
            TvSectionHeader(
                title = it,
                modifier = Modifier.padding(horizontal = dimens.overscanHorizontal),
            )
        }
        ProvideTvRowPivot {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvRowFocus(),
                horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
                // Vertical headroom so the focus scale never clips at the row bounds.
                contentPadding = PaddingValues(
                    horizontal = dimens.overscanHorizontal,
                    vertical = 12.dp,
                ),
            ) {
                items(items = items, key = key) { item -> card(item) }
            }
        }
    }
}
