package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class LikedVideoRecordTest {
    @Test
    fun `an old record reads as before`() {
        val like = deserializeVideo("dQw4w9WgXcQ|Never Gonna Give You Up|https://i.ytimg.com/vi/x/hq.jpg|Rick Astley|1700000000000|true")

        assertThat(like).isEqualTo(
            LikedVideoInfo(
                videoId = "dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                thumbnail = "https://i.ytimg.com/vi/x/hq.jpg",
                channelName = "Rick Astley",
                likedAt = 1_700_000_000_000L,
                isMusic = true,
            ),
        )
    }

    @Test
    fun `an old record from before the music flag reads as a video`() {
        val like = deserializeVideo("abc|Title|https://t/1.jpg|Channel|42")

        assertThat(like?.isMusic).isFalse()
        assertThat(like?.likedAt).isEqualTo(42L)
    }

    @Test
    fun `a pipe in an old title no longer loses the like`() {
        val like = deserializeVideo("abc|Lofi | beats to study|https://t/1.jpg|Lofi | Girl|42|false")

        assertThat(like?.title).isEqualTo("Lofi | beats to study")
        assertThat(like?.channelName).isEqualTo("Lofi | Girl")
        assertThat(like?.thumbnail).isEqualTo("https://t/1.jpg")
    }

    @Test
    fun `a new record keeps the channel and length`() {
        val like = LikedVideoInfo("abc", "T | x", "https://t", "C", likedAt = 5L, channelId = "UC1", durationSeconds = 300)
        val json = Json { encodeDefaults = true }.encodeToString(LikedVideoInfo.serializer(), like)

        assertThat(deserializeVideo(json)).isEqualTo(like)
    }

    @Test
    fun `a like re-applied with less keeps what was already filled in`() {
        val stored = LikedVideoInfo("abc", "T", "t", "C", likedAt = 1L, channelId = "UC1", durationSeconds = 300)
        val synced = LikedVideoInfo("abc", "T", "t", "C", likedAt = 9L)

        val merged = synced.keepingDetailsOf(stored)

        assertThat(merged.channelId).isEqualTo("UC1")
        assertThat(merged.durationSeconds).isEqualTo(300)
        assertThat(merged.likedAt).isEqualTo(9L)
    }

    @Test
    fun `undone likes go back where they were by date`() {
        val order = listOf("c", "a")
        val dated = listOf("c" to 30L, "a" to 10L, "b" to 20L, "d" to 40L)

        assertThat(restoredOrder(order, dated)).containsExactly("d", "c", "b", "a").inOrder()
    }
}
