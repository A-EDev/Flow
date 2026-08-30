package io.github.aedev.flow.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.music.AddToPlaylistDialog
import io.github.aedev.flow.ui.screens.music.CreatePlaylistDialog
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.screens.music.MusicTrackRow

@Composable
fun MusicQuickActionsSheet(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onViewArtist: (String) -> Unit = {},
    onViewAlbum: (String) -> Unit = {},
    onShare: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onAudioEffectsClick: () -> Unit = {},
    showPlaylistDialogs: Boolean = true,
    viewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMediaInfo by remember { mutableStateOf(false) }
    var showArtistSelection by remember { mutableStateOf(false) }

    if (showPlaylistDialogs && uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.showCreatePlaylistDialog(false) },
            onConfirm = { name, description ->
                viewModel.createPlaylist(name, description, track)
            },
        )
    }

    if (showPlaylistDialogs && uiState.showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = uiState.playlists,
            onDismiss = { viewModel.showAddToPlaylistDialog(false) },
            onSelectPlaylist = { playlistId ->
                viewModel.addToPlaylist(playlistId, track)
            },
            onCreateNew = {
                viewModel.showAddToPlaylistDialog(false)
                viewModel.showCreatePlaylistDialog(true)
            },
        )
    }

    if (showMediaInfo) {
        MediaInfoDialog(
            track = track,
            onDismiss = { showMediaInfo = false },
        )
    }

    if (showArtistSelection) {
        ArtistSelectionDialog(
            artists = track.artists,
            onArtistSelected = onViewArtist,
            onDismiss = { showArtistSelection = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        ) {
            item(key = "track_header") {
                MusicTrackRow(
                    track = track,
                    onClick = {},
                    trailingContent = {},
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            item(key = "quick_actions") {
                val isDownloaded = track.videoId in uiState.downloadedTrackIds
                FlowActionGrid(
                    actions =
                        listOf(
                            FlowAction(
                                icon = { Icon(Icons.Outlined.PlaylistAdd, null) },
                                text = stringResource(R.string.add_to_playlist),
                                onClick = { viewModel.showAddToPlaylistDialog(true) },
                            ),
                            FlowAction(
                                icon = {
                                    Icon(
                                        imageVector =
                                            if (isDownloaded) {
                                                Icons.Outlined.CheckCircle
                                            } else {
                                                Icons.Outlined.Download
                                            },
                                        contentDescription = null,
                                    )
                                },
                                text =
                                    if (isDownloaded) {
                                        stringResource(R.string.downloaded)
                                    } else {
                                        stringResource(R.string.download)
                                    },
                                onClick = {
                                    viewModel.downloadTrack(track)
                                    onDismiss()
                                },
                                enabled = !isDownloaded,
                            ),
                            FlowAction(
                                icon = { Icon(Icons.Outlined.Share, null) },
                                text = stringResource(R.string.share),
                                onClick = {
                                    onShare()
                                    onDismiss()
                                },
                            ),
                        ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item(key = "playback_spacer") { Spacer(modifier = Modifier.height(8.dp)) }

            item(key = "playback_actions") {
                FlowMenuSectionHeader(stringResource(R.string.playback_header))
                FlowMenuGroup(
                    items =
                        listOf(
                            FlowMenuItemData(
                                icon = { Icon(Icons.Outlined.QueueMusic, null) },
                                title = { Text(stringResource(R.string.play_next)) },
                                description = { Text(stringResource(R.string.play_next_desc)) },
                                onClick = {
                                    viewModel.playNext(track)
                                    onDismiss()
                                },
                            ),
                            FlowMenuItemData(
                                icon = { Icon(Icons.Outlined.PlaylistPlay, null) },
                                title = { Text(stringResource(R.string.add_to_queue)) },
                                description = { Text(stringResource(R.string.add_to_queue_desc)) },
                                onClick = {
                                    viewModel.addToQueue(track)
                                    onDismiss()
                                },
                            ),
                            FlowMenuItemData(
                                icon = { Icon(Icons.Default.GraphicEq, null) },
                                title = { Text(stringResource(R.string.audio_effects)) },
                                onClick = {
                                    onAudioEffectsClick()
                                    onDismiss()
                                },
                            ),
                        ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "more_spacer") { Spacer(modifier = Modifier.height(16.dp)) }

            item(key = "more_actions") {
                FlowMenuSectionHeader(stringResource(R.string.more_header))
                FlowMenuGroup(
                    items =
                        listOfNotNull(
                            if (track.channelId.isNotEmpty() || track.artists.isNotEmpty()) {
                                FlowMenuItemData(
                                    icon = { Icon(Icons.Outlined.Person, null) },
                                    title = { Text(stringResource(R.string.view_artist)) },
                                    description = { Text(track.artist) },
                                    onClick = {
                                        if (track.artists.size > 1) {
                                            showArtistSelection = true
                                        } else {
                                            val artistId = track.artists.firstOrNull()?.id ?: track.channelId
                                            if (artistId.isNotEmpty()) {
                                                onViewArtist(artistId)
                                                onDismiss()
                                            }
                                        }
                                    },
                                )
                            } else {
                                null
                            },
                            track.albumId?.takeIf { it.isNotEmpty() }?.let { albumId ->
                                FlowMenuItemData(
                                    icon = { Icon(Icons.Outlined.Album, null) },
                                    title = { Text(stringResource(R.string.view_album)) },
                                    description = { Text(track.album) },
                                    onClick = {
                                        onViewAlbum(albumId)
                                        onDismiss()
                                    },
                                )
                            },
                            FlowMenuItemData(
                                icon = { Icon(Icons.Outlined.Info, null) },
                                title = { Text(stringResource(R.string.details_metadata)) },
                                onClick = {
                                    onInfoClick()
                                    showMediaInfo = true
                                },
                            ),
                        ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
