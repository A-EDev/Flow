package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscordPlaybackSelectorTest {
    private val selector = DiscordPlaybackSelector()

    @Test
    fun `shorts take priority over regular video and music`() {
        val selected = selector.select(
            short = snapshot(PlaybackKind.SHORT, "short"),
            video = snapshot(PlaybackKind.VIDEO, "video"),
            music = snapshot(PlaybackKind.MUSIC, "music"),
        )

        assertThat(selected?.mediaId).isEqualTo("short")
    }

    @Test
    fun `regular video takes priority over music`() {
        val selected = selector.select(
            short = null,
            video = snapshot(PlaybackKind.LIVE, "live"),
            music = snapshot(PlaybackKind.MUSIC, "music"),
        )

        assertThat(selected?.mediaId).isEqualTo("live")
    }

    @Test
    fun `paused higher priority player does not hide playing music`() {
        val selected = selector.select(
            short = snapshot(PlaybackKind.SHORT, "short", isPlaying = false),
            video = snapshot(PlaybackKind.VIDEO, "video", isPlaying = false),
            music = snapshot(PlaybackKind.MUSIC, "music"),
        )

        assertThat(selected?.mediaId).isEqualTo("music")
    }

    @Test
    fun `no active player clears selection`() {
        assertThat(selector.select(null, null, null)).isNull()
    }

    private fun snapshot(
        kind: PlaybackKind,
        id: String,
        isPlaying: Boolean = true,
    ) = PlaybackSnapshot(
        kind = kind,
        mediaId = id,
        title = id,
        subtitle = "creator",
        artworkUrl = "",
        positionMs = 0,
        durationMs = 60_000,
        isPlaying = isPlaying,
        isLive = kind == PlaybackKind.LIVE,
    )
}
