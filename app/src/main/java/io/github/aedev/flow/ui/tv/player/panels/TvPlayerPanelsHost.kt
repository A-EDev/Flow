package io.github.aedev.flow.ui.tv.player.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.SleepTimerManager
import io.github.aedev.flow.ui.components.CommentSortFilter
import io.github.aedev.flow.ui.components.FlowCommentsList
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.tv.components.TvSelectionRow
import io.github.aedev.flow.ui.tv.components.TvSidePanel
import io.github.aedev.flow.ui.tv.components.TvVideoCard
import io.github.aedev.flow.ui.tv.player.state.TvPlayerPanel

/**
 * Hosts every player side panel inside the shared [TvSidePanel] scaffold.
 * Panel navigation (open/close/back) is owned by the overlay controller;
 * this composable only renders the active panel's content.
 */
@Composable
fun BoxScope.TvPlayerPanelsHost(
    activePanel: TvPlayerPanel?,
    video: Video,
    viewModel: VideoPlayerViewModel,
    manager: EnhancedPlayerManager,
    onOpenPanel: (TvPlayerPanel) -> Unit,
    onClosePanel: () -> Unit,
    onPlayVideo: (Video) -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    val playerState by manager.playerState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Live chat lifecycle follows the panel.
    LaunchedEffect(activePanel) {
        if (activePanel == TvPlayerPanel.LIVE_CHAT) {
            viewModel.maybeStartLiveChat(video.id)
        } else {
            viewModel.stopLiveChat()
        }
    }

    val title = when (activePanel) {
        TvPlayerPanel.SETTINGS -> stringResource(R.string.tv_player_settings)
        TvPlayerPanel.QUALITY -> stringResource(R.string.quality)
        TvPlayerPanel.SPEED -> stringResource(R.string.playback_speed)
        TvPlayerPanel.AUDIO -> stringResource(R.string.audio_track)
        TvPlayerPanel.SUBTITLES -> stringResource(R.string.filter_subtitles)
        TvPlayerPanel.QUEUE -> uiState.queueTitle ?: stringResource(R.string.tv_player_queue)
        TvPlayerPanel.COMMENTS -> stringResource(R.string.comments)
        TvPlayerPanel.LIVE_CHAT -> stringResource(R.string.live_chat)
        TvPlayerPanel.DESCRIPTION -> stringResource(R.string.description)
        TvPlayerPanel.SLEEP_TIMER -> stringResource(R.string.sleep_timer)
        null -> ""
    }

    TvSidePanel(
        visible = activePanel != null,
        title = title,
    ) {
        when (activePanel) {
            TvPlayerPanel.SETTINGS -> TvSettingsMainPage(
                currentQualityLabel = uiState.selectedQuality.label,
                currentSpeed = playerState.playbackSpeed,
                currentAudioLabel = playerState.availableAudioTracks
                    .firstOrNull { it.index == playerState.currentAudioTrack }?.label,
                currentSubtitleLabel = uiState.selectedSubtitle?.language,
                onOpen = onOpenPanel,
                subtitlesAvailable = uiState.subtitles.isNotEmpty(),
                audioTracksAvailable = playerState.availableAudioTracks.size > 1,
            )
            TvPlayerPanel.QUALITY -> TvQualityPage(
                qualities = uiState.availableQualities,
                selected = uiState.selectedQuality,
                onSelect = {
                    viewModel.switchQuality(it)
                    onClosePanel()
                },
            )
            TvPlayerPanel.SPEED -> TvSpeedPage(
                currentSpeed = playerState.playbackSpeed,
                onSelect = {
                    manager.setPlaybackSpeed(it)
                    onClosePanel()
                },
            )
            TvPlayerPanel.AUDIO -> TvAudioPage(
                tracks = playerState.availableAudioTracks,
                currentIndex = playerState.currentAudioTrack,
                onSelect = {
                    manager.switchAudioTrack(it.index)
                    onClosePanel()
                },
            )
            TvPlayerPanel.SUBTITLES -> TvSubtitlesPage(
                subtitles = uiState.subtitles,
                selected = uiState.selectedSubtitle,
                subtitlesEnabled = uiState.subtitlesEnabled,
                onToggle = viewModel::toggleSubtitles,
                onSelect = {
                    viewModel.selectSubtitleTrack(it)
                    viewModel.toggleSubtitles(true)
                },
            )
            TvPlayerPanel.QUEUE -> TvQueuePanelContent(
                manager = manager,
                onPlayVideo = onPlayVideo,
            )
            TvPlayerPanel.COMMENTS -> TvCommentsPanelContent(
                videoId = video.id,
                viewModel = viewModel,
                onSeekTo = onSeekTo,
            )
            TvPlayerPanel.LIVE_CHAT -> TvLiveChatPanelContent(viewModel = viewModel)
            TvPlayerPanel.DESCRIPTION -> TvDescriptionPanelContent(
                viewModel = viewModel,
                onSeekTo = onSeekTo,
            )
            TvPlayerPanel.SLEEP_TIMER -> TvSleepTimerPanelContent(onDone = onClosePanel)
            null -> Unit
        }
    }
}

