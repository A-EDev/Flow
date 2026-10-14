package io.github.aedev.flow.ui.components.shared.card

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoCardFeedbackLayoutTest {
    private fun plan(
        labels: List<Float>,
        available: Float,
    ) = planFeedbackLayout(labelWidths = labels, available = available, iconRoom = 24f, padding = 24f)

    @Test
    fun `icons stay when both labels fit beside them`() {
        val layout = plan(listOf(120f, 80f), available = 300f)

        assertThat(layout.showIcons).isTrue()
    }

    @Test
    fun `icons drop before any label is cut`() {
        val layout = plan(listOf(120f, 80f), available = 260f)

        assertThat(layout.showIcons).isFalse()
    }

    @Test
    fun `the longer label gets the wider button`() {
        val layout = plan(listOf(120f, 80f), available = 300f)

        assertThat(layout.weights[0]).isGreaterThan(layout.weights[1])
        assertThat(layout.weights).containsExactly(168f, 128f).inOrder()
    }

    @Test
    fun `without icons each button needs only its label and padding`() {
        val layout = plan(listOf(120f, 80f), available = 200f)

        assertThat(layout.weights).containsExactly(144f, 104f).inOrder()
    }
}
