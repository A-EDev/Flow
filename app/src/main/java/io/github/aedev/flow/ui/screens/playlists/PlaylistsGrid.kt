package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.PlaylistCardLayout
import io.github.aedev.flow.ui.components.layout.flowBottomContentPadding
import io.github.aedev.flow.ui.components.library.MusicPlaylistLibraryCard
import io.github.aedev.flow.ui.components.library.libraryGridLayoutFor
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.MediaKind

private val GridCellMinWidth = 160.dp
private val CompactCellMinWidth = 360.dp
private val GridSpacing = 16.dp
private val GridSidePadding = 16.dp
private val GridTopPadding = 8.dp

internal class PlaylistsGridActions(
    val onVideoClick: (PlaylistInfo) -> Unit,
    val onMusicClick: (PlaylistInfo) -> Unit,
    val onVideoDelete: (PlaylistInfo) -> Unit,
    val onMusicDownload: (PlaylistInfo) -> Unit,
    val onMusicRename: (PlaylistInfo) -> Unit,
    val onMusicDelete: (PlaylistInfo) -> Unit,
)

@Composable
internal fun PlaylistsGrid(
    kind: MediaKind,
    playlists: List<PlaylistInfo>,
    ownedMusicIds: Set<String>,
    compact: Boolean,
    actions: PlaylistsGridActions,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val videoGrid = remember(maxWidth) { libraryGridLayoutFor(maxWidth) }
        val videoCards = !compact && kind == MediaKind.Videos && videoGrid.isGrid
        val sidePadding =
            when {
                compact -> 0.dp
                videoCards -> videoGrid.padding
                else -> GridSidePadding
            }
        val partialRows = remember(playlists.size, videoGrid) { videoGrid.partialRows(playlists.size) }
        LazyVerticalGrid(
            columns =
                when {
                    compact -> GridCells.Adaptive(CompactCellMinWidth)
                    videoCards -> GridCells.Fixed(videoGrid.columns)
                    else -> GridCells.Adaptive(GridCellMinWidth)
                },
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = sidePadding,
                    top = GridTopPadding,
                    end = sidePadding,
                    bottom = flowBottomContentPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else GridSpacing),
            horizontalArrangement = Arrangement.spacedBy(if (videoCards) videoGrid.spacing else GridSpacing),
        ) {
            when {
                playlists.isEmpty() -> emptyPlaylists(kind)
                compact -> compactPlaylists(kind, playlists, ownedMusicIds, actions)
                kind == MediaKind.Videos -> videoPlaylistCards(playlists, videoCards, partialRows, actions)
                else -> musicPlaylistCards(playlists, ownedMusicIds, actions)
            }
        }
    }
}

private fun LazyGridScope.emptyPlaylists(kind: MediaKind) {
    item(
        key = if (kind == MediaKind.Videos) "empty-video-playlists" else "empty-music-playlists",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "empty",
    ) {
        if (kind == MediaKind.Videos) {
            FlowEmptyState(
                title = stringResource(R.string.no_playlists_found),
                icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
            )
        } else {
            FlowEmptyState(
                title = stringResource(R.string.empty_music_playlists),
                icon = Icons.Default.MusicNote,
            )
        }
    }
}

private fun LazyGridScope.compactPlaylists(
    kind: MediaKind,
    playlists: List<PlaylistInfo>,
    ownedMusicIds: Set<String>,
    actions: PlaylistsGridActions,
) {
    val isMusic = kind == MediaKind.Music
    items(
        items = playlists,
        key = { "${kind.name}-compact-${it.id}" },
        contentType = { "compact-playlist" },
    ) { playlist ->
        val isOwned = playlist.id in ownedMusicIds
        PlaylistCompactRow(
            playlist = playlist,
            isMusic = isMusic,
            onClick = { if (isMusic) actions.onMusicClick(playlist) else actions.onVideoClick(playlist) },
            trailing = {
                PlaylistRowMenuButton(
                    onDownload = if (isMusic && isOwned) ({ actions.onMusicDownload(playlist) }) else null,
                    onRename = if (isMusic && isOwned) ({ actions.onMusicRename(playlist) }) else null,
                    onDelete = { if (isMusic) actions.onMusicDelete(playlist) else actions.onVideoDelete(playlist) },
                )
            },
        )
    }
}

private fun LazyGridScope.videoPlaylistCards(
    playlists: List<PlaylistInfo>,
    videoCards: Boolean,
    partialRows: Collection<Int>,
    actions: PlaylistsGridActions,
) {
    itemsIndexed(
        items = playlists,
        key = { _, playlist -> "video-${playlist.id}" },
        contentType = { _, _ -> "video-playlist" },
        span = { index, _ ->
            GridItemSpan(if (videoCards && index !in partialRows) 1 else maxLineSpan)
        },
    ) { index, playlist ->
        PlaylistCard(
            playlist = playlist,
            onClick = { actions.onVideoClick(playlist) },
            onDeleteClick = { actions.onVideoDelete(playlist) },
            layout =
                if (videoCards && index !in partialRows) {
                    PlaylistCardLayout.SHELF
                } else {
                    PlaylistCardLayout.LIST
                },
            useInternalPadding = false,
        )
    }
}

private fun LazyGridScope.musicPlaylistCards(
    playlists: List<PlaylistInfo>,
    ownedMusicIds: Set<String>,
    actions: PlaylistsGridActions,
) {
    items(
        items = playlists,
        key = { "music-${it.id}" },
        contentType = { "music-playlist" },
    ) { playlist ->
        val isOwned = playlist.id in ownedMusicIds
        MusicPlaylistLibraryCard(
            playlist = playlist,
            onClick = { actions.onMusicClick(playlist) },
            onDownload = if (isOwned) ({ actions.onMusicDownload(playlist) }) else null,
            onRename = if (isOwned) ({ actions.onMusicRename(playlist) }) else null,
            onDelete = { actions.onMusicDelete(playlist) },
        )
    }
}
