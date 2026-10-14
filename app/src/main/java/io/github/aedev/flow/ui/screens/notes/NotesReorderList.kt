package io.github.aedev.flow.ui.screens.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.notes.Note
import io.github.aedev.flow.data.notes.NoteKind
import io.github.aedev.flow.ui.components.layout.flowContentPadding
import io.github.aedev.flow.ui.components.shared.ChannelAvatarImage
import io.github.aedev.flow.ui.components.shared.MediaRow
import io.github.aedev.flow.ui.components.shared.MediaThumbnail
import io.github.aedev.flow.ui.components.shared.ReorderHandle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val ReorderThumbnailWidth = 112.dp
private val ReorderAvatarSize = 48.dp

/** Every note with a drag handle; the new order is saved each time a drag ends. */
@Composable
internal fun NotesReorderList(
    notes: List<Note>,
    onOrderChanged: (List<Note>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // Held locally so a drop shows its result at once instead of snapping back until Room emits.
    var ordered by remember(notes) { mutableStateOf(notes) }
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }

    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (!reorderState.isAnyItemDragging && ordered.map { it.key } != notes.map { it.key }) onOrderChanged(ordered)
    }

    fun move(
        from: Int,
        to: Int,
    ): Boolean {
        if (to !in ordered.indices) return false
        ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
        onOrderChanged(ordered)
        return true
    }

    val moveUpLabel = stringResource(R.string.move_up)
    val moveDownLabel = stringResource(R.string.move_down)
    val channelNoteLabel = stringResource(R.string.note_channel_note)
    val unknownVideoLabel = stringResource(R.string.note_unknown_video)
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = flowContentPadding()) {
        itemsIndexed(ordered, key = { _, note -> note.key }) { index, note ->
            ReorderableItem(reorderState, key = note.key) { isDragging ->
                val itemScope = this
                Surface(
                    color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    val isVideo = note.kind == NoteKind.Video
                    MediaRow(
                        title = note.subject?.title ?: if (isVideo) unknownVideoLabel else channelNoteLabel,
                        subtitle = if (isVideo) note.subject?.channelName else channelNoteLabel,
                        titleMaxLines = 1,
                        modifier =
                            Modifier.semantics {
                                customActions =
                                    buildList {
                                        if (index > 0) add(CustomAccessibilityAction(moveUpLabel) { move(index, index - 1) })
                                        if (index <
                                            ordered.lastIndex
                                        ) {
                                            add(CustomAccessibilityAction(moveDownLabel) { move(index, index + 1) })
                                        }
                                    }
                            },
                        trailing = {
                            Box(
                                modifier =
                                    with(itemScope) {
                                        Modifier
                                            .size(LocalMinimumInteractiveComponentSize.current)
                                            .draggableHandle(
                                                onDragStarted = {
                                                    haptics.performHapticFeedback(
                                                        HapticFeedbackType.GestureThresholdActivate,
                                                    )
                                                },
                                                onDragStopped = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                                            )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                ReorderHandle()
                            }
                        },
                    ) {
                        if (isVideo) {
                            MediaThumbnail(
                                videoId = note.targetId,
                                thumbnailUrl = note.subject?.thumbnailUrl,
                                width = ReorderThumbnailWidth,
                            )
                        } else {
                            ChannelAvatarImage(
                                url = note.subject?.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.size(ReorderAvatarSize).clip(CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}
