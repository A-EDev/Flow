package io.github.aedev.flow.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.CollectionEditDialog
import io.github.aedev.flow.ui.components.shared.CollectionSheetEntry
import io.github.aedev.flow.ui.components.shared.SaveToCollectionSheet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AddToPlaylistDialog(
    video: Video,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { PlaylistRepository(context) }

    var playlists by remember { mutableStateOf<List<PlaylistInfo>>(emptyList()) }
    var watchLaterVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistsLoaded by remember { mutableStateOf(false) }
    var watchLaterLoaded by remember { mutableStateOf(false) }
    var savedIds by remember(video.id) { mutableStateOf<Set<String>>(emptySet()) }
    var selectionInitialized by remember(video.id) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            repo.getAllPlaylistsFlow().collect { all ->
                playlists = all.filter { it.id != PlaylistRepository.WATCH_LATER_ID }
                playlistsLoaded = true
            }
        }
        launch {
            repo.getWatchLaterVideosFlow().collect {
                watchLaterVideos = it
                watchLaterLoaded = true
            }
        }
    }

    LaunchedEffect(playlistsLoaded, watchLaterLoaded, playlists, watchLaterVideos, video.id) {
        if (playlistsLoaded && watchLaterLoaded && !selectionInitialized) {
            val existing =
                playlists
                    .filter { playlist ->
                        repo.getPlaylistVideosFlow(playlist.id).first().any { it.id == video.id }
                    }.mapTo(HashSet()) { it.id }
            if (watchLaterVideos.any { it.id == video.id }) {
                existing += PlaylistRepository.WATCH_LATER_ID
            }
            savedIds = existing
            selectionInitialized = true
        }
    }

    val watchLaterEntry =
        CollectionSheetEntry(
            id = PlaylistRepository.WATCH_LATER_ID,
            name = stringResource(R.string.watch_later),
            supporting =
                pluralStringResource(
                    R.plurals.videos_count_template,
                    watchLaterVideos.size,
                    watchLaterVideos.size,
                ),
            thumbnailUrl = watchLaterVideos.firstOrNull()?.thumbnailUrl.orEmpty(),
            isSaved = PlaylistRepository.WATCH_LATER_ID in savedIds,
        )
    val entries =
        listOf(watchLaterEntry) +
            playlists.map { playlist ->
                CollectionSheetEntry(
                    id = playlist.id,
                    name = playlist.name,
                    supporting =
                        pluralStringResource(
                            R.plurals.videos_count_template,
                            playlist.videoCount,
                            playlist.videoCount,
                        ),
                    thumbnailUrl = playlist.thumbnailUrl,
                    isSaved = playlist.id in savedIds,
                )
            }

    SaveToCollectionSheet(
        title = stringResource(R.string.save_to),
        entries = entries,
        placeholderIcon = Icons.AutoMirrored.Outlined.PlaylistPlay,
        createLabel = stringResource(R.string.create_new_playlist),
        emptyLabel = stringResource(R.string.no_playlists_found),
        onToggle = { entry ->
            if (!selectionInitialized) return@SaveToCollectionSheet
            val wasSaved = entry.isSaved
            savedIds = if (wasSaved) savedIds - entry.id else savedIds + entry.id
            scope.launch {
                runCatching {
                    when {
                        entry.id == PlaylistRepository.WATCH_LATER_ID && wasSaved -> {
                            repo.removeFromWatchLater(video.id)
                        }

                        entry.id == PlaylistRepository.WATCH_LATER_ID -> {
                            repo.addToWatchLater(video)
                        }

                        wasSaved -> {
                            repo.removeVideoFromPlaylist(entry.id, video.id)
                        }

                        else -> {
                            repo.addVideoToPlaylist(entry.id, video)
                        }
                    }
                }.onFailure {
                    savedIds = if (wasSaved) savedIds + entry.id else savedIds - entry.id
                }
            }
        },
        onCreateNew = { showCreateDialog = true },
        onDismiss = onDismiss,
    )

    if (showCreateDialog) {
        CollectionEditDialog(
            title = stringResource(R.string.create_new_playlist),
            confirmLabel = stringResource(R.string.create),
            icon = Icons.Default.PlaylistAdd,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description ->
                scope.launch {
                    val playlistId = System.currentTimeMillis().toString()
                    repo.createPlaylist(playlistId, name, description, true)
                    repo.addVideoToPlaylist(playlistId, video)
                    savedIds = savedIds + playlistId
                    showCreateDialog = false
                }
            },
        )
    }
}
