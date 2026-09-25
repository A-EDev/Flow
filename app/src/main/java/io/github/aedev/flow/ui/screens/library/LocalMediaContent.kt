package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.localmedia.LocalMediaItem
import io.github.aedev.flow.ui.components.library.LibrarySelection
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowPullToRefreshBox
import io.github.aedev.flow.ui.components.shared.FlowSegmentedGap
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.components.shared.animateMediaGridItem

private val GridSpacing = 12.dp
private val LaneCardWidth = 200.dp

/** How the viewer acts on files from the list; the screen decides what each does. */
internal class LocalContentActions(
    val onPlay: (items: List<LocalMediaItem>, index: Int, shuffle: Boolean) -> Unit,
    val onLongClick: (LocalMediaItem) -> Unit,
    val onOpenFolder: (String?) -> Unit,
    val onManageHidden: () -> Unit,
)

/**
 * The files of the current tab under [banner]: Continue watching, a count with Play all and
 * Shuffle, then the files as rows or as cards over [columns]. With [showFolders], the folders
 * instead, unless one is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalMediaContent(
    state: LocalMediaUiState,
    showFolders: Boolean,
    columns: Int,
    selection: LibrarySelection,
    actions: LocalContentActions,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    banner: (@Composable () -> Unit)? = null,
) {
    val isVideos = state.selection.kind == MediaKind.Videos
    val filters = state.selection.filters
    FlowPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = rememberPullToRefreshState(),
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (showFolders) 1 else columns),
            contentPadding = PaddingValues(start = inset(columns, showFolders), end = inset(columns, showFolders), bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
            modifier = Modifier.fillMaxSize(),
        ) {
            banner?.let { content ->
                fullWidth("banner") { Box(Modifier.padding(horizontal = rowInset(columns, showFolders), vertical = 8.dp)) { content() } }
            }
            if (showFolders) {
                itemsIndexed(state.folders, key = { _, folder -> "folder_${folder.id}" }) { index, folder ->
                    Box(
                        Modifier.padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom =
                                if (index <
                                    state.folders.lastIndex
                                ) {
                                    FlowSegmentedGap
                                } else {
                                    0.dp
                                },
                        ),
                    ) {
                        LocalFolderRow(folder, index, state.folders.size, isVideos, selected = false) { actions.onOpenFolder(folder.id) }
                    }
                }
            } else {
                val showLane = isVideos && state.openFolder == null && filters.query.isBlank() && !filters.isFiltering
                if (showLane && state.continueWatching.isNotEmpty()) {
                    fullWidth("continue") { ContinueWatchingLane(state, actions) }
                }
                if (state.items.isNotEmpty()) {
                    fullWidth("summary") { PlayAllRow(state.items, isVideos, rowInset(columns, showFolders), actions) }
                } else if (state.totalCount == 0) {
                    fullWidth("empty") {
                        FlowEmptyState(
                            title =
                                stringResource(
                                    R.string.local_media_empty_title,
                                    stringResource(if (isVideos) R.string.tab_videos else R.string.tab_music),
                                ),
                            subtitle = stringResource(R.string.local_media_empty_body),
                            icon = if (isVideos) Icons.Outlined.VideoLibrary else Icons.Outlined.MusicNote,
                            modifier = Modifier.padding(top = 48.dp),
                        )
                    }
                } else {
                    fullWidth("no_results") {
                        FlowEmptyState(
                            title =
                                stringResource(
                                    R.string.local_media_empty_title,
                                    stringResource(if (isVideos) R.string.tab_videos else R.string.tab_music),
                                ),
                            icon = Icons.Outlined.SearchOff,
                            modifier = Modifier.padding(top = 48.dp),
                        )
                    }
                }
                itemsIndexed(state.items, key = { _, item -> item.id }, contentType = { _, _ ->
                    if (columns >
                        1
                    ) {
                        "card"
                    } else {
                        "row"
                    }
                }) { index, item ->
                    val isNew = item.watchState(state.playback, state.nowMs) == WatchState.NEW
                    val onClick = { if (selection.active) selection.onToggle(item.mediaId) else actions.onPlay(state.items, index, false) }
                    if (isVideos && columns > 1) {
                        LocalMediaCard(
                            item = item,
                            isNew = isNew,
                            selected = item.mediaId in selection.ids,
                            onClick = onClick,
                            onLongClick = if (selection.active) null else ({ actions.onLongClick(item) }),
                            modifier = animateMediaGridItem().padding(bottom = GridSpacing),
                        )
                    } else {
                        LocalMediaRow(
                            item = item,
                            isNew = isNew,
                            selectionMode = selection.active,
                            selected = item.mediaId in selection.ids,
                            onClick = onClick,
                            onLongClick = { actions.onLongClick(item) },
                            modifier = animateMediaGridItem(),
                        )
                    }
                }
            }
            if (state.hiddenCount > 0 && state.openFolder == null) {
                fullWidth("hidden") { HiddenFilesNote(state.hiddenCount, actions.onManageHidden) }
            }
        }
    }
}

/** Rows carry their own side padding, so content above them is inset to match; cards use the grid's. */
private fun rowInset(
    columns: Int,
    showFolders: Boolean,
): Dp = if (columns > 1 && !showFolders) 0.dp else 16.dp

private fun inset(
    columns: Int,
    showFolders: Boolean,
): Dp = if (columns > 1 && !showFolders) 16.dp else 0.dp

private fun LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) { content() }
}

@Composable
private fun ContinueWatchingLane(
    state: LocalMediaUiState,
    actions: LocalContentActions,
) = Column {
    Text(
        text = stringResource(R.string.local_continue_watching),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
    )
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
        items(state.continueWatching, key = { it.id }) { item ->
            LocalMediaCard(
                item = item,
                isNew = false,
                selected = false,
                onClick = { actions.onPlay(state.continueWatching, state.continueWatching.indexOf(item), false) },
                onLongClick = { actions.onLongClick(item) },
                modifier = Modifier.width(LaneCardWidth),
            )
        }
    }
}

@Composable
private fun PlayAllRow(
    items: List<LocalMediaItem>,
    isVideos: Boolean,
    inset: Dp,
    actions: LocalContentActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = inset, end = inset, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text =
                pluralStringResource(
                    if (isVideos) R.plurals.local_videos_count else R.plurals.local_songs_count,
                    items.size,
                    items.size,
                ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = { actions.onPlay(items, items.indices.random(), true) }) {
            Icon(Icons.Filled.Shuffle, contentDescription = stringResource(R.string.shuffle))
        }
        FilledTonalIconButton(onClick = { actions.onPlay(items, 0, false) }) {
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.play_all))
        }
    }
}
