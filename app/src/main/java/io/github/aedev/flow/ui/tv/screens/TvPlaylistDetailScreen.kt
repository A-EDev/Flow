package io.github.aedev.flow.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.playlists.PlaylistDetailViewModel
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.components.TvMediaGrid
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvScreenScaffold
import io.github.aedev.flow.ui.tv.components.TvVideoCard
import io.github.aedev.flow.ui.tv.focus.tvInitialFocus
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * Playlist detail backed by the shared [PlaylistDetailViewModel], which
 * resolves local playlists first, then remote YouTube playlists, then music
 * playlists — so search/channel playlists open here too.
 */
@Composable
fun TvPlaylistDetailScreen(
    onVideoClick: (Video) -> Unit,
    onPlayPlaylist: (List<Video>, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val videos = state.videos
    val dimens = LocalTvDimens.current

    TvScreenScaffold(
        title = state.playlistName.ifBlank { stringResource(R.string.tv_library_playlists) },
        modifier = modifier,
        subtitle = stringResource(R.string.videos_count_template, videos.size),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (videos.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.overscanHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvButton(
                        text = stringResource(R.string.play_all),
                        onClick = { onPlayPlaylist(videos, state.playlistName) },
                        icon = Icons.Outlined.PlayArrow,
                        modifier = Modifier.tvInitialFocus(videos.isNotEmpty()),
                    )
                    TvButton(
                        text = stringResource(R.string.shuffle),
                        onClick = { onPlayPlaylist(videos.shuffled(), state.playlistName) },
                        icon = Icons.Outlined.Shuffle,
                    )
                }
            }

            if (videos.isEmpty()) {
                TvMessageState(
                    title = stringResource(R.string.tv_library_empty),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = dimens.overscanHorizontal),
                )
            } else {
                val indexed = videos.mapIndexed { index, video -> index to video }
                TvMediaGrid(
                    items = indexed,
                    key = { (index, video) -> "$index:${video.id}" },
                ) { (_, video) ->
                    TvVideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
