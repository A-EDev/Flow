package io.github.aedev.flow.ui.screens.settings.taste

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.ContentVector
import io.github.aedev.flow.data.recommendation.NeuroClusters
import io.github.aedev.flow.data.recommendation.UserBrain
import org.junit.Test

class TasteGroupsTest {
    private val brain =
        UserBrain(
            globalVector =
                ContentVector(
                    topics = mapOf("pixel" to 0.3, "android" to 0.25, "buy" to 0.29, "camera" to 0.1, "gym" to 0.2, "mma" to 0.15),
                ),
        )
    private val clusters =
        listOf(
            NeuroClusters.TopicCluster("pixel", listOf("pixel", "android", "buy", "camera"), 0.9),
            NeuroClusters.TopicCluster("gym", listOf("gym", "mma"), 0.35),
        )

    @Test
    fun `a group is named by its three strongest words of one subject`() {
        val groups =
            tasteGroups(brain, clusters, emptySet(), {
                if (it == "buy") {
                    0.1
                } else if (it == "android") {
                    0.4
                } else {
                    1.0
                }
            }, count = 8)

        assertThat(groups.map { it.label }).containsExactly("Pixel · Camera", "Gym · Mma").inOrder()
        assertThat(groups.first().members.map { it.id }).containsExactly("pixel", "android", "camera").inOrder()
        assertThat(groups.first().share).isEqualTo(1f)
    }

    @Test
    fun `picks and blocked words stay out of the groups`() {
        val groups = tasteGroups(brain.copy(blockedTopics = setOf("android")), clusters, setOf("gym", "mma"), { 1.0 }, count = 8)

        assertThat(groups.single().members.map { it.id }).containsExactly("pixel", "buy", "camera").inOrder()
    }
}
