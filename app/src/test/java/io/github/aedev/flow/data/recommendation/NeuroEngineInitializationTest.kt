package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * #1031: the Home feed, the player and the feedback menu can reach the engine before anything has
 * called initialize(). Such a call must load the persisted brain first, never read or save the
 * empty default over it.
 */
class NeuroEngineInitializationTest {
    private val persisted =
        UserBrain(
            totalInteractions = 42,
            blockedChannels = setOf("UCold"),
            suppressedVideoIds = mapOf("not-interested" to System.currentTimeMillis()),
        )
    private val saved = mutableListOf<UserBrain>()
    private val storage: NeuroStorage =
        mockk(relaxed = true) {
            coEvery { load() } returns persisted
            coEvery { save(any()) } answers {
                saved += firstArg<UserBrain>()
                Unit
            }
        }
    private val engine = FlowNeuroEngine(mockk(relaxed = true), storage, mockk<NeuroContentStore>(relaxed = true))

    @Test
    fun `a block before initialize adds to the persisted brain`() =
        runTest {
            engine.blockChannel("UCnew")

            assertThat(saved.last().blockedChannels).containsExactly("UCold", "UCnew")
            assertThat(saved.last().totalInteractions).isEqualTo(42)
        }

    @Test
    fun `a snapshot before initialize is the persisted brain`() =
        runTest {
            assertThat(engine.getBrainSnapshot().totalInteractions).isEqualTo(42)
        }

    @Test
    fun `feed exclusions before initialize carry the persisted suppressions`() =
        runTest {
            val exclusions = engine.feedExclusions()

            assertThat(exclusions.suppressedVideoIds).containsExactly("not-interested")
            assertThat(exclusions.blockedChannelIds).containsExactly("UCold")
        }

    @Test
    fun `the brain is loaded once however many callers race to it`() =
        runTest {
            engine.getBrainSnapshot()
            engine.feedExclusions()
            engine.initialize()

            coVerify(exactly = 1) { storage.load() }
        }
}
