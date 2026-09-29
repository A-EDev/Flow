package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * #1030: an imported subscription list or watch history must seed the brain without freezing it.
 * Imported channel words fade unless watching confirms them, and a large history must not damp
 * every later interaction.
 */
class NeuroImportBootstrapTest {
    private fun engineWith(brain: UserBrain): FlowNeuroEngine {
        val storage: NeuroStorage =
            mockk(relaxed = true) {
                coEvery { load() } returns brain
            }
        return FlowNeuroEngine(mockk(relaxed = true), storage, mockk<NeuroContentStore>(relaxed = true))
    }

    private fun video(index: Int) =
        Video(
            id = "video$index",
            title = "Woodworking joinery project number $index",
            channelName = "Channel ${index % 40}",
            channelId = "UC${index % 40}",
            thumbnailUrl = "",
            duration = 0,
            viewCount = 0L,
            uploadDate = "",
            timestamp = 1_000_000L - index,
        )

    @Test
    fun `subscription words never become preferred topics`() =
        runTest {
            val engine = engineWith(UserBrain(preferredTopics = setOf("chess")))

            engine.bootstrapFromSubscriptions(listOf("Guitar Lessons Daily", "Guitar World", "Cooking Guitar"))

            val brain = engine.getBrainSnapshot()
            assertThat(brain.preferredTopics).containsExactly("chess")
            assertThat(brain.globalVector.topics).isNotEmpty()
            assertThat(brain.hasCompletedOnboarding).isTrue()
        }

    @Test
    fun `subscription seeds stay below the established tier however often a word repeats`() =
        runTest {
            val engine = engineWith(UserBrain())

            engine.bootstrapFromSubscriptions(List(6) { "Guitar Channel $it" })

            val topics = engine.getBrainSnapshot().globalVector.topics
            assertThat(topics).isNotEmpty()
            assertThat(topics.values.max()).isLessThan(NeuroVectorMath.ESTABLISHED_TOPIC_THRESHOLD)
        }

    @Test
    fun `subscription seeds never lower a topic the brain already holds`() =
        runTest {
            val engine = engineWith(UserBrain(globalVector = ContentVector(topics = mapOf("guitar" to 0.8))))

            engine.bootstrapFromSubscriptions(listOf("Guitar Daily"))

            assertThat(engine.getBrainSnapshot().globalVector.topics["guitar"]).isEqualTo(0.8)
        }

    @Test
    fun `a large imported history counts only as the warm-up`() =
        runTest {
            val engine = engineWith(UserBrain(totalInteractions = 3))

            engine.bootstrapFromWatchHistory(List(600) { video(it) })

            val brain = engine.getBrainSnapshot()
            assertThat(brain.totalInteractions)
                .isEqualTo(3 + FlowNeuroEngine.HISTORY_BOOTSTRAP_MAX_INTERACTIONS)
            assertThat(brain.globalVector.topics).isNotEmpty()
        }

    @Test
    fun `a small imported history counts every video`() =
        runTest {
            val engine = engineWith(UserBrain())

            engine.bootstrapFromWatchHistory(List(12) { video(it) })

            assertThat(engine.getBrainSnapshot().totalInteractions).isEqualTo(12)
        }
}
