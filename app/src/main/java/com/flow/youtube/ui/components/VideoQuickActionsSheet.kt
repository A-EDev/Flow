package com.flow.youtube.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.R
import com.flow.youtube.data.model.Video

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQuickActionsBottomSheet(
    video: Video,
    onDismiss: () -> Unit,
    onWatchLater: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onDownload: (() -> Unit)? = null,
    onNotInterested: () -> Unit = {},
    onChannelClick: ((String) -> Unit)? = null,
    viewModel: QuickActionsViewModel = hiltViewModel()
) {
    val watchLaterIds by viewModel.watchLaterIds.collectAsState()
    val isInWatchLater = remember(watchLaterIds, video.id) { watchLaterIds.contains(video.id) }

    val watchedVideoIds by viewModel.watchedVideoIds.collectAsState()
    val isWatched = remember(watchedVideoIds, video.id) { watchedVideoIds.contains(video.id) }

    val subscribedChannelIds by viewModel.subscribedChannelIds.collectAsState()
    val isSubscribed = remember(subscribedChannelIds, video.channelId) {
        subscribedChannelIds.contains(video.channelId)
    }

    // Load subscription state when sheet opens
    LaunchedEffect(video.channelId) {
        if (video.channelId.isNotBlank()) {
            viewModel.loadSubscriptionState(video.channelId)
        }
    }

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showMediaInfo by remember { mutableStateOf(false) }

    // Dialogs
    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            video = video,
            onDismiss = {
                showAddToPlaylistDialog = false
                onDismiss()
            }
        )
    }

    if (showMediaInfo) {
        MediaInfoDialog(
            video = video,
            onDismiss = { showMediaInfo = false }
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Video info header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Action Grid — Save, Watch Later, Share
            item {
                FlowActionGrid(
                    actions = listOf(
                        FlowAction(
                            icon = { Icon(Icons.Outlined.PlaylistAdd, null) },
                            text = stringResource(R.string.save_to_playlist),
                            onClick = { showAddToPlaylistDialog = true }
                        ),
                        FlowAction(
                            icon = {
                                Icon(
                                    if (isInWatchLater) Icons.Default.WatchLater else Icons.Outlined.WatchLater,
                                    null,
                                    tint = if (isInWatchLater) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = if (isInWatchLater) stringResource(R.string.watch_later_unsave)
                            else stringResource(R.string.watch_later),
                            onClick = {
                                if (onWatchLater != null) {
                                    onWatchLater()
                                    onDismiss()
                                } else {
                                    viewModel.toggleWatchLater(video)
                                }
                            }
                        ),
                        FlowAction(
                            icon = { Icon(Icons.Outlined.Share, null) },
                            text = stringResource(R.string.share),
                            onClick = {
                                onShare()
                                onDismiss()
                            }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Channel Group — Go to channel, Subscribe
            if (video.channelId.isNotBlank()) {
                item {
                    Text(
                        text = stringResource(R.string.section_channel),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                    FlowMenuGroup(
                        items = listOf(
                            FlowMenuItemData(
                                icon = { Icon(Icons.Outlined.VideoLibrary, null) },
                                title = { Text(stringResource(R.string.go_to_channel)) },
                                onClick = {
                                    onChannelClick?.invoke(video.channelId)
                                    onDismiss()
                                }
                            ),
                            FlowMenuItemData(
                                icon = {
                                    Icon(
                                        if (isSubscribed) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                                        null,
                                        tint = if (isSubscribed) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                        title = {
                                    Text(
                                        if (isSubscribed) stringResource(R.string.subscribed)
                                        else stringResource(R.string.subscribe)
                                    )
                                },
                                onClick = {
                                    viewModel.toggleSubscription(
                                        channelId = video.channelId,
                                        channelName = video.channelName,
                                        channelThumbnail = video.thumbnailUrl
                                    )
                                }
                            )
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            // Playback Queue Group — Play Next, Add to Queue
            item {
                Text(
                    text = stringResource(R.string.playback_header),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                FlowMenuGroup(
                    items = listOf(
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.QueueMusic, null) },
                            title = { Text(stringResource(R.string.play_next_video)) },
                            description = { Text(stringResource(R.string.play_next_video_desc)) },
                            onClick = {
                                viewModel.playVideoNext(video)
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.play_next_toast),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }
                        ),
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.PlaylistPlay, null) },
                            title = { Text(stringResource(R.string.add_video_to_queue)) },
                            description = { Text(stringResource(R.string.add_video_to_queue_desc)) },
                            onClick = {
                                viewModel.addVideoToQueue(video)
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.added_to_queue_toast),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Algorithm Group — Mark as watched, I like this, Not interested
            item {
                Text(
                    text = stringResource(R.string.section_algorithm),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                FlowMenuGroup(
                    items = listOf(
                        FlowMenuItemData(
                            icon = {
                                Icon(
                                    if (isWatched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                    null,
                                    tint = if (isWatched) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            title = { Text(stringResource(R.string.mark_as_watched)) },
                            onClick = {
                                viewModel.markAsWatched(video)
                            }
                        ),
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.ThumbUp, null) },
                            title = { Text(stringResource(R.string.i_like_this)) },
                            onClick = {
                                viewModel.markAsInteresting(video)
                                onDismiss()
                            }
                        ),
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.ThumbDown, null) },
                            title = { Text(stringResource(R.string.not_interested)) },
                            onClick = {
                                viewModel.markNotInterested(video)
                                onNotInterested()
                                onDismiss()
                            }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Utility Group — Download, Details
            item {
                Text(
                    text = stringResource(R.string.section_options),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                FlowMenuGroup(
                    items = listOf(
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.Download, null) },
                            title = { Text(stringResource(R.string.download)) },
                            onClick = {
                                if (onDownload != null) {
                                    onDownload()
                                } else {
                                    viewModel.downloadVideo(video)
                                }
                                onDismiss()
                            }
                        ),
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.Info, null) },
                            title = { Text(stringResource(R.string.details_metadata)) },
                            onClick = {
                                showMediaInfo = true
                            }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

