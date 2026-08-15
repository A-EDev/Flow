package io.github.aedev.flow.player

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What adding a single video did, so the caller can run the follow-up it owns.
 */
internal enum class QueueAddOutcome {
    /** Added to the existing queue. */
    Inserted,

    /** Nothing was queued, so the currently playing video and the new one became the queue. */
    QueueCreated,

    /** Nothing was queued and nothing is playing, so the caller has to start a queue itself. */
    NoActiveQueue,
}

/**
 * Owns the video playback queue: its order, the current position, and the flows the UI observes.
 *
 * Ordering rules live in [PlaylistQueueOrder] and playback side effects (starting a video,
 * preloading the next one, clearing a preloaded window) stay with the caller, so this class never
 * touches a player. Mutations report back what changed and leave the caller to react.
 *
 * Main-thread confined: [EnhancedPlayerManager] posts every mutation to the main looper before it
 * reaches here, so the fields are deliberately unsynchronised, as the state they replaced was.
 */
internal class PlaybackQueueController {
    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndexState: StateFlow<Int> = _currentIndex.asStateFlow()

    /** Pre-shuffle order, kept so shuffle can be turned back off. */
    private var originalItems: List<Video> = emptyList()

    var title: String? = null
        private set

    var loopEnabled: Boolean = false
        private set

    var shuffleEnabled: Boolean = false
        private set

    private val items: List<Video>
        get() = _videos.value

    val size: Int
        get() = items.size

    val isEmpty: Boolean
        get() = items.isEmpty()

    val currentIndex: Int
        get() = _currentIndex.value

    val currentVideo: Video?
        get() = items.getOrNull(currentIndex)

    val hasPrevious: Boolean
        get() = currentIndex > 0

    val hasNext: Boolean
        get() = nextIndex() != null

    fun videoAt(index: Int): Video? = items.getOrNull(index)

    fun isCurrent(videoId: String): Boolean = currentVideo?.id == videoId

    fun nextIndex(): Int? =
        PlaylistQueueOrder.nextIndex(
            itemCount = items.size,
            currentIndex = currentIndex,
            loopEnabled = loopEnabled,
        )

    fun nextVideo(): Video? = nextIndex()?.let(items::getOrNull)

    /**
     * Replaces the queue, honouring the current shuffle setting.
     *
     * @return the video playback should start from, or null when [videos] is empty.
     */
    fun setQueue(
        videos: List<Video>,
        startIndex: Int,
        title: String?,
    ): Video? {
        originalItems = videos
        val normalizedStartIndex = startIndex.coerceIn(0, videos.lastIndex.coerceAtLeast(0))
        val ordered =
            if (shuffleEnabled && videos.size > 1) {
                PlaylistQueueOrder.shuffleFromCurrent(videos, normalizedStartIndex)
            } else {
                ReorderedQueue(videos, normalizedStartIndex)
            }
        this.title = title
        publish(ordered.items, if (videos.isEmpty()) -1 else ordered.currentIndex)
        return currentVideo
    }

    /**
     * Moves the current position to [index].
     *
     * @return the video now current, or null when [index] is out of range.
     */
    fun moveTo(index: Int): Video? {
        val video = items.getOrNull(index) ?: return null
        _currentIndex.value = index
        return video
    }

    fun movePrevious(): Video? = if (hasPrevious) moveTo(currentIndex - 1) else null

    /** Inserts [video] right after the current position (Play Next). */
    fun addNext(
        video: Video,
        currentlyPlaying: Video?,
    ): QueueAddOutcome {
        if (isEmpty) return startQueueFrom(currentlyPlaying, video)

        val current = currentVideo
        val originalInsertAt =
            originalItems
                .indexOfFirst { it.id == current?.id }
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: originalItems.size
        originalItems =
            originalItems.toMutableList().apply {
                add(originalInsertAt.coerceIn(0, size), video)
            }
        _videos.value = items.toMutableList().apply { add(currentIndex + 1, video) }
        return QueueAddOutcome.Inserted
    }

    /** Appends [video] to the end of the queue. */
    fun append(
        video: Video,
        currentlyPlaying: Video?,
    ): QueueAddOutcome {
        if (isEmpty) return startQueueFrom(currentlyPlaying, video)

        originalItems = originalItems + video
        _videos.value = items + video
        return QueueAddOutcome.Inserted
    }

    /** @return whether [index] was a removable position, i.e. in range and not the current one. */
    fun removeAt(index: Int): Boolean {
        val removal = PlaylistQueueOrder.removeAt(items, currentIndex, index) ?: return false
        originalItems =
            PlaylistQueueOrder.removeMatching(
                items = originalItems,
                target = removal.removedItem,
                keySelector = Video::id,
            )
        publish(removal.queue.items, removal.queue.currentIndex)
        return true
    }

    /** @return whether the move was applied. */
    fun move(
        fromIndex: Int,
        toIndex: Int,
    ): Boolean {
        val reordered =
            PlaylistQueueOrder.move(
                items = items,
                currentIndex = currentIndex,
                fromIndex = fromIndex,
                toIndex = toIndex,
            ) ?: return false

        // While shuffled, the pre-shuffle order has to survive a manual reorder untouched.
        if (!shuffleEnabled) originalItems = reordered.items
        publish(reordered.items, reordered.currentIndex)
        return true
    }

    fun setLoopEnabled(enabled: Boolean) {
        loopEnabled = enabled
    }

    /** @return whether this changed anything, i.e. there was a non-empty queue to reorder. */
    fun setShuffleEnabled(enabled: Boolean): Boolean {
        if (shuffleEnabled == enabled || isEmpty) return false

        val reordered =
            if (enabled) {
                originalItems = items
                PlaylistQueueOrder.shuffleFromCurrent(items, currentIndex)
            } else {
                PlaylistQueueOrder.restoreOriginal(
                    original = originalItems,
                    currentItem = currentVideo,
                    keySelector = Video::id,
                )
            }
        shuffleEnabled = enabled
        publish(reordered.items, reordered.currentIndex)
        return true
    }

    fun clear() {
        originalItems = emptyList()
        title = null
        loopEnabled = false
        shuffleEnabled = false
        publish(emptyList(), currentIndex = -1)
    }

    private fun startQueueFrom(
        currentlyPlaying: Video?,
        video: Video,
    ): QueueAddOutcome {
        if (currentlyPlaying == null) return QueueAddOutcome.NoActiveQueue
        originalItems = listOf(currentlyPlaying, video)
        publish(originalItems, currentIndex = 0)
        return QueueAddOutcome.QueueCreated
    }

    private fun publish(
        items: List<Video>,
        currentIndex: Int,
    ) {
        _videos.value = items
        _currentIndex.value = currentIndex
    }
}
