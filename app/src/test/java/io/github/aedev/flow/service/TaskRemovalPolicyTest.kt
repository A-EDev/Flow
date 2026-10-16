package io.github.aedev.flow.service

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaskRemovalPolicyTest {
    private fun keeps(
        ongoing: Boolean = true,
        playWhenReady: Boolean = true,
        state: Int = Player.STATE_READY,
        hasMedia: Boolean = true,
    ) = keepsPlayingOnTaskRemoved(ongoing, playWhenReady, state, hasMedia)

    @Test
    fun `playing media keeps the service`() {
        assertThat(keeps()).isTrue()
    }

    @Test
    fun `a song still loading keeps the service`() {
        assertThat(keeps(state = Player.STATE_BUFFERING)).isTrue()
    }

    @Test
    fun `a pause stops the service even while the notification is still in the foreground`() {
        assertThat(keeps(playWhenReady = false)).isFalse()
        assertThat(keeps(playWhenReady = false, state = Player.STATE_BUFFERING)).isFalse()
    }

    @Test
    fun `a stopped, finished or empty player stops the service`() {
        assertThat(keeps(state = Player.STATE_IDLE)).isFalse()
        assertThat(keeps(state = Player.STATE_ENDED)).isFalse()
        assertThat(keeps(hasMedia = false)).isFalse()
    }

    @Test
    fun `a service that is not in the foreground always stops`() {
        assertThat(keeps(ongoing = false)).isFalse()
    }
}
