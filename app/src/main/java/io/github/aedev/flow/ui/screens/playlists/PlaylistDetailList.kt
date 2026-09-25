package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.library.PlaylistVideoRow
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.ReorderableLazyListState
import io.github.aedev.flow.ui.components.shared.animateMediaListItem

/** How the list lets the viewer act on its videos. */
internal class PlaylistListMode(
    val canReorder: Boolean,
    val canModify: Boolean,
    val selectionMode: Boolean,
    val selectedIds: Set<String>,
    val showAddedDate: Boolean,
    val isWatchLater: Boolean,
    val isLikes: Boolean = false,
    /** Set while a search narrows the list; each shown video keeps its number in the whole playlist. */
    val searchQuery: String = "",
    val positions: Map<String, Int> = emptyMap(),
)

/**
 * The playlist's videos under an optional [header]. Positions in [reorderState] count the header
 * as item zero, so the list and the reorder state must agree on whether it is there.
 */
@Composable
internal fun PlaylistDetailList(
    videos: List<Video>,
    mode: PlaylistListMode,
    isLoadingMore: Boolean,
    listState: LazyListState,
    reorderState: ReorderableLazyListState,
    onVideoClick: (index: Int, video: Video) -> Unit,
    onRemove: (Video) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (header != null) item(key = "playlist-header", contentType = "header") { header() }
        if (videos.isEmpty() && mode.searchQuery.isNotBlank()) {
            item(key = "playlist-no-results", contentType = "empty") {
                FlowEmptyState(
                    title = stringResource(R.string.playlist_search_no_results, mode.searchQuery.trim()),
                    icon = Icons.Outlined.SearchOff,
                )
            }
        } else if (videos.isEmpty() && !isLoadingMore) {
            item(key = "playlist-empty", contentType = "empty") {
                when {
                    mode.isLikes -> {
                        FlowEmptyState(
                            title = stringResource(R.string.liked_videos_empty_title),
                            subtitle = stringResource(R.string.liked_videos_empty_body),
                            icon = Icons.Outlined.ThumbUp,
                        )
                    }

                    mode.isWatchLater -> {
                        FlowEmptyState(
                            title = stringResource(R.string.no_videos_saved),
                            subtitle = stringResource(R.string.no_videos_saved_body),
                            icon = Icons.Default.WatchLater,
                        )
                    }

                    else -> {
                        FlowEmptyState(
                            title = stringResource(R.string.playlist_empty_title),
                            subtitle = stringResource(R.string.playlist_empty_desc),
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        )
                    }
                }
            }
        }
        itemsIndexed(items = videos, key = { _, video -> video.id }, contentType = { _, _ -> "playlist-video" }) { index, video ->
            PlaylistVideoRow(
                modifier = if (mode.canReorder) Modifier else animateMediaListItem(),
                video = video,
                position = mode.positions[video.id] ?: (index + 1),
                isSelected = video.id in mode.selectedIds,
                inSelectionMode = mode.selectionMode,
                canModify = mode.canModify,
                reorderModifier = if (mode.canReorder) reorderState.itemModifier(index) else Modifier,
                dragHandleModifier = if (mode.canReorder && !mode.selectionMode) reorderState.handleModifier(index) else Modifier,
                showDragHandle = mode.canReorder,
                showAddedDate = mode.showAddedDate,
                isWatchLater = mode.isWatchLater,
                isLikes = mode.isLikes,
                onRemove = { onRemove(video) },
                onClick = { onVideoClick(index, video) },
            )
        }
        if (isLoadingMore) {
            item(key = "playlist-loading-more", contentType = "loading") {
                FlowLoadingIndicator(modifier = Modifier.padding(vertical = 24.dp))
            }
        }
    }
}
