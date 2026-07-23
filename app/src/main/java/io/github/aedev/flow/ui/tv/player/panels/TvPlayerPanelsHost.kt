package io.github.aedev.flow.ui.tv.player.panels

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.state.SubtitleOption
import io.github.aedev.flow.ui.components.VideoThumbnailImage
import io.github.aedev.flow.ui.components.parseHtmlDescription
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.components.TvCard
import io.github.aedev.flow.ui.tv.components.TvSelectionRow
import io.github.aedev.flow.ui.tv.components.TvSidePanel
import io.github.aedev.flow.ui.tv.focus.tvInitialFocus
import io.github.aedev.flow.ui.tv.player.state.TvPlayerPanel
import kotlinx.coroutines.launch

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
    selectedSubtitleUrl: String?,
    onSelectSubtitle: (Int, SubtitleOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    ambientModeEnabled: Boolean,
    onToggleAmbientMode: (Boolean) -> Unit,
    onOpenPanel: (TvPlayerPanel) -> Unit,
    onClosePanel: () -> Unit,
    onDismissPanels: () -> Unit,
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
        TvPlayerPanel.SAVE -> stringResource(R.string.add_to_playlist)
        null -> ""
    }

    TvSidePanel(
        visible = activePanel != null,
        title = title,
        onClose = onDismissPanels,
    ) {
        when (activePanel) {
            TvPlayerPanel.SETTINGS -> TvSettingsMainPage(
                currentQualityLabel = uiState.selectedQuality.label,
                currentSpeed = playerState.playbackSpeed,
                currentAudioLabel = playerState.availableAudioTracks
                    .firstOrNull { it.index == playerState.currentAudioTrack }?.label,
                currentSubtitleLabel = playerState.availableSubtitles
                    .firstOrNull { it.url == selectedSubtitleUrl }?.label,
                subtitlesAvailable = playerState.availableSubtitles.isNotEmpty(),
                audioTracksAvailable = playerState.availableAudioTracks.size > 1,
                autoplayEnabled = uiState.autoplayEnabled,
                onToggleAutoplay = viewModel::toggleAutoplay,
                isLooping = playerState.isLooping,
                onToggleLoop = viewModel::toggleLoop,
                skipSilenceEnabled = playerState.isSkipSilenceEnabled,
                onToggleSkipSilence = viewModel::toggleSkipSilence,
                stableVolumeEnabled = playerState.isStableVolumeEnabled,
                onToggleStableVolume = viewModel::toggleStableVolume,
                ambientModeEnabled = ambientModeEnabled,
                onToggleAmbientMode = onToggleAmbientMode,
                onOpen = onOpenPanel,
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
                subtitles = playerState.availableSubtitles,
                selectedUrl = selectedSubtitleUrl,
                subtitlesEnabled = uiState.subtitlesEnabled,
                onDisable = onDisableSubtitles,
                onSelect = onSelectSubtitle,
            )
            TvPlayerPanel.QUEUE -> TvQueuePanelContent(
                manager = manager,
                onPlayVideo = onPlayVideo,
            )
            TvPlayerPanel.COMMENTS -> TvCommentsPanelContent(
                videoId = video.id,
                viewModel = viewModel,
            )
            TvPlayerPanel.LIVE_CHAT -> TvLiveChatPanelContent(viewModel = viewModel)
            TvPlayerPanel.DESCRIPTION -> TvDescriptionPanelContent(
                viewModel = viewModel,
                onSeekTo = onSeekTo,
            )
            TvPlayerPanel.SAVE -> TvSavePanelContent(video = video)
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
            TvQueueVideoRow(
                video = item,
                selected = index == currentIndex,
                onClick = { onPlayVideo(item) },
                modifier = if (index == 0) Modifier.tvInitialFocus() else Modifier,
            )
        }
    }
}

