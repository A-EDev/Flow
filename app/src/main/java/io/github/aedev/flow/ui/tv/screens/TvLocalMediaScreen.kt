package io.github.aedev.flow.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.library.LocalMediaItem
import io.github.aedev.flow.ui.screens.library.LocalMediaViewModel
import io.github.aedev.flow.ui.tv.components.TvLoadingState
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvScreenScaffold
import io.github.aedev.flow.ui.tv.components.TvSectionHeader
import io.github.aedev.flow.ui.tv.components.TvSelectionRow
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import io.github.aedev.flow.utils.formatDuration

/** Device media browser: local videos and music played via content:// URIs. */
@Composable
fun TvLocalMediaScreen(
    onPlayLocal: (Video, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocalMediaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalTvDimens.current

    LaunchedEffect(Unit) {
        viewModel.scan()
    }

    fun LocalMediaItem.toVideo() = Video(
        id = "local-$id",
        title = title,
        channelName = subtitle,
        channelId = "",
        thumbnailUrl = artworkUri.orEmpty(),
        duration = (durationMs / 1_000L).toInt(),
        viewCount = 0L,
        uploadDate = "",
        isMusic = !isVideo,
    )

    TvScreenScaffold(
        title = stringResource(R.string.tv_library_local),
        modifier = modifier,
    ) {
        when {
            state.permissionDenied -> TvMessageState(
                title = stringResource(R.string.tv_local_permission_needed),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimens.overscanHorizontal),
            )
            state.isScanning && !state.hasScanned -> TvLoadingState(Modifier.fillMaxSize())
            state.videos.isEmpty() && state.music.isEmpty() -> TvMessageState(
                title = stringResource(R.string.tv_library_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimens.overscanHorizontal),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = dimens.overscanHorizontal,
                    end = dimens.overscanHorizontal,
                    bottom = dimens.overscanVertical,
                ),
            ) {
                if (state.videos.isNotEmpty()) {
                    item(key = "videos-header") {
                        TvSectionHeader(title = stringResource(R.string.tv_channel_videos))
                    }
                    items(count = state.videos.size, key = { "video:${state.videos[it].id}" }) { index ->
                        val item = state.videos[index]
                        TvSelectionRow(
                            label = item.title,
                            supportingText = listOfNotNull(
                                item.subtitle.takeIf { it.isNotBlank() },
                                formatDuration((item.durationMs / 1_000L).toInt())
                                    .takeIf { item.durationMs > 0 },
                            ).joinToString(" • "),
                            selected = false,
                            onClick = { onPlayLocal(item.toVideo(), item.contentUri) },
                        )
                    }
                }
                if (state.music.isNotEmpty()) {
                    item(key = "music-header") {
                        TvSectionHeader(title = stringResource(R.string.tv_downloads_music))
                    }
                    items(count = state.music.size, key = { "music:${state.music[it].id}" }) { index ->
                        val item = state.music[index]
                        TvSelectionRow(
                            label = item.title,
                            supportingText = item.subtitle,
                            selected = false,
                            onClick = { onPlayLocal(item.toVideo(), item.contentUri) },
                        )
                    }
                }
            }
        }
    }
}