@Composable
private fun TvQueuePanelContent(
    manager: EnhancedPlayerManager,
    onPlayVideo: (Video) -> Unit,
) {
    val queue by manager.queueVideos.collectAsStateWithLifecycle()
    val currentIndex by manager.currentQueueIndexState.collectAsStateWithLifecycle()

    if (queue.isEmpty()) {
        Text(
            text = stringResource(R.string.tv_library_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(queue, key = { index, item -> "$index-${item.id}" }) { index, item ->
            TvSelectionRow(
                label = item.title,
                supportingText = item.channelName.takeIf { it.isNotBlank() },
                selected = index == currentIndex,
                onClick = { onPlayVideo(item) },
            )
        }
    }
}

@Composable
private fun TvCommentsPanelContent(
    videoId: String,
    viewModel: VideoPlayerViewModel,
    onSeekTo: (Long) -> Unit,
) {
    val comments by viewModel.commentsState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingComments.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMoreComments.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(videoId) {
        viewModel.loadComments(videoId)
    }

    FlowCommentsList(
        comments = comments,
        isLoading = isLoading,
        listState = listState,
        selectedFilter = CommentSortFilter.TOP,
        onTimestampClick = { timestamp -> parseTimestampMs(timestamp)?.let(onSeekTo) },
        onLoadReplies = viewModel::loadCommentReplies,
        onLoadMoreReplies = viewModel::loadMoreCommentReplies,
        onAuthorClick = {},
        onAvatarClick = {},
        isLoadingMore = false,
        onLoadMore = { viewModel.loadMoreComments(videoId) },
        hasMore = hasMore,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TvLiveChatPanelContent(viewModel: VideoPlayerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.liveChatMessages.size) {
        if (uiState.liveChatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.liveChatMessages.lastIndex)
        }
    }

    when {
        uiState.isLiveChatLoading && uiState.liveChatMessages.isEmpty() -> Text(
            text = stringResource(R.string.live_chat_connecting),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        uiState.liveChatMessages.isEmpty() -> Text(
            text = stringResource(R.string.live_chat_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(
                uiState.liveChatMessages,
                key = { index, message -> "$index-${message.id}" },
            ) { _, message ->
                Column {
                    Text(
                        text = message.author,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (message.isOwner || message.isModerator) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvDescriptionPanelContent(
    viewModel: VideoPlayerViewModel,
    onSeekTo: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val description = uiState.streamInfo?.description?.content.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (uiState.chapters.isNotEmpty()) {
            Text(
                text = stringResource(R.string.tv_player_chapters),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.chapters.forEach { chapter ->
                    TvSelectionRow(
                        label = chapter.title.orEmpty(),
                        supportingText = io.github.aedev.flow.utils.formatDuration(chapter.startTimeSeconds),
                        selected = false,
                        onClick = { onSeekTo(chapter.startTimeSeconds * 1_000L) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSleepTimerPanelContent(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(15, 30, 45, 60, 90).forEach { minutes ->
            TvSelectionRow(
                label = stringResource(R.string.tv_sleep_minutes, minutes),
                selected = false,
                onClick = {
                    SleepTimerManager.start(minutes)
                    onDone()
                },
            )
        }
        TvSelectionRow(
            label = stringResource(R.string.tv_sleep_end_of_video),
            selected = false,
            onClick = {
                SleepTimerManager.startEndOfMedia()
                onDone()
            },
        )
        TvSelectionRow(
            label = stringResource(R.string.tv_sleep_cancel_timer),
            selected = false,
            onClick = {
                SleepTimerManager.cancel()
                onDone()
            },
        )
    }
}

/** Parses "m:ss" / "h:mm:ss" comment timestamps into milliseconds. */
private fun parseTimestampMs(timestamp: String): Long? {
    val parts = timestamp.trim().split(":").map { it.toIntOrNull() ?: return null }
    return when (parts.size) {
        2 -> (parts[0] * 60L + parts[1]) * 1_000L
        3 -> (parts[0] * 3_600L + parts[1] * 60L + parts[2]) * 1_000L
        else -> null
    }
}