@Composable
private fun TvQueueVideoRow(
    video: Video,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        selected = selected,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                VideoThumbnailImage(
                    videoId = video.id,
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(width = 96.dp, height = 54.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (video.channelName.isNotBlank()) {
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSavePanelContent(video: Video) {
    val context = LocalContext.current
    val repository = remember { PlaylistRepository(context.applicationContext) }
    val playlists by repository.getAllPlaylistsFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val visible = playlists.filterNot { it.id == PlaylistRepository.SAVED_SHORTS_ID }
    var membership by remember(video.id) { mutableStateOf(emptySet<String>()) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val nameFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(creating) {
        if (creating) {
            kotlinx.coroutines.delay(80)
            runCatching { nameFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(video.id, visible.size) {
        membership = visible
            .filter { repository.isVideoInPlaylist(it.id, video.id) }
            .map { it.id }
            .toSet()
    }

    fun createAndAdd() {
        val name = newName.trim()
        if (name.isEmpty()) return
        scope.launch {
            val playlistId = System.currentTimeMillis().toString()
            repository.createPlaylist(playlistId, name, "", true)
            repository.addVideoToPlaylist(playlistId, video)
            membership = membership + playlistId
            newName = ""
            creating = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "create") {
            if (creating) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.playlist_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvButton(
                            text = stringResource(R.string.create),
                            onClick = ::createAndAdd,
                        )
                        TvButton(
                            text = stringResource(R.string.cancel),
                            onClick = {
                                newName = ""
                                creating = false
                            },
                        )
                    }
                }
            } else {
                TvCard(
                    onClick = { creating = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvInitialFocus(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text(
                            text = stringResource(R.string.new_playlist_button),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
        items(visible, key = { it.id }) { info ->
            val added = info.id in membership
            TvSelectionRow(
                label = info.name,
                supportingText = stringResource(R.string.videos_count_template, info.videoCount),
                selected = added,
                onClick = {
                    scope.launch {
                        if (added) {
                            repository.removeVideoFromPlaylist(info.id, video.id)
                            membership = membership - info.id
                        } else {
                            repository.addVideoToPlaylist(info.id, video)
                            membership = membership + info.id
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun TvCommentsPanelContent(
    videoId: String,
    viewModel: VideoPlayerViewModel,
) {
    val comments by viewModel.commentsState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingComments.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMoreComments.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var expandedIds by remember(videoId) { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(videoId) {
        viewModel.loadComments(videoId)
    }

    if (isLoading && comments.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (comments.isEmpty()) {
        Text(
            text = stringResource(R.string.no_comments_yet),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    // Comment cards are real focus targets, so D-pad moves through them (and
    // scrolls the list) and CENTER expands a comment's replies in place.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(comments, key = { it.id }) { comment ->
            val expanded = comment.id in expandedIds
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TvCommentRow(
                    comment = comment,
                    repliesExpanded = expanded,
                    modifier = if (comment.id == comments.first().id) {
                        Modifier.tvInitialFocus(comments.first().id)
                    } else {
                        Modifier
                    },
                    onClick = {
                        if (comment.replyCount > 0 || comment.replies.isNotEmpty()) {
                            if (expanded) {
                                expandedIds = expandedIds - comment.id
                            } else {
                                expandedIds = expandedIds + comment.id
                                if (comment.replies.isEmpty()) {
                                    viewModel.loadCommentReplies(comment)
                                }
                            }
                        }
                    },
                )
                if (expanded) {
                    if (comment.replies.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 44.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(start = 44.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            comment.replies.forEach { reply ->
                                Column {
                                    Text(
                                        text = reply.author,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = reply.text,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            if (comment.repliesPage != null) {
                                TvCard(onClick = { viewModel.loadMoreCommentReplies(comment) }) {
                                    Text(
                                        text = stringResource(R.string.tv_more_replies),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (hasMore) {
            item(key = "load-more") {
                LaunchedEffect(comments.size) {
                    viewModel.loadMoreComments(videoId)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun TvCommentRow(
    comment: Comment,
    repliesExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        selected = repliesExpanded,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = comment.authorThumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = listOf(comment.author, comment.publishedTime)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (comment.likeCount > 0) {
                        Icon(
                            imageVector = Icons.Outlined.ThumbUp,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = comment.likeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (comment.replyCount > 0) {
                        Text(
                            text = stringResource(R.string.view_replies_template, comment.replyCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .tvInitialFocus()
            .focusable(),
    ) {
        TvLiveChatMessages(uiState = uiState, listState = listState)
    }
}

@Composable
private fun TvLiveChatMessages(
    uiState: io.github.aedev.flow.ui.screens.player.VideoPlayerUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
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
    // Mobile's formatter: strips/styles HTML, highlights links and timestamps.
    val formattedDescription = remember(description) { parseHtmlDescription(description) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val hasChapters = uiState.chapters.isNotEmpty()
    val focusRequester = remember { FocusRequester() }

    // Without focusable chapter rows the panel needs its own D-pad scrolling.
    LaunchedEffect(hasChapters) {
        if (!hasChapters) runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .then(
                if (hasChapters) {
                    Modifier
                } else {
                    Modifier
                        .focusRequester(focusRequester)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    scope.launch { scrollState.animateScrollBy(320f) }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    scope.launch { scrollState.animateScrollBy(-320f) }
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable()
                }
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (description.isNotBlank()) {
            Text(
                text = formattedDescription,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (uiState.chapters.isNotEmpty()) {
            Text(
                text = stringResource(R.string.tv_player_chapters),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.chapters.forEachIndexed { index, chapter ->
                    TvSelectionRow(
                        label = chapter.title.orEmpty(),
                        supportingText = io.github.aedev.flow.utils.formatDuration(chapter.startTimeSeconds),
                        selected = false,
                        onClick = { onSeekTo(chapter.startTimeSeconds * 1_000L) },
                        modifier = if (index == 0) Modifier.tvInitialFocus() else Modifier,
                    )
                }
            }
        }
    }
}
