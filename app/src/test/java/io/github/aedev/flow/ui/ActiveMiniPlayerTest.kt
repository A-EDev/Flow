package io.github.aedev.flow.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ActiveMiniPlayerTest {
    private fun resolve(
        hasVideo: Boolean = false,
        videoVisible: Boolean = false,
        videoInBackground: Boolean = false,
        onShortsPlayer: Boolean = false,
        hasMusic: Boolean = false,
        musicSuppressed: Boolean = false,
    ) = resolveActiveMiniPlayer(hasVideo, videoVisible, videoInBackground, onShortsPlayer, hasMusic, musicSuppressed)

    @Test
    fun `nothing playing shows nothing`() {
        assertThat(resolve()).isEqualTo(ActiveMiniPlayer.None)
    }

    @Test
    fun `a visible video shows the video player`() {
        assertThat(resolve(hasVideo = true, videoVisible = true)).isEqualTo(ActiveMiniPlayer.Video)
    }

    @Test
    fun `a video in the background shows the bar`() {
        assertThat(resolve(hasVideo = true, videoInBackground = true)).isEqualTo(ActiveMiniPlayer.VideoBar)
    }

    @Test
    fun `music shows its bar only with no video cached`() {
        assertThat(resolve(hasMusic = true)).isEqualTo(ActiveMiniPlayer.Music)
        assertThat(resolve(hasMusic = true, hasVideo = true, videoInBackground = true)).isEqualTo(ActiveMiniPlayer.VideoBar)
        assertThat(resolve(hasMusic = true, musicSuppressed = true)).isEqualTo(ActiveMiniPlayer.None)
    }

    @Test
    fun `the Shorts player hides the video bar`() {
        assertThat(resolve(hasVideo = true, videoInBackground = true, onShortsPlayer = true)).isEqualTo(ActiveMiniPlayer.None)
    }
}
