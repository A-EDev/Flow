package io.github.aedev.flow.player.cache

import android.net.Uri
import androidx.media3.datasource.DataSpec
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class VideoCacheKeysTest {
    private var playing: String? = "first"
    private val keys = VideoCacheKeys { playing }

    private fun open(url: String): String {
        val uri = mockk<Uri> { every { this@mockk.toString() } returns url }
        return keys.buildCacheKey(DataSpec.Builder().setUri(uri).build())
    }

    @Test
    fun `keys are Media3's own and remembered under the video that opened them`() {
        assertThat(open("https://example.com/a?itag=137")).isEqualTo("https://example.com/a?itag=137")
        open("https://example.com/a?itag=140")
        playing = "second"
        open("https://example.com/b?itag=137")

        assertThat(keys.takeKeys("first")).containsExactly("https://example.com/a?itag=137", "https://example.com/a?itag=140")
        assertThat(keys.takeKeys("second")).containsExactly("https://example.com/b?itag=137")
    }

    @Test
    fun `taking a video's keys forgets them`() {
        open("https://example.com/a")
        keys.takeKeys("first")

        assertThat(keys.takeKeys("first")).isEmpty()
    }

    @Test
    fun `nothing is recorded while no video is playing`() {
        playing = null
        open("https://example.com/a")

        assertThat(keys.takeKeys("first")).isEmpty()
    }

    @Test
    fun `only the most recent videos are remembered`() {
        repeat(9) { index ->
            playing = "video$index"
            open("https://example.com/$index")
        }

        assertThat(keys.takeKeys("video0")).isEmpty()
        assertThat(keys.takeKeys("video8")).containsExactly("https://example.com/8")
    }
}
