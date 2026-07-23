package io.github.aedev.flow.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.music.MusicPlaylist
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.screens.music.MusicViewModel
import io.github.aedev.flow.ui.tv.components.TvArtistCard
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.components.TvLoadingState
import io.github.aedev.flow.ui.tv.components.TvMediaRow
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvMusicCard
import io.github.aedev.flow.ui.tv.components.TvMusicCollectionCard
import io.github.aedev.flow.ui.tv.focus.ProvideTvColumnPivot
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import io.github.aedev.flow.utils.formatSubscriberCount

/**
 * Artist page for TV music: avatar header with follow/play/shuffle, then the
 * full mobile data set — popular tracks, albums, singles, videos, featured-on
 * playlists, and related artists — via the shared [MusicViewModel].
 */
@Composable
fun TvArtistScreen(
    channelId: String,
    viewModel: MusicViewModel,
    onTrackClick: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalTvDimens.current

    LaunchedEffect(channelId) { viewModel.fetchArtistDetails(channelId) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.clearArtistDetails() }
    }

    val details = state.artistDetails
    when {
        state.isArtistLoading || details == null -> {
            if (state.isArtistLoading) {
                TvLoadingState(modifier.fillMaxSize())
            } else {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(horizontal = dimens.overscanHorizontal),
                ) {
                    TvMessageState(title = stringResource(R.string.tv_error_loading))
                }
            }
        }

        else -> {
            val topTracks = details.topTracks
            ProvideTvColumnPivot {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        top = dimens.overscanVertical,
                        bottom = dimens.overscanVertical,
                    ),
                ) {
                    item(key = "artist-header") {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = dimens.overscanHorizontal,
                                vertical = 8.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                AsyncImage(
                                    model = details.thumbnailUrl,
                                    contentDescription = details.name,
                                    modifier = Modifier.size(180.dp),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = details.name,
                                    style = MaterialTheme.typography.displaySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (details.subscriberCount > 0) {
                                    Text(
                                        text = stringResource(
                                            R.string.subscribers_count_template,
                                            formatSubscriberCount(details.subscriberCount),
                                        ),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (details.description.isNotBlank()) {
                                    Text(
                                        text = details.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (topTracks.isNotEmpty()) {
                                        TvButton(
                                            text = stringResource(R.string.play),
                                            onClick = {
                                                onTrackClick(topTracks.first(), topTracks, details.name)
                                            },
                                            icon = Icons.Outlined.PlayArrow,
                                        )
                                        TvButton(
                                            text = stringResource(R.string.shuffle),
                                            onClick = {
                                                val shuffled = topTracks.shuffled()
                                                onTrackClick(shuffled.first(), shuffled, details.name)
                                            },
                                            icon = Icons.Outlined.Shuffle,
                                        )
                                    }
                                    TvButton(
                                        text = if (details.isSubscribed) {
                                            stringResource(R.string.subscribed)
                                        } else {
                                            stringResource(R.string.subscribe)
                                        },
                                        onClick = { viewModel.toggleFollowArtist(details) },
                                        icon = if (details.isSubscribed) {
                                            Icons.Outlined.Check
                                        } else {
                                            Icons.Outlined.PersonAddAlt
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (topTracks.isNotEmpty()) {
                        item(key = "artist-popular") {
                            TvMediaRow(
                                items = topTracks,
                                key = MusicTrack::videoId,
                                title = stringResource(R.string.filter_popular),
                            ) { track ->
                                TvMusicCard(
                                    track = track,
                                    onClick = { onTrackClick(track, topTracks, details.name) },
                                )
                            }
                        }
                    }
                    if (details.albums.isNotEmpty()) {
                        item(key = "artist-albums") {
                            TvArtistCollectionShelf(
                                title = stringResource(R.string.filter_albums),
                                playlists = details.albums,
                                onOpenCollection = onOpenCollection,
                            )
                        }
                    }
                    if (details.singles.isNotEmpty()) {
                        item(key = "artist-singles") {
                            TvArtistCollectionShelf(
                                title = stringResource(R.string.section_singles),
                                playlists = details.singles,
                                onOpenCollection = onOpenCollection,
                            )
                        }
                    }
                    if (details.videos.isNotEmpty()) {
                        item(key = "artist-videos") {
                            TvMediaRow(
                                items = details.videos,
                                key = MusicTrack::videoId,
                                title = stringResource(R.string.tab_videos),
                            ) { track ->
                                TvMusicCard(
                                    track = track,
                                    onClick = { onTrackClick(track, details.videos, details.name) },
                                )
                            }
                        }
                    }
                    if (details.featuredOn.isNotEmpty()) {
                        item(key = "artist-featured-on") {
                            TvArtistCollectionShelf(
                                title = stringResource(R.string.section_featured_on),
                                playlists = details.featuredOn,
                                onOpenCollection = onOpenCollection,
                            )
                        }
                    }
                    if (details.relatedArtists.isNotEmpty()) {
                        item(key = "artist-related") {
                            TvMediaRow(
                                items = details.relatedArtists,
                                key = { it.channelId },
                                title = stringResource(R.string.section_fans_also_like),
                            ) { related ->
                                TvArtistCard(
                                    name = related.name,
                                    thumbnailUrl = related.thumbnailUrl,
                                    onClick = { onOpenArtist(related.channelId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvArtistCollectionShelf(
    title: String,
    playlists: List<MusicPlaylist>,
    onOpenCollection: (String) -> Unit,
) {
    TvMediaRow(
        items = playlists,
        key = MusicPlaylist::id,
        title = title,
    ) { playlist ->
        TvMusicCollectionCard(
            title = playlist.title,
            subtitle = playlist.author,
            thumbnailUrl = playlist.thumbnailUrl,
            onClick = { onOpenCollection(playlist.id) },
        )
    }
}
