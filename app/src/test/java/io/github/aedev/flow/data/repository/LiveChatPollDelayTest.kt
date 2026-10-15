package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveChatPollDelayTest {
    @Test
    fun `a quiet chat waits as long as the server asks (#1042)`() {
        assertThat(LiveChatRepository.pollDelayMs(10_000L)).isEqualTo(10_000L)
        assertThat(LiveChatRepository.pollDelayMs(4_200L)).isEqualTo(4_200L)
    }

    @Test
    fun `the wait stays between one and ten seconds`() {
        assertThat(LiveChatRepository.pollDelayMs(200L)).isEqualTo(1_000L)
        assertThat(LiveChatRepository.pollDelayMs(60_000L)).isEqualTo(10_000L)
        assertThat(LiveChatRepository.pollDelayMs(null)).isEqualTo(1_000L)
    }
}
