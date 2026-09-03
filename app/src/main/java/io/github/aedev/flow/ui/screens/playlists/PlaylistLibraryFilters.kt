package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowFilterChip
import io.github.aedev.flow.ui.components.shared.MediaKind

private val FilterRowPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
private val MenuIconSize = 16.dp

internal enum class PlaylistOwnershipFilter {
    All,
    Owned,
    Saved,
    ;

    fun select(
        owned: List<PlaylistInfo>,
        saved: List<PlaylistInfo>,
    ): List<PlaylistInfo> =
        when (this) {
            All -> (owned + saved).distinctBy(PlaylistInfo::id)
            Owned -> owned
            Saved -> saved
        }
}

@Composable
internal fun PlaylistLibraryFilterRow(
    selectedKind: MediaKind,
    onKindSelected: (MediaKind) -> Unit,
    selectedOwnership: PlaylistOwnershipFilter,
    onOwnershipSelected: (PlaylistOwnershipFilter) -> Unit,
) {
    var ownershipExpanded by remember { mutableStateOf(false) }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = FilterRowPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = MediaKind.entries,
            key = { it.name },
        ) { kind ->
            FlowFilterChip(
                label = stringResource(kind.labelRes),
                selected = selectedKind == kind,
                onClick = { onKindSelected(kind) },
            )
        }

        item(key = "ownership-filter") {
            Box {
                FlowFilterChip(
                    label = selectedOwnership.label(),
                    selected = selectedOwnership != PlaylistOwnershipFilter.All,
                    onClick = { ownershipExpanded = true },
                )
                DropdownMenu(
                    expanded = ownershipExpanded,
                    onDismissRequest = { ownershipExpanded = false },
                ) {
                    PlaylistOwnershipFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.label()) },
                            leadingIcon =
                                if (filter == selectedOwnership) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(MenuIconSize),
                                        )
                                    }
                                } else {
                                    null
                                },
                            onClick = {
                                ownershipExpanded = false
                                onOwnershipSelected(filter)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistOwnershipFilter.label(): String =
    stringResource(
        when (this) {
            PlaylistOwnershipFilter.All -> R.string.search_filter_all
            PlaylistOwnershipFilter.Owned -> R.string.playlist_filter_owned
            PlaylistOwnershipFilter.Saved -> R.string.saved
        },
    )
