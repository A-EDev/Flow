package io.github.aedev.flow.ui.components.videoplayer.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.FlowBottomSheet
import io.github.aedev.flow.ui.components.shared.defaultSheetExpandedHeight
import io.github.aedev.flow.ui.components.shared.rememberFlowBottomSheetState
import io.github.aedev.flow.ui.components.shared.rememberReorderableLazyListState

private class QueueDisplayItem(
    val key: String,
    val video: Video,
)

@Composable
fun FlowPlaylistQueueBottomSheet(
    queueVideos: List<Video>,
    currentQueueIndex: Int,
    playlistTitle: String?,
    isLooping: Boolean,
    isShuffled: Boolean,
    onLoopToggle: (Boolean) -> Unit,
    onShuffleToggle: (Boolean) -> Unit,
    onPlayVideoAtIndex: (Int) -> Unit,
    onRemoveVideoAtIndex: (Int) -> Unit,
    onMoveVideoAtIndex: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
    onSheetProgressChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberFlowBottomSheetState()
    val listState = rememberLazyListState()
    val displayItems =
        remember(queueVideos) {
            queueVideos
                .mapIndexed { index, video ->
                    QueueDisplayItem(
                        key = "$index:${video.id}",
                        video = video,
                    )
                }.toMutableStateList()
        }
    val currentDisplayItem =
        remember(queueVideos, currentQueueIndex) {
            displayItems.getOrNull(currentQueueIndex)
        }
    var pendingMoveFrom by remember(queueVideos) { mutableStateOf<Int?>(null) }
    var pendingMoveTo by remember(queueVideos) { mutableStateOf<Int?>(null) }
    val reorderState =
        rememberReorderableLazyListState(
            listState = listState,
            onMove = { fromIndex, toIndex ->
                if (pendingMoveFrom == null) {
                    pendingMoveFrom = fromIndex
                }
                pendingMoveTo = toIndex
                displayItems.add(toIndex, displayItems.removeAt(fromIndex))
            },
            onDragStopped = {
                val fromIndex = pendingMoveFrom
                val toIndex = pendingMoveTo
                pendingMoveFrom = null
                pendingMoveTo = null
                if (fromIndex != null && toIndex != null) {
                    onMoveVideoAtIndex(fromIndex, toIndex)
                }
            },
        )

    LaunchedEffect(currentQueueIndex) {
        if (currentQueueIndex >= 0 && currentQueueIndex < queueVideos.size) {
            listState.scrollToItem(currentQueueIndex)
        }
    }

    FlowBottomSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        state = sheetState,
        expandedHeight = expandedHeight ?: defaultSheetExpandedHeight(),
        collapsedHeight = collapsedHeight,
        dismissOnOutsideTap = false,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        onProgressChange = onSheetProgressChange,
        header = { dragModifier ->
            // Not FlowSheetHeader: this is the only sheet whose title truncates to one line and
            // whose subtitle is bodyMedium, and neither reads as a shared header parameter.
            QueueSheetHeader(
                playlistTitle = playlistTitle,
                currentQueueIndex = currentQueueIndex,
                queueSize = queueVideos.size,
                isLooping = isLooping,
                isShuffled = isShuffled,
                onLoopToggle = onLoopToggle,
                onShuffleToggle = onShuffleToggle,
                onClose = { sheetState.dismiss() },
                dragModifier = dragModifier,
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            itemsIndexed(displayItems, key = { _, item -> item.key }) { index, item ->
                val isPlaying = item === currentDisplayItem
                PlaylistQueueItem(
                    video = item.video,
                    isPlaying = isPlaying,
                    reorderModifier = reorderState.itemModifier(index),
                    dragHandleModifier = reorderState.handleModifier(index),
                    onClick = { onPlayVideoAtIndex(index) },
                    onRemove =
                        if (isPlaying) {
                            null
                        } else {
                            { onRemoveVideoAtIndex(index) }
                        },
                    onMoveUp =
                        if (index > 0) {
                            { onMoveVideoAtIndex(index, index - 1) }
                        } else {
                            null
                        },
                    onMoveDown =
                        if (index < displayItems.lastIndex) {
                            { onMoveVideoAtIndex(index, index + 1) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheetHeader(
    playlistTitle: String?,
    currentQueueIndex: Int,
    queueSize: Int,
    isLooping: Boolean,
    isShuffled: Boolean,
    onLoopToggle: (Boolean) -> Unit,
    onShuffleToggle: (Boolean) -> Unit,
    onClose: () -> Unit,
    dragModifier: Modifier,
) {
    Column(modifier = dragModifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            BottomSheetDefaults.DragHandle()
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlistTitle ?: stringResource(R.string.playlist_queue),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        stringResource(
                            R.string.queue_position_template,
                            currentQueueIndex + 1,
                            queueSize,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QueueModeIconButton(
                selected = isShuffled,
                onClick = { onShuffleToggle(!isShuffled) },
                imageVector = Icons.Default.Shuffle,
                contentDescription = stringResource(R.string.shuffle),
            )
            QueueModeIconButton(
                selected = isLooping,
                onClick = { onLoopToggle(!isLooping) },
                imageVector = Icons.Default.Repeat,
                contentDescription = stringResource(R.string.repeat),
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    }
}
