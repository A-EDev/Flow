package io.github.aedev.flow.ui.screens.player.effects

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeldOrientationTest {
    @Test
    fun `upright is portrait and either side is landscape`() {
        assertThat(heldOrientation(0, previous = 1)).isEqualTo(0)
        assertThat(heldOrientation(345, previous = 1)).isEqualTo(0)
        assertThat(heldOrientation(90, previous = 0)).isEqualTo(1)
        assertThat(heldOrientation(270, previous = 0)).isEqualTo(1)
    }

    @Test
    fun `upside down keeps fullscreen landscape`() {
        assertThat(heldOrientation(180, previous = 1)).isEqualTo(1)
        assertThat(heldOrientation(150, previous = 1)).isEqualTo(1)
        assertThat(heldOrientation(210, previous = 1)).isEqualTo(1)
    }

    @Test
    fun `upside down from portrait stays portrait`() {
        assertThat(heldOrientation(180, previous = 0)).isEqualTo(0)
    }

    @Test
    fun `angles between the bands keep the last reading`() {
        assertThat(heldOrientation(31, previous = 1)).isEqualTo(1)
        assertThat(heldOrientation(45, previous = 0)).isEqualTo(0)
        assertThat(heldOrientation(149, previous = -1)).isEqualTo(-1)
    }
}
