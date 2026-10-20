package io.github.aedev.flow.ui.components.musicplayer.queue

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.QueueSwipeAction

/** What a swipe on a queue or radio row does. Queue rows take theirs from settings; radio rows are fixed. */
internal enum class QueueRowSwipe {
    PLAY_NEXT,
    ADD_TO_QUEUE,
    MOVE_TO_END,
    REMOVE,
    ;

    /** A removed row leaves the list, so it flies off instead of swinging back. */
    val leavesList: Boolean get() = this == REMOVE
}

/** The swipe set for each side of a queue row. */
@Immutable
internal data class QueueSwipes(
    val left: QueueSwipeAction = QueueSwipeAction.PLAY_NEXT,
    val right: QueueSwipeAction = QueueSwipeAction.REMOVE,
)

internal val QueueSwipeAction.rowSwipe: QueueRowSwipe
    get() =
        when (this) {
            QueueSwipeAction.PLAY_NEXT -> QueueRowSwipe.PLAY_NEXT
            QueueSwipeAction.MOVE_TO_END -> QueueRowSwipe.MOVE_TO_END
            QueueSwipeAction.REMOVE -> QueueRowSwipe.REMOVE
        }

internal val QueueRowSwipe.icon: ImageVector
    get() =
        when (this) {
            QueueRowSwipe.PLAY_NEXT -> Icons.AutoMirrored.Outlined.PlaylistPlay
            QueueRowSwipe.ADD_TO_QUEUE, QueueRowSwipe.MOVE_TO_END -> Icons.AutoMirrored.Outlined.QueueMusic
            QueueRowSwipe.REMOVE -> Icons.Outlined.PlaylistRemove
        }

internal val QueueRowSwipe.labelRes: Int
    get() =
        when (this) {
            QueueRowSwipe.PLAY_NEXT -> R.string.play_next
            QueueRowSwipe.ADD_TO_QUEUE -> R.string.add_to_queue
            QueueRowSwipe.MOVE_TO_END -> R.string.queue_move_to_end
            QueueRowSwipe.REMOVE -> R.string.remove_from_queue
        }

internal val QueueRowSwipe.containerColor: Color
    @Composable get() =
        when (this) {
            QueueRowSwipe.PLAY_NEXT -> MaterialTheme.colorScheme.primaryContainer
            QueueRowSwipe.ADD_TO_QUEUE, QueueRowSwipe.MOVE_TO_END -> MaterialTheme.colorScheme.tertiaryContainer
            QueueRowSwipe.REMOVE -> MaterialTheme.colorScheme.errorContainer
        }

internal val QueueRowSwipe.contentColor: Color
    @Composable get() =
        when (this) {
            QueueRowSwipe.PLAY_NEXT -> MaterialTheme.colorScheme.onPrimaryContainer
            QueueRowSwipe.ADD_TO_QUEUE, QueueRowSwipe.MOVE_TO_END -> MaterialTheme.colorScheme.onTertiaryContainer
            QueueRowSwipe.REMOVE -> MaterialTheme.colorScheme.onErrorContainer
        }
