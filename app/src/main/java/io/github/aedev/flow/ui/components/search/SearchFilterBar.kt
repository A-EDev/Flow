package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.ui.components.shared.FlowFilterChip

/**
 * The row above the results: the types YouTube lets a search be narrowed to, a layout toggle, and
 * the button that opens the full filter sheet.
 *
 * Types are Flow's own list rather than the response's chip cloud — the cloud mixes in
 * watched/unwatched chips that only mean something to a signed-in account.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchFilterBar(
    filter: SearchFilter,
    shortsEnabled: Boolean,
    isGridMode: Boolean,
    onContentTypeSelected: (ContentType) -> Unit,
    onToggleGridMode: () -> Unit,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val types =
        remember(shortsEnabled) {
            ContentType.entries.filterNot { it == ContentType.SHORTS && !shortsEnabled }
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
            contentPadding = PaddingValues(horizontal = BarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(types) { type ->
                FlowFilterChip(
                    label = stringResource(type.labelRes()),
                    selected = filter.contentType == type,
                    onClick = { onContentTypeSelected(type) },
                )
            }
        }

        FilledIconToggleButton(
            checked = isGridMode,
            onCheckedChange = { onToggleGridMode() },
            shapes = IconButtonDefaults.toggleableShapes(),
        ) {
            Icon(
                imageVector = if (isGridMode) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                contentDescription = stringResource(R.string.search_toggle_view_mode),
            )
        }

        BadgedBox(
            badge = {
                if (filter.activeCount > 0) Badge { Text(filter.activeCount.toString()) }
            },
            modifier = Modifier.padding(end = BarHorizontalPadding),
        ) {
            IconButton(onClick = onOpenFilters) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.search_filters_title),
                    modifier = Modifier.size(FilterIconSize),
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

private val ChipSpacing = 8.dp
private val BarHorizontalPadding = 12.dp
private val FilterIconSize = 22.dp
