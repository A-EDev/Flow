package io.github.aedev.flow.ui.components.shared

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.SpeedSwipeSide
import org.junit.Test

class PlayerSpeedSwipeTest {
    private fun speed(
        start: Float,
        travelUpPx: Float,
        step: Float = 0.1f,
    ) = PlayerSpeedSwipe.speedFor(
        startSpeed = start,
        travelUpPx = travelUpPx,
        stepTravelPx = 40f,
        step = step,
        minSpeed = 0.1f,
        maxSpeed = 4.0f,
    )

    @Test
    fun `less than one step of travel keeps the speed`() {
        assertThat(speed(start = 1.0f, travelUpPx = 39f)).isEqualTo(1.0f)
        assertThat(speed(start = 1.0f, travelUpPx = -39f)).isEqualTo(1.0f)
    }

    @Test
    fun `each step of travel up or down moves one step`() {
        assertThat(speed(start = 1.0f, travelUpPx = 40f)).isEqualTo(1.1f)
        assertThat(speed(start = 1.0f, travelUpPx = 200f)).isEqualTo(1.5f)
        assertThat(speed(start = 1.0f, travelUpPx = -80f)).isEqualTo(0.8f)
        assertThat(speed(start = 1.0f, travelUpPx = 40f, step = 0.25f)).isEqualTo(1.25f)
        assertThat(speed(start = 1.0f, travelUpPx = 200f, step = 0.05f)).isEqualTo(1.25f)
    }

    @Test
    fun `a speed off the grid lands on the grid with the first step`() {
        assertThat(speed(start = 1.07f, travelUpPx = 40f)).isEqualTo(1.1f)
        assertThat(speed(start = 1.07f, travelUpPx = -40f)).isEqualTo(1.0f)
        assertThat(speed(start = 1.3f, travelUpPx = 40f, step = 0.25f)).isEqualTo(1.5f)
    }

    @Test
    fun `the speed stops at the player's range and never wraps`() {
        assertThat(speed(start = 3.9f, travelUpPx = 4_000f)).isEqualTo(4.0f)
        assertThat(speed(start = 0.3f, travelUpPx = -4_000f)).isEqualTo(0.1f)
        assertThat(speed(start = 0.25f, travelUpPx = -40f, step = 0.25f)).isEqualTo(0.1f)
    }

    @Test
    fun `the chosen side owns its half and the other half keeps its own gesture`() {
        assertThat(PlayerSpeedSwipe.ownsDrag(SpeedSwipeSide.LEFT, x = 100f, width = 1000f)).isTrue()
        assertThat(PlayerSpeedSwipe.ownsDrag(SpeedSwipeSide.LEFT, x = 900f, width = 1000f)).isFalse()
        assertThat(PlayerSpeedSwipe.ownsDrag(SpeedSwipeSide.RIGHT, x = 900f, width = 1000f)).isTrue()
        assertThat(PlayerSpeedSwipe.ownsDrag(SpeedSwipeSide.RIGHT, x = 100f, width = 1000f)).isFalse()
        assertThat(PlayerSpeedSwipe.ownsDrag(SpeedSwipeSide.OFF, x = 100f, width = 1000f)).isFalse()
    }
}
