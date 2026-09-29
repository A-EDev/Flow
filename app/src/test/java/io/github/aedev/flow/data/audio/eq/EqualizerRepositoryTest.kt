package io.github.aedev.flow.data.audio.eq

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerRepositoryTest {
    private class FakePersistence(
        var stored: EqState = EqState(),
    ) : EqStatePersistence {
        var saves = 0

        override suspend fun load(): EqState = stored

        override suspend fun save(state: EqState) {
            saves++
            stored = state
        }
    }

    /** advanceUntilIdle skips backgroundScope work, so the repository gets a plain scope on the test scheduler. */
    private fun TestScope.repository(persistence: FakePersistence): EqualizerRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return EqualizerRepository(persistence, CoroutineScope(SupervisorJob() + dispatcher), dispatcher)
    }

    @Test
    fun `restores the saved state and exposes it to the players`() =
        runTest {
            val saved = EqState().selectPreset("builtin:rock")
            val repository = repository(FakePersistence(saved))
            advanceUntilIdle()

            assertThat(repository.state.value).isEqualTo(saved)
            assertThat(repository.loaded.value).isTrue()
            assertThat(repository.processingSpec.value.bands).isNotEmpty()
            assertThat(repository.needsProcessing.value).isTrue()
        }

    @Test
    fun `edits are saved once, after the debounce`() =
        runTest {
            val persistence = FakePersistence()
            val repository = repository(persistence)
            advanceUntilIdle()

            repeat(5) { i -> repository.update { it.withBassBoost(i.toDouble()) } }
            advanceTimeBy(100)
            assertThat(persistence.saves).isEqualTo(0)
            advanceUntilIdle()

            assertThat(persistence.saves).isEqualTo(1)
            assertThat(persistence.stored.bassBoost).isEqualTo(4.0)
        }

    @Test
    fun `a preview reaches the players but not the saved state`() =
        runTest {
            val persistence = FakePersistence()
            val repository = repository(persistence)
            advanceUntilIdle()

            repository.preview(EqCurve(bands = listOf(EqBand(1_000.0, 6.0))))
            runCurrent()

            assertThat(repository.processingSpec.value.bands).containsExactly(EqBand(1_000.0, 6.0))
            assertThat(repository.state.value).isEqualTo(EqState())
            assertThat(repository.needsProcessing.value).isFalse()
            advanceUntilIdle()
            assertThat(persistence.saves).isEqualTo(0)
        }

    @Test
    fun `compare bypasses the sound without changing offload`() =
        runTest {
            val repository = repository(FakePersistence(EqState().selectPreset("builtin:rock")))
            advanceUntilIdle()

            repository.setBypass(true)
            runCurrent()

            assertThat(repository.processingSpec.value).isEqualTo(EqProcessingSpec.OFF)
            assertThat(repository.needsProcessing.value).isTrue()
        }

    @Test
    fun `a backup value replaces the state and is saved at once`() =
        runTest {
            val persistence = FakePersistence()
            val repository = repository(persistence)
            advanceUntilIdle()
            val incoming = EqState(bassBoost = 5.0)

            assertThat(repository.importJson(EqStateJson.encode(incoming))).isTrue()
            assertThat(repository.importJson("garbage")).isFalse()

            assertThat(repository.state.value).isEqualTo(incoming)
            assertThat(persistence.stored).isEqualTo(incoming)
            assertThat(EqStateJson.decode(repository.exportJson())).isEqualTo(incoming)
        }

    @Test
    fun `an edit made while the saved state loads is kept`() =
        runTest {
            val persistence = FakePersistence(EqState().selectPreset("builtin:rock"))
            val repository = repository(persistence)

            repository.update { it.copy(enabled = false) }
            advanceUntilIdle()

            assertThat(repository.state.value.enabled).isFalse()
            assertThat(repository.state.value.active.presetId).isEqualTo("builtin:rock")
            assertThat(persistence.stored.enabled).isFalse()
        }
}
