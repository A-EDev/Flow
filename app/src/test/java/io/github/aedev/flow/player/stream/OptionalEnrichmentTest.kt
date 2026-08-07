package io.github.aedev.flow.player.stream

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OptionalEnrichmentTest {
    @Test
    fun `a probe that finishes within the grace delivers its value`() =
        runTest {
            val probe = CompletableDeferred<List<String>>()
            probe.complete(listOf("en", "es", "de"))

            val result = awaitWithinGrace(probe, graceMs = 1_000L)

            assertThat(result).containsExactly("en", "es", "de").inOrder()
        }

    @Test
    fun `a probe that overruns the grace yields null instead of blocking`() =
        runTest {
            val probe = async { awaitCancellation() }

            val result: Any? = awaitWithinGrace(probe, graceMs = 1_000L)

            assertThat(result).isNull()
        }

    @Test
    fun `an overrunning probe is cancelled so it stops competing for bandwidth`() =
        runTest {
            val probe = async { awaitCancellation() }

            awaitWithinGrace(probe, graceMs = 1_000L)

            assertThat(probe.isCancelled).isTrue()
        }
}
