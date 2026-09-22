package io.github.aedev.flow.player.audio

import androidx.media3.exoplayer.ExoPlayer
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

/**
 * The speed the viewer chose has to reach the player *and* the state every time (#867).
 *
 * Releasing the player resets the state flow to its defaults, while the manager keeps the chosen
 * speed so it can be restored onto the next player. Restoring it silently left the state reading 1x
 * against a player genuinely running faster, which showed the wrong figure on the speed pill and
 * made the long-press boost compare against 1x — so holding stepped to a speed already in effect
 * and appeared to do nothing.
 */
class PlaybackSpeedStateTest {
    private val stateFlow = MutableStateFlow(EnhancedPlayerState())
    private val manager = AudioFeaturesManager(CoroutineScope(Dispatchers.Unconfined), stateFlow)

    private fun player() = mockk<ExoPlayer>(relaxed = true).also { every { it.audioSessionId } returns 0 }

    @Test
    fun `choosing a speed publishes it`() {
        manager.setPlaybackSpeed(player(), 2.0f)

        assertThat(stateFlow.value.playbackSpeed).isEqualTo(2.0f)
    }

    @Test
    fun `a speed restored onto a fresh player is published too`() {
        manager.setPlaybackSpeed(player(), 2.0f)
        // What releasing the player does to the state, while the manager keeps the chosen speed.
        stateFlow.value = EnhancedPlayerState()
        assertThat(stateFlow.value.playbackSpeed).isEqualTo(1.0f)

        val replacement = player()
        manager.setPlayer(replacement)

        assertThat(stateFlow.value.playbackSpeed).isEqualTo(2.0f)
        verify { replacement.setPlaybackParameters(match { it.speed == 2.0f }) }
    }

    @Test
    fun `an unchanged speed leaves the fresh state alone`() {
        manager.setPlayer(player())

        assertThat(stateFlow.value.playbackSpeed).isEqualTo(1.0f)
    }
}
