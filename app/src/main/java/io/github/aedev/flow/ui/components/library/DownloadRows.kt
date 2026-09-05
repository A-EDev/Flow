package io.github.aedev.flow.ui.components.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.ui.components.library.ActiveDownloadRow
import io.github.aedev.flow.ui.components.library.DownloadsEmptyState
import io.github.aedev.flow.ui.components.library.DownloadsSectionHeader
import io.github.aedev.flow.ui.components.library.MusicDownloadRow
import io.github.aedev.flow.ui.components.library.VideoDownloadRow
import io.github.aedev.flow.ui.components.shared.ArtworkThumbnail
import io.github.aedev.flow.ui.components.shared.ExplicitBadge
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.components.shared.MediaRow
import io.github.aedev.flow.ui.components.shared.MediaRowAction
import io.github.aedev.flow.ui.components.shared.MediaThumbnail
import io.github.aedev.flow.ui.components.shared.animateMediaListItem

private val ProgressBarHeight: Dp = 3.dp
private val SectionHeaderPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
private val EmptyActionWidthFraction = 0.55f
private val EmptyActionHeight: Dp = 48.dp

@Composable
internal fun DownloadsSectionHeader(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(SectionHeaderPadding),
    )
}

@Composable
internal fun VideoDownloadRow(
    video: DownloadedVideo,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaRow(
        title = video.video.title,
        modifier = modifier,
        subtitle = video.video.channelName,
        onClick = onClick,
        trailing = {
            MediaRowAction(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.cd_delete_download, video.video.title),
                onClick = onDeleteClick,
            )
        },
    ) {
        MediaThumbnail(
            videoId = video.video.id,
            thumbnailUrl = video.video.thumbnailUrl,
            durationSeconds = video.video.duration,
        )
    }
}

