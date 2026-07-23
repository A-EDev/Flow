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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.components.TvMediaGrid
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvScreenScaffold
import io.github.aedev.flow.ui.tv.components.TvVideoCard
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/** Local playlist detail: Play all / Shuffle plus the playlist's video grid. */
@Composable
fun TvPlaylistDetailScreen(
    playlistId: String,
    onVideoClick: (Video) -> Unit,
    onPlayPlaylist: (List<Video>, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playlistRepository = remember { PlaylistRepository(context.applicationContext) }
    val playlists by playlistRepository.getAllPlaylistsFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val videos by remember(playlistId) { playlistRepository.getPlaylistVideosFlow(playlistId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val dimens = LocalTvDimens.current

    TvScreenScaffold(
        title = playlist?.name ?: stringResource(R.string.tv_library_playlists),
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
                    val title = playlist?.name.orEmpty()
                    TvButton(
                        text = stringResource(R.string.play_all),
                        onClick = { onPlayPlaylist(videos, title) },
                        icon = Icons.Outlined.PlayArrow,
                    )
                    TvButton(
                        text = stringResource(R.string.shuffle),
                        onClick = { onPlayPlaylist(videos.shuffled(), title) },
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
                TvMediaGrid(
                    items = videos,
                    key = Video::id,
                ) { video ->
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
