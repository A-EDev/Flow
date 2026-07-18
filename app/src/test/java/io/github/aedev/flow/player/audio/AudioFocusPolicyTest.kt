package io.github.aedev.flow.player.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioFocusPolicyTest {

    @Test
    fun `play during calls disables automatic audio focus handling`() {
        assertThat(shouldHandleAudioFocus(playDuringCalls = true)).isFalse()
    }

    @Test
    fun `default playback keeps automatic audio focus handling`() {
        assertThat(shouldHandleAudioFocus(playDuringCalls = false)).isTrue()
    }
}
