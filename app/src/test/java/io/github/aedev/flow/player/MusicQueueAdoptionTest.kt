package io.github.aedev.flow.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class MusicQueueAdoptionTest {
    private fun artwork(url: String): Uri = mockk { every { this@mockk.toString() } returns url }

    private fun item(
        id: String,
        title: String = "Song $id",
    ) = MediaItem
        .Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata
                .Builder()
                .setTitle(title)
                .setArtist("Artist")
                .setArtworkUri(artwork("https://i.ytimg.com/vi/$id/hqdefault.jpg"))
                .setDurationMs(215_000L)
                .build(),
        ).build()

    @Test
    fun `the player's items become the queue, in order, with what the app shows`() {
        val queue = adoptedQueue(listOf(item("a"), item("b", title = "Second")))

        assertThat(queue.map { it.videoId }).containsExactly("a", "b").inOrder()
        assertThat(queue[1].title).isEqualTo("Second")
        assertThat(queue[0].artist).isEqualTo("Artist")
        assertThat(queue[0].thumbnailUrl).isEqualTo("https://i.ytimg.com/vi/a/hqdefault.jpg")
        assertThat(queue[0].duration).isEqualTo(215)
    }

    @Test
    fun `an item without an id is left out`() {
        assertThat(adoptedQueue(listOf(item(""), item("a"))).map { it.videoId }).containsExactly("a")
    }
}
