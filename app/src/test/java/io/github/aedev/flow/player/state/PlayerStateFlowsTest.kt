package io.github.aedev.flow.player.state

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStateFlowsTest {
    @Test
    fun queuePresenceIgnoresUnrelatedPlayerStateUpdates() = runTest {
        val state = MutableStateFlow(EnhancedPlayerState())

        state.queuePresence().test {
            assertFalse(awaitItem())

            state.value = state.value.copy(
                bufferedPercentage = 50f,
                isPlaying = true,
            )
            expectNoEvents()

            state.value = state.value.copy(queueTitle = "Playlist")
            assertTrue(awaitItem())

            state.value = state.value.copy(
                bufferedPercentage = 75f,
                isPlaying = false,
            )
            expectNoEvents()

            state.value = state.value.copy(queueTitle = null)
            assertFalse(awaitItem())
        }
    }
}
