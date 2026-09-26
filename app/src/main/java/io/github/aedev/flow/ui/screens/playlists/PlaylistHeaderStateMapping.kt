package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.DownloadBatch
import io.github.aedev.flow.ui.components.library.PlaylistHeaderState
import io.github.aedev.flow.ui.components.shared.mediaLengthLabel

/**
 * What the header shows for this playlist: owner, count, total length and what kind of playlist it
 * is on one line, and which actions it offers. The length waits until every page has arrived.
 */
@Composable
internal fun rememberPlaylistHeaderState(
    uiState: PlaylistDetailUiState,
    videos: List<Video>,
    downloadBatch: DownloadBatch?,
): PlaylistHeaderState {
    val isUserCreated = uiState.isLocalPlaylist && !uiState.isSaved
    val totalSeconds = remember(videos) { videos.sumOf { it.duration.coerceAtLeast(0).toLong() } }
    val parts =
        listOfNotNull(
            uiState.ownerName?.takeIf(String::isNotBlank),
            pluralStringResource(R.plurals.videos_count_template, videos.size, videos.size),
            mediaLengthLabel(totalSeconds).takeUnless { uiState.isLoadingMore },
            when {
                uiState.isWatchLater || uiState.isLikes -> stringResource(R.string.playlist_type_builtin)
                isUserCreated -> stringResource(R.string.playlist_type_yours)
                uiState.isSaved -> stringResource(R.string.playlist_type_saved)
                else -> null
            },
        )
    val separator = stringResource(R.string.metadata_separator)
    return PlaylistHeaderState(
        name = uiState.playlistName,
        metadata = parts.joinToString(" $separator "),
        description = uiState.description,
        thumbnailUrl = videos.firstOrNull()?.thumbnailUrl ?: uiState.thumbnailUrl,
        isSaved = uiState.isSaved,
        canSave = !isUserCreated,
        canAddAll = !isUserCreated,
        canEdit = isUserCreated && !uiState.isWatchLater && !uiState.isLikes,
        canExport = uiState.isLocalPlaylist,
        downloadProgress = downloadBatch?.takeUnless { it.isFinished }?.let { it.processed.toFloat() / it.total },
    )
}
