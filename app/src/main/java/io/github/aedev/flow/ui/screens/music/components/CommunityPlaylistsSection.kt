package io.github.aedev.flow.ui.screens.music.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.SectionTitle
import io.github.aedev.flow.ui.screens.music.CommunityMusicPlaylist
import io.github.aedev.flow.ui.screens.music.MusicTrack

@Composable
fun CommunityPlaylistsSection(
    playlists: List<CommunityMusicPlaylist>,
    onPlaylistClick: (CommunityMusicPlaylist) -> Unit,
    onPlaylistAction: (CommunityMusicPlaylist) -> Unit = {},
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit = {},
    downloadedTrackIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(title = stringResource(R.string.section_from_the_community))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            items(playlists) { item ->
                CommunityPlaylistCard(
                    item = item,
                    onPlaylistClick = { onPlaylistClick(item) },
                    onPlaylistAction = { onPlaylistAction(item) },
                    onTrackClick = { track -> onTrackClick(track, item.tracks) },
                    onTrackMenu = onTrackMenu,
                    downloadedTrackIds = downloadedTrackIds
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommunityPlaylistCard(
    item: CommunityMusicPlaylist,
    onPlaylistClick: () -> Unit,
    onPlaylistAction: () -> Unit = {},
    onTrackClick: (MusicTrack) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit = {},
    downloadedTrackIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(328.dp)
            .height(420.dp)
            .combinedClickable(
                onClick = onPlaylistClick,
                onLongClick = onPlaylistAction
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MosaicThumbnail(
                    tracks = item.tracks,
                    modifier = Modifier.size(104.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.playlist.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.playlist.author.ifBlank {
                            item.playlist.trackCount.takeIf { it > 0 }?.let { "$it tracks" } ?: ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPlaylistAction) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                item.tracks.take(3).forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .combinedClickable(
                                onClick = { onTrackClick(track) },
                                onLongClick = { onTrackMenu(track) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(MaterialTheme.shapes.medium)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (downloadedTrackIds.contains(track.videoId)) {
                            Icon(
                                imageVector = Icons.Rounded.OfflinePin,
                                contentDescription = stringResource(R.string.status_downloaded),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                FilledTonalIconButton(onClick = { item.tracks.firstOrNull()?.let(onTrackClick) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_all))
                }
                FilledTonalIconButton(onClick = onPlaylistAction) {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = stringResource(R.string.add_to_library))
                }
                FilledTonalIconButton(onClick = onPlaylistClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun MosaicThumbnail(
    tracks: List<MusicTrack>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clip(MaterialTheme.shapes.medium)) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(2) { row ->
                Row(modifier = Modifier.weight(1f)) {
                    repeat(2) { col ->
                        val track = tracks.getOrNull(row * 2 + col)
                        AsyncImage(
                            model = track?.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
