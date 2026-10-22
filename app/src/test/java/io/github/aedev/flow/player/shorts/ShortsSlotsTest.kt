package io.github.aedev.flow.player.shorts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShortsSlotsTest {
    private val slots = ShortsSlots(size = 3)

    @Test
    fun `a slot is owned only by the index and id it was assigned`() {
        slots.assign(1, index = 4, videoId = "a", videoUrl = "v", audioUrl = "x", videoManifest = null, audioManifest = "m")

        assertThat(slots.owns(1, index = 4, videoId = "a")).isTrue()
        assertThat(slots.owns(1, index = 7, videoId = "a")).isFalse()
        assertThat(slots.owns(1, index = 4, videoId = "b")).isFalse()
        assertThat(slots.audioManifests[1]).isEqualTo("m")
    }

    @Test
    fun `clearing a slot forgets everything it held`() {
        slots.assign(2, index = 5, videoId = "a", videoUrl = "v", audioUrl = "x", videoManifest = "vm", audioManifest = "am")

        slots.clear(2)

        assertThat(slots.owns(2, index = 5, videoId = "a")).isFalse()
        assertThat(
            listOf(slots.videoUrls[2], slots.audioUrls[2], slots.videoManifests[2], slots.audioManifests[2]),
        ).containsExactly(null, null, null, null)
    }
}
