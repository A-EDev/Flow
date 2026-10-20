package io.github.aedev.flow.player

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.music.model.MusicTrack
import io.mockk.mockk
import org.junit.Test

class MusicMediaItemsTest {
    private fun track(duration: Int) = MusicTrack(videoId = "v", title = "Song", artist = "Artist", thumbnailUrl = "", duration = duration)

    @Test
    fun `a song's item carries its duration, so the queue read back from the player keeps it`() {
        val item = musicMediaItem(track(duration = 200), mockk<Uri>(), useCacheKey = false)

        assertThat(item.mediaMetadata.durationMs).isEqualTo(200_000L)
        assertThat(adoptedQueue(listOf(item)).single().duration).isEqualTo(200)
    }

    @Test
    fun `an unknown duration stays unknown instead of zero`() {
        val item = musicMediaItem(track(duration = 0), mockk<Uri>(), useCacheKey = false)

        assertThat(item.mediaMetadata.durationMs).isNull()
    }
}
