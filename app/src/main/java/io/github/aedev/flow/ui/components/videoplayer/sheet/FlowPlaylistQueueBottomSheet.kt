package io.github.aedev.flow.ui.components.videoplayer.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.FlowBottomSheet
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.defaultSheetExpandedHeight
import io.github.aedev.flow.ui.components.shared.mediaLengthLabel
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
    enableVerticalDismiss: Boolean = true,
    onSheetProgressChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberFlowBottomSheetState()
    val listState = rememberLazyListState()
    val displayItems =
        remember(queueVideos) {
            queueVideos
                .zip(queueRowKeys(queueVideos)) { video, key -> QueueDisplayItem(key = key, video = video) }
                .toMutableStateList()
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
        dismissible = enableVerticalDismiss,
        dismissOnOutsideTap = false,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        onProgressChange = onSheetProgressChange,
        header = { dragModifier ->
            FlowSheetHeader(
                inSidePane = !enableVerticalDismiss,
                title = playlistTitle ?: stringResource(R.string.playlist_queue),
                subtitle = queueSubtitle(queueVideos, currentQueueIndex),
                onClose = { sheetState.dismiss() },
                modifier = dragModifier,
                titleMaxLines = 1,
                actions = {
                    QueueModeToggle(
                        checked = isShuffled,
                        onCheckedChange = onShuffleToggle,
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                    )
                    QueueModeToggle(
                        checked = isLooping,
                        onCheckedChange = onLoopToggle,
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = stringResource(R.string.repeat),
                    )
                },
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
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(displayItems, key = { _, item -> item.key }) { index, item ->
                val isPlaying = item === currentDisplayItem
                PlaylistQueueItem(
                    video = item.video,
                    isPlaying = isPlaying,
                    isPlayed = index < currentQueueIndex,
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

@Composable
private fun queueSubtitle(
    queueVideos: List<Video>,
    currentQueueIndex: Int,
): String {
    val position = stringResource(R.string.queue_position_template, currentQueueIndex + 1, queueVideos.size)
    val secondsAfter =
        remember(queueVideos, currentQueueIndex) {
            queueVideos.drop(currentQueueIndex + 1).sumOf { it.duration.coerceAtLeast(0).toLong() }
        }
    val after = mediaLengthLabel(secondsAfter) ?: return position
    return "$position ${stringResource(R.string.metadata_separator)} ${stringResource(R.string.queue_after_this_template, after)}"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QueueModeToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = IconButtonDefaults.toggleableShapes(),
    ) {
        Icon(imageVector = imageVector, contentDescription = contentDescription)
    }
}
