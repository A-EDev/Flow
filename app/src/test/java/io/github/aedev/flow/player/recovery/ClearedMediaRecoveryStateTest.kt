package io.github.aedev.flow.player.recovery

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClearedMediaRecoveryStateTest {
    @Test
    fun `capture preserves paused local playback state`() {
        val state = ClearedMediaRecoveryState()

        state.capture(
            videoId = "video-id",
            positionMs = 12_345L,
            playWhenReady = false,
            localFilePath = "/media/video.mp4"
        )

        assertThat(state.pendingFor("video-id")).isEqualTo(
            ClearedMediaRecoverySnapshot(
                videoId = "video-id",
                positionMs = 12_345L,
                playWhenReady = false,
                localFilePath = "/media/video.mp4"
            )
        )
    }

    @Test
    fun `repeated empty trim does not replace the pending snapshot`() {
        val state = ClearedMediaRecoveryState()
        state.capture("video-id", 8_000L, playWhenReady = false, localFilePath = null)

        state.capture(null, 0L, playWhenReady = false, localFilePath = null)

        assertThat(state.pendingFor("video-id")?.positionMs).isEqualTo(8_000L)
    }

    @Test
    fun `snapshot cannot be reused for another video`() {
        val state = ClearedMediaRecoveryState()
        state.capture("old-video", 8_000L, playWhenReady = false, localFilePath = null)

        assertThat(state.pendingFor("new-video")).isNull()
    }

    @Test
    fun `completed recovery clears only the snapshot that was loaded`() {
        val state = ClearedMediaRecoveryState()
        state.capture("video-id", 8_000L, playWhenReady = false, localFilePath = null)
        val loadedSnapshot = state.pendingFor("video-id")!!
        state.capture("video-id", 9_000L, playWhenReady = false, localFilePath = null)

        state.complete(loadedSnapshot)

        assertThat(state.pendingFor("video-id")?.positionMs).isEqualTo(9_000L)
    }

    @Test
    fun `explicit clear prevents stopped media from being recovered`() {
        val state = ClearedMediaRecoveryState()
        state.capture("video-id", 8_000L, playWhenReady = false, localFilePath = null)

        state.clear()

        assertThat(state.pendingFor("video-id")).isNull()
    }
}
