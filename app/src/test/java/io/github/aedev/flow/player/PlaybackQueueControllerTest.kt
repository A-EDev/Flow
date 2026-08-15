package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

/**
 * The queue decides what plays next and what the queue sheet shows, and the two have to agree: an
 * index that drifts from the published list makes the sheet highlight one video and play another.
 * None of this was reachable from a test while the state lived inside `EnhancedPlayerManager`.
 */
class PlaybackQueueControllerTest {
    private fun video(id: String) =
        Video(
            id = id,
            title = "Video $id",
            channelName = "Channel",
            channelId = "UC123",
            thumbnailUrl = "https://example.test/$id.jpg",
            duration = 120,
            viewCount = 10L,
            uploadDate = "today",
        )

    private fun ids(videos: List<Video>) = videos.map(Video::id)

    private fun controllerOf(
        vararg ids: String,
        startIndex: Int = 0,
    ): PlaybackQueueController =
        PlaybackQueueController().apply {
            setQueue(ids.map(::video), startIndex, title = "Playlist")
        }

    @Test
    fun `setQueue starts at the requested index and publishes the queue`() {
        val controller = PlaybackQueueController()

        val start = controller.setQueue(listOf(video("a"), video("b"), video("c")), startIndex = 1, title = "Playlist")

        assertThat(start?.id).isEqualTo("b")
        assertThat(controller.currentIndex).isEqualTo(1)
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "c").inOrder()
        assertThat(controller.currentIndexState.value).isEqualTo(1)
        assertThat(controller.title).isEqualTo("Playlist")
    }

    @Test
    fun `setQueue on an empty list parks the index instead of pointing at a video`() {
        val controller = PlaybackQueueController()

        val start = controller.setQueue(emptyList(), startIndex = 0, title = null)

        assertThat(start).isNull()
        assertThat(controller.currentIndex).isEqualTo(-1)
        assertThat(controller.isEmpty).isTrue()
    }

    @Test
    fun `setQueue while shuffled plays the tapped video and publishes the order it plays`() {
        val controller = controllerOf("a", "b", "c")
        controller.setShuffleEnabled(true)

        val start = controller.setQueue(listOf(video("x"), video("y"), video("z")), startIndex = 2, title = null)

        // The published list, the index and the video handed back must describe the same item, or
        // the queue sheet highlights one row and playback starts on another.
        assertThat(start?.id).isEqualTo("z")
        assertThat(controller.videos.value[controller.currentIndex].id).isEqualTo("z")
        assertThat(ids(controller.videos.value)).containsExactly("x", "y", "z")
    }

    @Test
    fun `moveTo out of range leaves the position alone`() {
        val controller = controllerOf("a", "b", "c", startIndex = 1)

        assertThat(controller.moveTo(5)).isNull()
        assertThat(controller.moveTo(-1)).isNull()
        assertThat(controller.currentIndex).isEqualTo(1)
    }

    @Test
    fun `movePrevious stops at the head of the queue`() {
        val controller = controllerOf("a", "b", startIndex = 1)

        assertThat(controller.movePrevious()?.id).isEqualTo("a")
        assertThat(controller.movePrevious()).isNull()
        assertThat(controller.currentIndex).isEqualTo(0)
    }

    @Test
    fun `hasNext follows the loop setting at the end of the queue`() {
        val controller = controllerOf("a", "b", startIndex = 1)

        assertThat(controller.hasNext).isFalse()
        assertThat(controller.nextVideo()).isNull()

        controller.setLoopEnabled(true)

        assertThat(controller.hasNext).isTrue()
        assertThat(controller.nextVideo()?.id).isEqualTo("a")
    }

    @Test
    fun `addNext inserts straight after the current video`() {
        val controller = controllerOf("a", "b", "c", startIndex = 1)

        val outcome = controller.addNext(video("x"), currentlyPlaying = null)

        assertThat(outcome).isEqualTo(QueueAddOutcome.Inserted)
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "x", "c").inOrder()
        assertThat(controller.currentVideo?.id).isEqualTo("b")
    }

    @Test
    fun `append adds to the end without moving the current video`() {
        val controller = controllerOf("a", "b", startIndex = 1)

        val outcome = controller.append(video("x"), currentlyPlaying = null)

        assertThat(outcome).isEqualTo(QueueAddOutcome.Inserted)
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "x").inOrder()
        assertThat(controller.currentIndex).isEqualTo(1)
    }

    @Test
    fun `queueing against an empty queue keeps the playing video as the first entry`() {
        val controller = PlaybackQueueController()

        val outcome = controller.addNext(video("x"), currentlyPlaying = video("playing"))

        assertThat(outcome).isEqualTo(QueueAddOutcome.QueueCreated)
        assertThat(ids(controller.videos.value)).containsExactly("playing", "x").inOrder()
        assertThat(controller.currentIndex).isEqualTo(0)
    }

    @Test
    fun `queueing with nothing playing and nothing queued reports there is no queue`() {
        val controller = PlaybackQueueController()

        val outcome = controller.append(video("x"), currentlyPlaying = null)

        assertThat(outcome).isEqualTo(QueueAddOutcome.NoActiveQueue)
        assertThat(controller.isEmpty).isTrue()
        assertThat(controller.currentIndex).isEqualTo(-1)
    }

    @Test
    fun `removing an earlier video keeps the same video current`() {
        val controller = controllerOf("a", "b", "c", startIndex = 2)

        assertThat(controller.removeAt(0)).isTrue()
        assertThat(controller.currentIndex).isEqualTo(1)
        assertThat(controller.currentVideo?.id).isEqualTo("c")
        assertThat(controller.currentIndexState.value).isEqualTo(1)
    }

    @Test
    fun `the currently playing video cannot be removed out from under playback`() {
        val controller = controllerOf("a", "b", "c", startIndex = 1)

        assertThat(controller.removeAt(1)).isFalse()
        assertThat(controller.removeAt(9)).isFalse()
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `turning shuffle off restores the original order and stays on the current video`() {
        val controller = controllerOf("a", "b", "c", "d", startIndex = 2)

        assertThat(controller.setShuffleEnabled(true)).isTrue()
        assertThat(controller.currentVideo?.id).isEqualTo("c")

        assertThat(controller.setShuffleEnabled(false)).isTrue()
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "c", "d").inOrder()
        assertThat(controller.currentVideo?.id).isEqualTo("c")
    }

    @Test
    fun `a video queued while shuffled survives turning shuffle off`() {
        val controller = controllerOf("a", "b", "c")
        controller.setShuffleEnabled(true)

        controller.addNext(video("x"), currentlyPlaying = null)
        controller.append(video("y"), currentlyPlaying = null)
        controller.setShuffleEnabled(false)

        // Added videos have to be folded into the pre-shuffle order too, or they vanish from the
        // queue the moment shuffle is turned off.
        assertThat(ids(controller.videos.value)).containsExactly("a", "x", "b", "c", "y").inOrder()
    }

    @Test
    fun `reordering while shuffled leaves the original order intact`() {
        val controller = controllerOf("a", "b", "c")
        controller.setShuffleEnabled(true)

        assertThat(controller.move(fromIndex = 1, toIndex = 2)).isTrue()
        controller.setShuffleEnabled(false)

        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `reordering while unshuffled becomes the order to restore later`() {
        val controller = controllerOf("a", "b", "c")

        assertThat(controller.move(fromIndex = 2, toIndex = 0)).isTrue()
        assertThat(ids(controller.videos.value)).containsExactly("c", "a", "b").inOrder()

        controller.setShuffleEnabled(true)
        controller.setShuffleEnabled(false)

        assertThat(ids(controller.videos.value)).containsExactly("c", "a", "b").inOrder()
    }

    @Test
    fun `toggling shuffle to its current value or on an empty queue changes nothing`() {
        val empty = PlaybackQueueController()
        assertThat(empty.setShuffleEnabled(true)).isFalse()
        assertThat(empty.shuffleEnabled).isFalse()

        val controller = controllerOf("a", "b")
        assertThat(controller.setShuffleEnabled(false)).isFalse()
    }

    @Test
    fun `clear resets the queue along with its title and toggles`() {
        val controller = controllerOf("a", "b", startIndex = 1)
        controller.setLoopEnabled(true)
        controller.setShuffleEnabled(true)

        controller.clear()

        assertThat(controller.isEmpty).isTrue()
        assertThat(controller.size).isEqualTo(0)
        assertThat(controller.currentIndex).isEqualTo(-1)
        assertThat(controller.videos.value).isEmpty()
        assertThat(controller.currentIndexState.value).isEqualTo(-1)
        assertThat(controller.title).isNull()
        assertThat(controller.loopEnabled).isFalse()
        assertThat(controller.shuffleEnabled).isFalse()
        assertThat(controller.hasNext).isFalse()
    }

    @Test
    fun `isCurrent only matches the video at the current position`() {
        val controller = controllerOf("a", "b", "c", startIndex = 1)

        assertThat(controller.isCurrent("b")).isTrue()
        assertThat(controller.isCurrent("a")).isFalse()
        assertThat(controller.isCurrent("missing")).isFalse()
    }
}
