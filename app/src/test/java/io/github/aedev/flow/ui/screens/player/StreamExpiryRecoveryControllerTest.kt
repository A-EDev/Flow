package io.github.aedev.flow.ui.screens.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamExpiryRecoveryControllerTest {
    private fun controller() = StreamExpiryRecoveryController(maxRetries = 3)

    @Test
    fun `the first expiry reloads without evicting the cache`() {
        val decision = controller().onStreamExpired("vid_a")

        assertThat(decision).isEqualTo(
            StreamExpiryRecoveryController.Decision.Reload(attempt = 1, limit = 3, evictCache = false),
        )
    }

    @Test
    fun `a repeat expiry evicts what the cache holds for the video`() {
        val recovery = controller()
        recovery.onStreamExpired("vid_a")

        val second = recovery.onStreamExpired("vid_a") as StreamExpiryRecoveryController.Decision.Reload

        assertThat(second.attempt).isEqualTo(2)
        assertThat(second.evictCache).isTrue()
    }

    @Test
    fun `the budget is spent after the retry limit and every later expiry is ignored`() {
        val recovery = controller()

        repeat(3) { assertThat(recovery.onStreamExpired("vid_a")).isInstanceOf(StreamExpiryRecoveryController.Decision.Reload::class.java) }

        assertThat(recovery.onStreamExpired("vid_a")).isEqualTo(StreamExpiryRecoveryController.Decision.GiveUp)
        assertThat(recovery.abandonedVideoId).isEqualTo("vid_a")
        assertThat(recovery.onStreamExpired("vid_a")).isEqualTo(StreamExpiryRecoveryController.Decision.Ignored)
        assertThat(recovery.onStreamExpired("vid_a")).isEqualTo(StreamExpiryRecoveryController.Decision.Ignored)
    }

    @Test
    fun `a different video starts from a fresh budget`() {
        val recovery = controller()
        repeat(4) { recovery.onStreamExpired("vid_a") }

        val other = recovery.onStreamExpired("vid_b")

        assertThat(other).isEqualTo(
            StreamExpiryRecoveryController.Decision.Reload(attempt = 1, limit = 3, evictCache = false),
        )
    }

    @Test
    fun `playback the player abandoned takes no further expiry events`() {
        val recovery = controller()

        recovery.onPlaybackAbandoned("vid_a")

        assertThat(recovery.onStreamExpired("vid_a")).isEqualTo(StreamExpiryRecoveryController.Decision.Ignored)
        assertThat(recovery.onStreamExpired("vid_b")).isInstanceOf(StreamExpiryRecoveryController.Decision.Reload::class.java)
    }

    @Test
    fun `asking for playback again restores the budget for a video that was given up on`() {
        val recovery = controller()
        repeat(4) { recovery.onStreamExpired("vid_a") }

        recovery.onPlaybackRequested()

        assertThat(recovery.abandonedVideoId).isNull()
        assertThat(recovery.onStreamExpired("vid_a")).isEqualTo(
            StreamExpiryRecoveryController.Decision.Reload(attempt = 1, limit = 3, evictCache = false),
        )
    }

    @Test
    fun `loading another video releases the abandoned latch but loading the same one keeps it`() {
        val recovery = controller()
        recovery.onPlaybackAbandoned("vid_a")

        recovery.onLoadStarted("vid_a")
        assertThat(recovery.abandonedVideoId).isEqualTo("vid_a")

        recovery.onLoadStarted("vid_b")
        assertThat(recovery.abandonedVideoId).isNull()
    }
}
