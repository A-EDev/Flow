package io.github.aedev.flow.ui.components.videoplayer.gesture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The press-and-hold speed ladder, moved here from `VideoPlayerUtilsTest` with the object itself.
 */
class PlayerSpeedBoostTest {
    @Test
    fun `a hold below the target jumps straight to it`() {
        assertThat(PlayerSpeedBoost.boostedPlaybackSpeed(currentSpeed = 1.0f, targetSpeed = 2.0f)).isEqualTo(2.0f)
        assertThat(PlayerSpeedBoost.boostedPlaybackSpeed(currentSpeed = 1.5f, targetSpeed = 2.0f)).isEqualTo(2.0f)
    }

    @Test
    fun `a hold at or above the target steps up instead`() {
        assertThat(PlayerSpeedBoost.boostedPlaybackSpeed(currentSpeed = 2.0f, targetSpeed = 2.0f)).isEqualTo(2.5f)
        assertThat(PlayerSpeedBoost.boostedPlaybackSpeed(currentSpeed = 3.0f, targetSpeed = 2.0f)).isEqualTo(3.5f)
        assertThat(PlayerSpeedBoost.boostedPlaybackSpeed(currentSpeed = 3.7f, targetSpeed = 2.0f)).isEqualTo(4.0f)
        assertThat(PlayerSpeedBoost.boostedPlaybackSpeed(currentSpeed = 4.0f, targetSpeed = 2.0f)).isEqualTo(4.0f)
    }

    @Test
    fun `a missing current speed is read as normal speed`() {
        assertThat(PlayerSpeedBoost.boostedPlaybackSpeed(currentSpeed = 0f, targetSpeed = 2.0f)).isEqualTo(2.0f)
        assertThat(PlayerSpeedBoost.boostedPlaybackSpeed(currentSpeed = -1f, targetSpeed = 2.0f)).isEqualTo(2.0f)
    }

    @Test
    fun `the target is clamped to the boost cap`() {
        assertThat(PlayerSpeedBoost.boostedPlaybackSpeed(currentSpeed = 1.0f, targetSpeed = 10f)).isEqualTo(4.0f)
        assertThat(PlayerSpeedBoost.MAX_BOOST_SPEED).isEqualTo(4.0f)
    }

    @Test
    fun `a target below the current speed still steps up`() {
        // Pins current behaviour: a target slower than the current speed is not applied; the
        // current speed is bumped by the step instead.
        assertThat(PlayerSpeedBoost.boostedPlaybackSpeed(currentSpeed = 1.0f, targetSpeed = 0.5f)).isEqualTo(1.5f)
        assertThat(PlayerSpeedBoost.boostedPlaybackSpeed(currentSpeed = 1.0f, targetSpeed = 0f)).isEqualTo(1.5f)
    }
}
