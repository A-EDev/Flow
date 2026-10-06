package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveChatRepositoryTest {
    @Test
    fun `polling honors the server interval`() {
        assertThat(LiveChatRepository.pollTimeoutMs(5_000)).isEqualTo(5_000)
        assertThat(LiveChatRepository.pollTimeoutMs(10_000)).isEqualTo(10_000)
        assertThat(LiveChatRepository.pollTimeoutMs(60_000)).isEqualTo(60_000)
    }

    @Test
    fun `missing or invalid intervals cannot create a busy loop`() {
        listOf(null, -1L, 0L, 100L).forEach {
            assertThat(LiveChatRepository.pollTimeoutMs(it)).isEqualTo(1_000)
        }
    }

    @Test
    fun `an implausibly long server interval is bounded`() {
        assertThat(LiveChatRepository.pollTimeoutMs(120_000)).isEqualTo(60_000)
    }
}
