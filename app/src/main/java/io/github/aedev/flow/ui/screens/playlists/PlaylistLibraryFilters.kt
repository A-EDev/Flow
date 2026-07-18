package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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

internal enum class PlaylistContentFilter {
    Videos,
    Music
}

internal enum class PlaylistOwnershipFilter {
    All,
    Owned,
    Saved;

    fun select(
        owned: List<PlaylistInfo>,
        saved: List<PlaylistInfo>
    ): List<PlaylistInfo> = when (this) {
        All -> (owned + saved).distinctBy(PlaylistInfo::id)
        Owned -> owned
        Saved -> saved
    }
}

@Composable
internal fun PlaylistLibraryFilterRow(
    selectedContent: PlaylistContentFilter,
    onContentSelected: (PlaylistContentFilter) -> Unit,
    selectedOwnership: PlaylistOwnershipFilter,
    onOwnershipSelected: (PlaylistOwnershipFilter) -> Unit
) {
    var ownershipExpanded by remember { mutableStateOf(false) }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = PlaylistContentFilter.entries,
            key = { it.name }
        ) { filter ->
            FilterChip(
                selected = selectedContent == filter,
                onClick = { onContentSelected(filter) },
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                PlaylistContentFilter.Videos -> R.string.tab_videos
                                PlaylistContentFilter.Music -> R.string.tab_music
                            }
                        )
                    )
                }
            )
        }

        item(key = "ownership-filter") {
            Box {
                FilterChip(
                    selected = selectedOwnership != PlaylistOwnershipFilter.All,
                    onClick = { ownershipExpanded = true },
                    label = { Text(selectedOwnership.label()) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                DropdownMenu(
                    expanded = ownershipExpanded,
                    onDismissRequest = { ownershipExpanded = false }
                ) {
                    PlaylistOwnershipFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.label()) },
                            leadingIcon = if (filter == selectedOwnership) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                null
                            },
                            onClick = {
                                ownershipExpanded = false
                                onOwnershipSelected(filter)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistOwnershipFilter.label(): String = stringResource(
    when (this) {
        PlaylistOwnershipFilter.All -> R.string.search_filter_all
        PlaylistOwnershipFilter.Owned -> R.string.playlist_filter_owned
        PlaylistOwnershipFilter.Saved -> R.string.saved
    }
)
