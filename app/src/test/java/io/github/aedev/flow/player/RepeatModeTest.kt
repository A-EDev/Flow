package io.github.aedev.flow.player

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A saved queue numbers repeat modes differently from Media3 (ALL is 1 there, ONE is 1 in Media3),
 * so a restored mode must be translated before the player gets it, or Repeat one comes back as
 * Repeat all.
 */
class RepeatModeTest {
    @Test
    fun `a saved mode restores to the player's own value for it`() {
        assertThat(RepeatMode.fromSaved(0).playerMode).isEqualTo(Player.REPEAT_MODE_OFF)
        assertThat(RepeatMode.fromSaved(1).playerMode).isEqualTo(Player.REPEAT_MODE_ALL)
        assertThat(RepeatMode.fromSaved(2).playerMode).isEqualTo(Player.REPEAT_MODE_ONE)
    }

    @Test
    fun `every mode survives a save and a restore`() {
        RepeatMode.entries.forEach { mode ->
            assertThat(RepeatMode.fromSaved(mode.savedCode)).isEqualTo(mode)
            assertThat(RepeatMode.fromPlayer(mode.playerMode)).isEqualTo(mode)
        }
    }

    @Test
    fun `an unknown code is off`() {
        assertThat(RepeatMode.fromSaved(7)).isEqualTo(RepeatMode.OFF)
        assertThat(RepeatMode.fromPlayer(7)).isEqualTo(RepeatMode.OFF)
    }
}
