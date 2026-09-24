package io.github.aedev.flow.ui.components.shared.quickactions

import androidx.annotation.StringRes
import io.github.aedev.flow.data.local.entity.PlaylistVideoCrossRef
import io.github.aedev.flow.data.model.Video

/**
 * A confirmation for the app's snackbar: [text] formatted with [arg], or [plainText] already
 * resolved by a screen, and an [undo] when the action has one.
 */
data class QuickActionMessage(
    @StringRes val text: Int = 0,
    val arg: String? = null,
    val undo: QuickActionUndo? = null,
    val plainText: String? = null,
)

/** How to put an action back. Each carries the state to restore, not the state that was set. */
sealed interface QuickActionUndo {
    data class WatchLater(
        val video: Video,
        val saved: Boolean,
    ) : QuickActionUndo

    data class Subscription(
        val channelId: String,
        val channelName: String,
        val channelThumbnail: String,
        val subscribed: Boolean,
    ) : QuickActionUndo

    data class ChannelBlock(
        val channelId: String,
    ) : QuickActionUndo

    data class PlaylistRemoval(
        val entries: List<PlaylistVideoCrossRef>,
    ) : QuickActionUndo
}
