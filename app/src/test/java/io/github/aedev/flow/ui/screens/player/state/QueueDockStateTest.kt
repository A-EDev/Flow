package io.github.aedev.flow.ui.screens.player.state

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class QueueDockStateTest {
    private fun video(id: String) =
        Video(
            id = id,
            title = id,
            channelName = "Channel",
            channelId = "UC1",
            thumbnailUrl = "",
            duration = 60,
            viewCount = 1L,
            uploadDate = "",
        )

    private val queue = listOf(video("a"), video("b"), video("c"))

    @Test
    fun `a named queue shows from one video, an unnamed one from two`() {
        assertThat(hasVisibleQueue(queueTitle = "Watch later", queueSize = 1)).isTrue()
        assertThat(hasVisibleQueue(queueTitle = null, queueSize = 1)).isFalse()
        assertThat(hasVisibleQueue(queueTitle = null, queueSize = 2)).isTrue()
        assertThat(hasVisibleQueue(queueTitle = "Watch later", queueSize = 0)).isFalse()
    }

    @Test
    fun `the next video wraps to the first only while looping`() {
        assertThat(nextQueueVideo(queue, currentIndex = 0, isLooping = false)).isEqualTo(IndexedValue(1, queue[1]))
        assertThat(nextQueueVideo(queue, currentIndex = 2, isLooping = false)).isNull()
        assertThat(nextQueueVideo(queue, currentIndex = 2, isLooping = true)).isEqualTo(IndexedValue(0, queue[0]))
    }

    @Test
    fun `the wide layout puts what plays next in the side pane`() {
        assertThat(queueDockPlacement(hasQueue = true, layoutMode = PlayerLayoutMode.WIDE)).isEqualTo(QueueDockPlacement.IN_PANE)
        assertThat(queueDockPlacement(hasQueue = true, layoutMode = PlayerLayoutMode.MEDIUM)).isEqualTo(QueueDockPlacement.FLOATING)
        assertThat(queueDockPlacement(hasQueue = true, layoutMode = PlayerLayoutMode.COMPACT)).isEqualTo(QueueDockPlacement.FLOATING)
        assertThat(queueDockPlacement(hasQueue = false, layoutMode = PlayerLayoutMode.WIDE)).isEqualTo(QueueDockPlacement.HIDDEN)
    }
}
