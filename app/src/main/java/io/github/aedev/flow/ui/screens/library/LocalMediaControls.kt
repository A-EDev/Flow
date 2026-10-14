package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowFilterChip
import io.github.aedev.flow.ui.components.shared.FlowSortChip

/**
 * The chips under the search field: All or Folders, then sort and the filters that apply to this
 * tab. Video-only filters (quality, orientation, unwatched) are left out on the Music tab.
 */
@Composable
internal fun LocalMediaFilterBar(
    isVideos: Boolean,
    view: LocalView,
    filters: LocalFilters,
    onViewChange: (LocalView) -> Unit,
    onFiltersChange: ((LocalFilters) -> LocalFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FlowFilterChip(
                stringResource(R.string.local_view_all),
                selected = view == LocalView.ALL,
                onClick = { onViewChange(LocalView.ALL) },
            )
        }
        item(key = "folders") {
            FlowFilterChip(
                stringResource(R.string.local_view_folders),
                selected = view == LocalView.FOLDERS,
                onClick = { onViewChange(LocalView.FOLDERS) },
            )
        }
        item(key = "sort") {
            FlowSortChip(
                options = LocalSort.entries,
                selected = filters.sort,
                default = LocalSort.DATE_ADDED,
                label = { stringResource(it.labelRes) },
                onSelected = { sort -> onFiltersChange { it.copy(sort = sort) } },
            )
        }
        item(key = "length") {
            FlowSortChip(
                options = LengthFilter.entries,
                selected = filters.length,
                default = LengthFilter.ANY,
                label = { stringResource(it.labelRes) },
                onSelected = { length -> onFiltersChange { it.copy(length = length) } },
            )
        }
        if (isVideos) {
            item(key = "quality") {
                FlowSortChip(
                    options = QualityFilter.entries,
                    selected = filters.quality,
                    default = QualityFilter.ANY,
                    label = { stringResource(it.labelRes) },
                    onSelected = { quality -> onFiltersChange { it.copy(quality = quality) } },
                )
            }
            item(key = "unwatched") {
                FlowFilterChip(
                    stringResource(R.string.local_filter_unwatched),
                    selected = filters.unwatchedOnly,
                    onClick = { onFiltersChange { it.copy(unwatchedOnly = !it.unwatchedOnly) } },
                )
            }
            item(key = "portrait") {
                FlowFilterChip(
                    stringResource(R.string.local_filter_portrait),
                    selected = filters.portraitOnly,
                    onClick = { onFiltersChange { it.copy(portraitOnly = !it.portraitOnly) } },
                )
            }
        }
    }
}
