package io.github.aedev.flow.ui.components

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.VideoHistoryEntry
import org.junit.Test

class VideoCardStateTest {

    private fun entry(
        videoId: String,
        position: Long,
        duration: Long
    ) = VideoHistoryEntry(
        videoId = videoId,
        position = position,
        duration = duration,
        timestamp = 0L,
        title = videoId,
        thumbnailUrl = ""
    )

    @Test
    fun `partially watched videos map to fractional progress`() {
        val map = listOf(entry("a", position = 50, duration = 100)).toWatchProgressMap()

        assertThat(map["a"]).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `barely started videos are omitted so cards show no bar`() {
        val map = listOf(entry("a", position = 2, duration = 100)).toWatchProgressMap()

        assertThat(map).isEmpty()
    }

    @Test
    fun `the three percent threshold is inclusive`() {
        val map = listOf(entry("a", position = 3, duration = 100)).toWatchProgressMap()

        assertThat(map["a"]).isWithin(0.001f).of(0.03f)
    }

    @Test
    fun `near-complete videos fill the bar rather than stopping short`() {
        val map = listOf(entry("a", position = 95, duration = 100)).toWatchProgressMap()

        assertThat(map["a"]).isEqualTo(1f)
    }

    @Test
    fun `entries without a known duration are omitted`() {
        val map = listOf(entry("a", position = 10, duration = 0)).toWatchProgressMap()

        assertThat(map).isEmpty()
    }

    @Test
    fun `every qualifying entry is keyed by video id in one pass`() {
        val map = listOf(
            entry("a", position = 50, duration = 100),
            entry("b", position = 1, duration = 100),
            entry("c", position = 99, duration = 100)
        ).toWatchProgressMap()

        assertThat(map.keys).containsExactly("a", "c")
    }

    @Test
    fun `an empty history produces an empty map`() {
        assertThat(emptyList<VideoHistoryEntry>().toWatchProgressMap()).isEmpty()
    }
}
