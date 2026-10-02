package io.github.aedev.flow.player

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The button and the player must name the same mode: Media3 numbers them differently from the saved queue. */
class RepeatModeTest {
    @Test
    fun `every mode reads back from the player's own value`() {
        RepeatMode.entries.forEach { mode ->
            assertThat(RepeatMode.fromPlayer(mode.playerMode)).isEqualTo(mode)
        }
        assertThat(RepeatMode.fromPlayer(Player.REPEAT_MODE_ONE)).isEqualTo(RepeatMode.ONE)
    }

    @Test
    fun `an unknown value is off`() {
        assertThat(RepeatMode.fromPlayer(7)).isEqualTo(RepeatMode.OFF)
    }
}