@Composable
internal fun ActiveDownloadRow(
    download: DownloadWithItems,
    progressMap: Map<String, Float>,
    isMerging: Boolean,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = (progressMap[download.download.videoId] ?: download.progress).coerceIn(0f, 1f)
    val percent = (progress * 100).toInt()
    val isPaused = download.overallStatus == DownloadItemStatus.PAUSED
    val canControl =
        !isMerging &&
            download.overallStatus != DownloadItemStatus.FAILED &&
            download.overallStatus != DownloadItemStatus.CANCELLED

    val status =
        when {
            isMerging -> {
                stringResource(R.string.download_merging_audio_video)
            }

            download.overallStatus == DownloadItemStatus.PENDING -> {
                stringResource(R.string.download_status_queued)
            }

            isPaused -> {
                stringResource(
                    R.string.download_status_paused_template,
                    percent,
                    stringResource(R.string.download_status_paused),
                )
            }

            download.overallStatus == DownloadItemStatus.FAILED -> {
                stringResource(R.string.download_status_failed)
            }

            download.overallStatus == DownloadItemStatus.CANCELLED -> {
                stringResource(R.string.download_status_cancelled)
            }

            else -> {
                stringResource(R.string.download_progress_percent, percent)
            }
        }

    MediaRow(
        title = download.download.title,
        modifier = modifier,
        subtitle = download.download.uploader,
        supporting = status,
        trailing = {
            if (canControl) {
                MediaRowAction(
                    icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription =
                        if (isPaused) {
                            stringResource(R.string.resume)
                        } else {
                            stringResource(R.string.pause)
                        },
                    onClick = if (isPaused) onResumeClick else onPauseClick,
                )
            }
            MediaRowAction(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.cd_delete_download, download.download.title),
                onClick = onDeleteClick,
            )
        },
    ) {
        MediaThumbnail(
            videoId = download.download.videoId,
            thumbnailUrl = download.download.thumbnailUrl,
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(ProgressBarHeight)
                        .align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
internal fun MusicDownloadRow(
    downloadedTrack: DownloadedTrack,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaRow(
        title = downloadedTrack.track.title,
        modifier = modifier,
        subtitle = downloadedTrack.track.artist,
        titleMaxLines = 1,
        onClick = onClick,
        subtitleLeading =
            if (downloadedTrack.track.isExplicit == true) {
                { ExplicitBadge(modifier = Modifier.padding(end = 4.dp)) }
            } else {
                null
            },
        trailing = {
            MediaRowAction(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.cd_delete_download, downloadedTrack.track.title),
                onClick = onDeleteClick,
            )
        },
    ) {
        ArtworkThumbnail(
            thumbnailUrl = downloadedTrack.track.thumbnailUrl,
            placeholder = Icons.Outlined.MusicNote,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadsEmptyState(
    kind: MediaKind,
    onHomeClick: () -> Unit,
) {
    val label = stringResource(kind.labelRes)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        FlowEmptyState(
            title = stringResource(R.string.empty_offline_title, label),
            subtitle = stringResource(R.string.empty_offline_body, label),
            icon = kind.icon,
            action = {
                FilledTonalButton(
                    onClick = onHomeClick,
                    shapes = ButtonDefaults.shapes(),
                    modifier =
                        Modifier
                            .fillMaxWidth(EmptyActionWidthFraction)
                            .height(EmptyActionHeight),
                ) {
                    Text(stringResource(R.string.action_go_to_home))
                }
            },
        )
    }
}

private val ListContentPadding = PaddingValues(vertical = 8.dp)
private val ListItemSpacing = 2.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideosDownloadsList(
    videos: List<DownloadedVideo>,
    incompleteDownloads: List<DownloadWithItems>,
    progressMap: Map<String, Float>,
    mergingVideoIds: Set<String>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onVideoClick: (List<DownloadedVideo>, Int) -> Unit,
    onDeleteClick: (String, String) -> Unit,
    onPauseClick: (String) -> Unit,
    onResumeClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
    ) {
        if (videos.isEmpty() && incompleteDownloads.isEmpty()) {
            DownloadsEmptyState(kind = MediaKind.Videos, onHomeClick = onHomeClick)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = ListContentPadding,
                verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
            ) {
                if (incompleteDownloads.isNotEmpty()) {
                    item(key = "section_active", contentType = "section") {
                        DownloadsSectionHeader(
                            text = stringResource(R.string.section_incomplete_downloads),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(
                        items = incompleteDownloads,
                        key = { "active_${it.download.videoId}" },
                        contentType = { "active" },
                    ) { download ->
                        ActiveDownloadRow(
                            download = download,
                            progressMap = progressMap,
                            isMerging = download.download.videoId in mergingVideoIds,
                            onPauseClick = { onPauseClick(download.download.videoId) },
                            onResumeClick = { onResumeClick(download.download.videoId) },
                            onDeleteClick = {
                                onDeleteClick(download.download.videoId, download.download.title)
                            },
                            modifier = animateMediaListItem(),
                        )
                    }
                    if (videos.isNotEmpty()) {
                        item(key = "section_completed", contentType = "section") {
                            DownloadsSectionHeader(
                                text = stringResource(R.string.section_completed),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                itemsIndexed(
                    items = videos,
                    key = { _, video -> video.video.id },
                    contentType = { _, _ -> "video" },
                ) { index, video ->
                    VideoDownloadRow(
                        video = video,
                        onClick = { onVideoClick(videos, index) },
                        onDeleteClick = { onDeleteClick(video.video.id, video.video.title) },
                        modifier = animateMediaListItem(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicDownloadsList(
    tracks: List<DownloadedTrack>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onMusicClick: (List<DownloadedTrack>, Int) -> Unit,
    onDeleteClick: (String, String) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
    ) {
        if (tracks.isEmpty()) {
            DownloadsEmptyState(kind = MediaKind.Music, onHomeClick = onHomeClick)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = ListContentPadding,
                verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
            ) {
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.track.videoId },
                    contentType = { _, _ -> "track" },
                ) { index, downloadedTrack ->
                    MusicDownloadRow(
                        downloadedTrack = downloadedTrack,
                        onClick = { onMusicClick(tracks, index) },
                        onDeleteClick = {
                            onDeleteClick(downloadedTrack.track.videoId, downloadedTrack.track.title)
                        },
                        modifier = animateMediaListItem(),
                    )
                }
            }
        }
    }
}
