package io.github.aedev.flow.player

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MusicQueueAdoptionTest {
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
                .setArtworkUri(Uri.parse("https://i.ytimg.com/vi/$id/hqdefault.jpg"))
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
