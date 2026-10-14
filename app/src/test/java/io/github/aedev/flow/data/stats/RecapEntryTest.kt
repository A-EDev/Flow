package io.github.aedev.flow.data.stats

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.PrivacyGate
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecapEntryTest {
    private fun gate(
        deepFlow: Boolean,
        historyPaused: Boolean,
    ) = PrivacyGate(
        deepFlowActive = { deepFlow },
        watchHistoryPaused = { historyPaused },
        scrobbleDuringDeepFlow = { false },
    )

    @Test
    fun `everything is recorded when nothing is paused`() =
        runTest {
            RecapEntry.entries.forEach { assertThat(it.allowed(gate(deepFlow = false, historyPaused = false))).isTrue() }
        }

    @Test
    fun `Deep Flow keeps searches, actions and views out`() =
        runTest {
            val gate = gate(deepFlow = true, historyPaused = false)

            assertThat(RecapEntry.ACTIVITY.allowed(gate)).isFalse()
            assertThat(RecapEntry.VIEW.allowed(gate)).isFalse()
        }

    @Test
    fun `paused watch history keeps views out but still counts searches`() =
        runTest {
            val gate = gate(deepFlow = false, historyPaused = true)

            assertThat(RecapEntry.VIEW.allowed(gate)).isFalse()
            assertThat(RecapEntry.ACTIVITY.allowed(gate)).isTrue()
        }

    @Test
    fun `forgetting stored data always goes through`() =
        runTest {
            assertThat(RecapEntry.FORGET.allowed(gate(deepFlow = true, historyPaused = true))).isTrue()
        }
}
