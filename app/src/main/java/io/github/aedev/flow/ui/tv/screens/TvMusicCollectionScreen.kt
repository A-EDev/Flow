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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
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
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.screens.music.MusicViewModel
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.components.TvLoadingState
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvMusicTrackRow
import io.github.aedev.flow.ui.tv.focus.ProvideTvColumnPivot
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * Album / playlist detail page for TV music: large art header with Play All
 * and Shuffle, then the focusable track list. Content comes from the shared
 * [MusicViewModel.fetchPlaylistDetails] (local playlists fast-path included).
 */
@Composable
fun TvMusicCollectionScreen(
    collectionId: String,
    viewModel: MusicViewModel,
    onTrackClick: (MusicTrack, List<MusicTrack>, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalTvDimens.current

    LaunchedEffect(collectionId) { viewModel.fetchPlaylistDetails(collectionId) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.clearPlaylistDetails() }
    }

    val details = state.playlistDetails
    when {
        state.isPlaylistLoading || (details == null && state.error == null) ->
            TvLoadingState(modifier.fillMaxSize())

        details == null -> Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = dimens.overscanHorizontal),
        ) {
            TvMessageState(title = stringResource(R.string.error_failed_to_load_playlist))
        }

        else -> {
            val tracks = details.tracks
            ProvideTvColumnPivot {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = dimens.overscanHorizontal,
                        end = dimens.overscanHorizontal,
                        top = dimens.overscanVertical,
                        bottom = dimens.overscanVertical,
                    ),
                ) {
                    item(key = "collection-header") {
                        Row(
                            modifier = Modifier.padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                AsyncImage(
                                    model = details.thumbnailUrl,
                                    contentDescription = details.title,
                                    modifier = Modifier.size(220.dp),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = details.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val metadata = listOfNotNull(
                                    details.author.takeIf { it.isNotBlank() },
                                    stringResource(
                                        R.string.tracks_count_template,
                                        tracks.size.takeIf { it > 0 } ?: details.trackCount,
                                    ),
                                ).joinToString(" • ")
                                Text(
                                    text = metadata,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (tracks.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        TvButton(
                                            text = stringResource(R.string.play_all),
                                            onClick = {
                                                onTrackClick(tracks.first(), tracks, details.title)
                                            },
                                            icon = Icons.Outlined.PlayArrow,
                                        )
                                        TvButton(
                                            text = stringResource(R.string.shuffle),
                                            onClick = {
                                                val shuffled = tracks.shuffled()
                                                onTrackClick(shuffled.first(), shuffled, details.title)
                                            },
                                            icon = Icons.Outlined.Shuffle,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (tracks.isEmpty()) {
                        item(key = "collection-empty") {
                            TvMessageState(title = stringResource(R.string.tv_music_empty))
                        }
                    } else {
                        itemsIndexed(
                            items = tracks,
                            key = { index, track -> "$index:${track.videoId}" },
                        ) { _, track ->
                            TvMusicTrackRow(
                                track = track,
                                onClick = { onTrackClick(track, tracks, details.title) },
                            )
                        }
                    }
                }
            }
        }
    }
}
