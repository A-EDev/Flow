package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.local.UploadDate
import io.github.aedev.flow.data.local.WatchState
import io.github.aedev.flow.data.paging.supportsVideoFilters
import io.github.aedev.flow.ui.components.shared.FlowFilterChip

/**
 * The type chips above the results, in the order YouTube shows them — Shorts second, so the one
 * chip people reach for most is never the one that scrolls off. Recently uploaded, Unwatched and
 * Watched follow them as quick toggles over the same fields the filter dialog edits, so the two
 * always agree; they leave with the dialog's video rows when the type has no videos.
 *
 * The filter and layout affordances live in the top bar, so this row owns the full width.
 */
@Composable
fun SearchFilterBar(
    filter: SearchFilter,
    shortsEnabled: Boolean,
    onFilterChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val types =
        remember(shortsEnabled) {
            CHIP_ORDER.filterNot { it == ContentType.SHORTS && !shortsEnabled }
        }
    val videoFilters = filter.contentType.supportsVideoFilters()

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        contentPadding = PaddingValues(horizontal = RowHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(types, key = { it.name }) { type ->
            FlowFilterChip(
                label = stringResource(type.labelRes()),
                selected = filter.contentType == type,
                onClick = { onFilterChange(filter.copy(contentType = type)) },
            )
        }
        if (videoFilters) {
            item(key = RECENT_KEY) {
                val recent = filter.uploadDate == UploadDate.THIS_WEEK
                FlowFilterChip(
                    label = stringResource(R.string.search_filter_recently_uploaded),
                    selected = recent,
                    onClick = {
                        onFilterChange(filter.copy(uploadDate = if (recent) UploadDate.ANY else UploadDate.THIS_WEEK))
                    },
                )
            }
            items(WATCH_CHIPS, key = { it.name }) { state ->
                val selected = filter.watchState == state
                FlowFilterChip(
                    label = stringResource(state.labelRes()),
                    selected = selected,
                    onClick = { onFilterChange(filter.copy(watchState = if (selected) WatchState.ANY else state)) },
                )
            }
        }
    }
}

internal fun ContentType.labelRes(): Int =
    when (this) {
        ContentType.ALL -> R.string.search_filter_all
        ContentType.VIDEOS -> R.string.videos_header
        ContentType.SHORTS -> R.string.tab_shorts
        ContentType.CHANNELS -> R.string.channels_header
        ContentType.PLAYLISTS -> R.string.tab_playlists
        ContentType.MOVIES -> R.string.search_filter_movies
        ContentType.LIVE -> R.string.tab_live
    }

private val CHIP_ORDER =
    listOf(
        ContentType.ALL,
        ContentType.SHORTS,
        ContentType.VIDEOS,
        ContentType.LIVE,
        ContentType.CHANNELS,
        ContentType.PLAYLISTS,
        ContentType.MOVIES,
    )

private val WATCH_CHIPS = listOf(WatchState.UNWATCHED, WatchState.WATCHED)

private const val RECENT_KEY = "recently_uploaded"

private val ChipSpacing = 8.dp
private val RowHorizontalPadding = 12.dp
