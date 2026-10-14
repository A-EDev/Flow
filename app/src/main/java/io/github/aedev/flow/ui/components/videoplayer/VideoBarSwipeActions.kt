package io.github.aedev.flow.ui.components.videoplayer

import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.MiniBarSwipeAction
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.components.shared.MiniBarSwipeCommit
import io.github.aedev.flow.ui.components.shared.MiniBarSwipeMotion
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionUndo
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsViewModel
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val QUEUE_MOVE_TIMEOUT_MS = 2_000L

/**
 * The swipe actions of the background video bar. Next and previous go through the same calls as the
 * notification, which keep a background video audio-only; the rest reuse the quick actions.
 */
internal class VideoBarSwipeActions(
    private val video: () -> Video?,
    private val quickActions: QuickActionsViewModel,
    private val playerViewModel: VideoPlayerViewModel,
    private val scope: CoroutineScope,
    private val removedFromQueue: String,
    private val onClose: () -> Unit,
) {
    private val manager get() = EnhancedPlayerManager.getInstance()

    /** The video [action] would move to, for the peek under the swipe; null for every other action. */
    fun peek(action: MiniBarSwipeAction): Video? =
        when (action) {
            MiniBarSwipeAction.NEXT -> manager.nextSessionVideo()
            MiniBarSwipeAction.PREVIOUS -> previousQueueVideo()
            else -> null
        }

    fun isAvailable(action: MiniBarSwipeAction): Boolean =
        when (action) {
            MiniBarSwipeAction.NEXT -> manager.nextSessionVideo() != null
            MiniBarSwipeAction.PREVIOUS -> previousQueueVideo() != null
            MiniBarSwipeAction.REMOVE_FROM_QUEUE -> manager.currentQueueIndexState.value >= 0 && manager.hasNext()
            else -> video() != null
        }

    fun commit(action: MiniBarSwipeAction): MiniBarSwipeCommit? {
        if (!isAvailable(action)) return null
        val current = video() ?: return null
        return when (action) {
            MiniBarSwipeAction.CLOSE -> {
                MiniBarSwipeCommit(MiniBarSwipeMotion.FlyOff, onClose)
            }

            MiniBarSwipeAction.NEXT -> {
                MiniBarSwipeCommit(MiniBarSwipeMotion.FlyThrough) { manager.skipToNextFromSession() }
            }

            MiniBarSwipeAction.PREVIOUS -> {
                MiniBarSwipeCommit(MiniBarSwipeMotion.FlyThrough) { manager.playPrevious() }
            }

            MiniBarSwipeAction.REMOVE_FROM_QUEUE -> {
                MiniBarSwipeCommit(MiniBarSwipeMotion.FlyThrough) { skipAndRemoveCurrent() }
            }

            MiniBarSwipeAction.WATCH_LATER -> {
                MiniBarSwipeCommit(MiniBarSwipeMotion.SpringBack) {
                    if (current.id in quickActions.watchLaterIds.value) {
                        quickActions.announce(R.string.toast_added_to_watch_later)
                    } else {
                        quickActions.toggleWatchLater(current)
                    }
                }
            }

            MiniBarSwipeAction.LIKE -> {
                MiniBarSwipeCommit(MiniBarSwipeMotion.SpringBack) {
                    playerViewModel.likeVideo(current.id, current.title, current.thumbnailUrl, current.channelName, current.channelId)
                    quickActions.announce(R.string.liked)
                }
            }

            MiniBarSwipeAction.DOWNLOAD -> {
                MiniBarSwipeCommit(MiniBarSwipeMotion.SpringBack) { quickActions.requestDownload(current) }
            }

            MiniBarSwipeAction.NOT_INTERESTED -> {
                MiniBarSwipeCommit(MiniBarSwipeMotion.SpringBack) { quickActions.markNotInterested(current) }
            }
        }
    }

    private fun previousQueueVideo(): Video? {
        val index = manager.currentQueueIndexState.value
        return if (index > 0) manager.queueVideos.value.getOrNull(index - 1) else null
    }

    /** The playing video cannot leave the queue, so the queue moves on first and the old entry goes after. */
    private fun skipAndRemoveCurrent() {
        val index = manager.currentQueueIndexState.value
        if (!manager.skipToNextFromSession()) return
        scope.launch {
            withTimeoutOrNull(QUEUE_MOVE_TIMEOUT_MS) { manager.currentQueueIndexState.first { it != index } }
            manager.removeVideoAtIndex(index)?.let { removed ->
                quickActions.announce(removedFromQueue, QuickActionUndo.QueueRemoval(removed))
            }
        }
    }
}
