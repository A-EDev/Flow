package io.github.aedev.flow.ui.screens.settings.taste

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.ContentVector
import io.github.aedev.flow.data.recommendation.DisplayForm
import io.github.aedev.flow.data.recommendation.NeuroTokenizer
import io.github.aedev.flow.data.recommendation.UserBrain
import org.junit.Test

class TasteTopicsTest {
    @Test
    fun `lists the strongest unblocked topics relative to the strongest`() {
        val brain =
            UserBrain(
                globalVector = ContentVector(topics = mapOf("comedy" to 0.4, "cooking" to 0.2, "phonk" to 0.3)),
                blockedTopics = setOf("phonk"),
            )

        val topics = tasteTopics(brain, count = 12, pickKeys = emptySet())

        assertThat(topics.map { it.id }).containsExactly("comedy", "cooking").inOrder()
        assertThat(topics.map { it.share }).containsExactly(1.0f, 0.5f).inOrder()
    }

    @Test
    fun `today picks that decayed together read as identical full bars (#907)`() {
        val picks = listOf("android", "mma", "web development", "gym", "ai", "anime").associateWith { 0.3824 }
        val brain = UserBrain(globalVector = ContentVector(topics = picks + ("laptops" to 0.3367)))

        val shares = tasteTopics(brain, count = 12, pickKeys = emptySet()).map { it.share }

        assertThat(shares.take(6)).containsExactly(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
    }

    @Test
    fun `an onboarding pick stored as typed shows as boosted, under its own spelling (#907)`() {
        val brain =
            UserBrain(
                globalVector = ContentVector(topics = mapOf("web development" to 0.38)),
                preferredTopics = setOf("Web Development"),
                displayForms = mapOf("web development" to DisplayForm("Web Development")),
            )

        val topic = tasteTopics(brain, count = 12, pickKeys = NeuroTokenizer().keysOf("Web Development").toSet()).single()

        assertThat(topic.preferred).isTrue()
        assertThat(topic.label).isEqualTo("Web Development")
    }
}
