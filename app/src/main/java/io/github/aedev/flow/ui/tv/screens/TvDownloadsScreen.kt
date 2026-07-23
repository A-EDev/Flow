package io.github.aedev.flow.ui.tv.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.screens.library.DownloadsViewModel
import io.github.aedev.flow.ui.tv.components.TvConfirmDialog
import io.github.aedev.flow.ui.tv.components.TvIconButton
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvScreenScaffold
import io.github.aedev.flow.ui.tv.components.TvSectionHeader
import io.github.aedev.flow.ui.tv.components.TvSelectionRow
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import io.github.aedev.flow.utils.formatDuration
import java.io.File

/**
 * Offline downloads: rows play through the local file paths; deletion is
 * confirmed via [TvConfirmDialog].
 */
@Composable
fun TvDownloadsScreen(
    onPlayLocal: (Video, String) -> Unit,
    onPlayLocalTrack: (MusicTrack, List<MusicTrack>, Map<String, Uri>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalTvDimens.current
    var pendingDelete by remember { mutableStateOf<DownloadedVideo?>(null) }

    pendingDelete?.let { download ->
        TvConfirmDialog(
            title = stringResource(R.string.tv_delete_download_confirm),
            message = download.video.title,
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.deleteVideoDownload(download.video.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    TvScreenScaffold(
        title = stringResource(R.string.tv_library_downloads),
        modifier = modifier,
    ) {
        if (state.downloadedVideos.isEmpty() && state.downloadedMusic.isEmpty()) {
            TvMessageState(
                title = stringResource(R.string.tv_library_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimens.overscanHorizontal),
            )
            return@TvScreenScaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                start = dimens.overscanHorizontal,
                end = dimens.overscanHorizontal,
                bottom = dimens.overscanVertical,
            ),
        ) {
            if (state.downloadedVideos.isNotEmpty()) {
                item(key = "videos-header") {
                    TvSectionHeader(title = stringResource(R.string.tv_channel_videos))
                }
                items(
                    count = state.downloadedVideos.size,
                    key = { "video:${state.downloadedVideos[it].video.id}" },
                ) { index ->
                    val download = state.downloadedVideos[index]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvSelectionRow(
                            label = download.video.title,
                            supportingText = listOfNotNull(
                                download.quality.takeIf { it.isNotBlank() },
                                formatDuration(download.video.duration)
                                    .takeIf { download.video.duration > 0 },
                            ).joinToString(" • "),
                            selected = false,
                            onClick = { onPlayLocal(download.video, download.filePath) },
                            modifier = Modifier.weight(1f),
                        )
                        TvIconButton(
                            icon = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete),
                            onClick = { pendingDelete = download },
                        )
                    }
                }
            }
            if (state.downloadedMusic.isNotEmpty()) {
                item(key = "music-header") {
                    TvSectionHeader(title = stringResource(R.string.tv_downloads_music))
                }
                items(
                    count = state.downloadedMusic.size,
                    key = { "music:${state.downloadedMusic[it].track.videoId}" },
                ) { index ->
                    val download = state.downloadedMusic[index]
                    TvSelectionRow(
                        label = download.track.title,
                        supportingText = download.track.artist,
                        selected = false,
                        onClick = {
                            val queue = state.downloadedMusic.map { it.track }
                            val uris = state.downloadedMusic.associate {
                                it.track.videoId to File(it.filePath).toUri()
                            }
                            onPlayLocalTrack(download.track, queue, uris)
                        },
                    )
                }
            }
        }
    }
}
