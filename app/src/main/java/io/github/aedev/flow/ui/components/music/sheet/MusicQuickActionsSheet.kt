package io.github.aedev.flow.ui.components.music.sheet

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.*
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.components.music.sheet.AddToPlaylistDialog
import io.github.aedev.flow.ui.components.music.sheet.CreatePlaylistDialog
import io.github.aedev.flow.ui.components.shared.rememberFlowSheetState
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicQuickActionsSheet(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onViewArtist: (String) -> Unit = {},
    onViewAlbum: (String) -> Unit = {},
    onShare: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onAudioEffectsClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    showPlaylistDialogs: Boolean = true,
    viewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showMediaInfo by remember { mutableStateOf(false) }
    var showArtistSelection by remember { mutableStateOf(false) }

    // Dialogs
    if (showPlaylistDialogs && uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.showCreatePlaylistDialog(false) },
            onConfirm = { name, desc ->
                viewModel.createPlaylist(name, desc, track)
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
            artists = track.artists ?: emptyList(),
            onArtistSelected = { channelId ->
                onViewArtist(channelId)
            },
            onDismiss = { showArtistSelection = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        ) {
            // Header
            item {
                MusicTrackItem(
                    track = track,
                    onClick = {},
                    showMenu = false,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Quick Actions Grid
            item {
                val isDownloaded = uiState.downloadedTrackIds.contains(track.videoId)

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
                                    val downloadTint =
                                        if (isDownloaded) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    Icon(
                                        if (isDownloaded) Icons.Outlined.CheckCircle else Icons.Outlined.Download,
                                        null,
                                        tint = downloadTint,
                                    )
                                },
                                text = if (isDownloaded) stringResource(R.string.downloaded) else stringResource(R.string.download),
                                onClick = {
                                    if (!isDownloaded) viewModel.downloadTrack(track)
                                    onDismiss()
                                },
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

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Playback Options Group
            item {
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
                            FlowMenuItemData(
                                icon = { Icon(Icons.Outlined.Bedtime, null) },
                                title = { Text(stringResource(R.string.sleep_timer)) },
                                onClick = {
                                    onSleepTimerClick()
                                    onDismiss()
                                },
                            ),
                        ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Recommendation feedback — the two-layer escalation from the music brain:
            // "not interested" soft-suppresses the artist, a repeat (or the explicit
            // action) blocks permanently. Reversible in the Control Center.
            item {
                val artistName = track.artists.firstOrNull()?.name ?: track.artist
                FlowMenuSectionHeader(stringResource(R.string.recommendations_header))
                FlowMenuGroup(
                    items =
                        listOf(
                            FlowMenuItemData(
                                icon = { Icon(Icons.Outlined.ThumbDown, null, tint = MaterialTheme.colorScheme.error) },
                                title = { Text(stringResource(R.string.not_interested), color = MaterialTheme.colorScheme.error) },
                                description = { Text(stringResource(R.string.not_interested_desc)) },
                                onClick = {
                                    viewModel.notInterested(track)
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.feedback_not_interested_applied, artistName),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    onDismiss()
                                },
                            ),
                            FlowMenuItemData(
                                icon = { Icon(Icons.Outlined.Block, null, tint = MaterialTheme.colorScheme.error) },
                                title = {
                                    Text(
                                        stringResource(R.string.dont_recommend_artist, artistName),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                description = { Text(stringResource(R.string.dont_recommend_artist_desc)) },
                                onClick = {
                                    viewModel.dontRecommendArtist(track)
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.feedback_artist_blocked, artistName),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    onDismiss()
                                },
                            ),
                        ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Info & Navigation Group
            item {
                FlowMenuSectionHeader(stringResource(R.string.more_header))
                FlowMenuGroup(
                    items =
                        listOfNotNull(
                            if ((track.channelId?.isNotEmpty() == true) || (track.artists?.isNotEmpty() == true)) {
                                FlowMenuItemData(
                                    icon = { Icon(Icons.Outlined.Person, null) },
                                    title = { Text(stringResource(R.string.view_artist)) },
                                    description = { Text(track.artist ?: "") },
                                    onClick = {
                                        if ((track.artists?.size ?: 0) > 1) {
                                            showArtistSelection = true
                                        } else {
                                            val artistId = track.artists?.firstOrNull()?.id ?: track.channelId
                                            if (artistId?.isNotEmpty() == true) {
                                                onViewArtist(artistId)
                                                onDismiss()
                                            }
                                        }
                                    },
                                )
                            } else {
                                null
                            },
                            if (!track.albumId.isNullOrEmpty()) {
                                FlowMenuItemData(
                                    icon = { Icon(Icons.Outlined.Album, null) },
                                    title = { Text(stringResource(R.string.view_album)) },
                                    description = { Text(track.album) },
                                    onClick = {
                                        onViewAlbum(track.albumId.orEmpty())
                                        onDismiss()
                                    },
                                )
                            } else {
                                null
                            },
                            FlowMenuItemData(
                                icon = { Icon(Icons.Outlined.Info, null) },
                                title = { Text(stringResource(R.string.details_metadata)) },
                                onClick = {
                                    showMediaInfo = true
                                    // Do not dismiss so user can come back or dismiss manually
                                },
                            ),
                        ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
