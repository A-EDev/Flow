package io.github.aedev.flow.ui.screens.settings.taste

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HiddenChannelOrderTest {
    @Test
    fun `named channels sort by name before the ones still without a name`() {
        val ordered =
            hiddenChannelOrder(
                listOf(
                    NamedItem("UCzz", ""),
                    NamedItem("UC1", "veritasium"),
                    NamedItem("UCaa", ""),
                    NamedItem("UC2", "Car Throttle"),
                ),
            )

        assertThat(ordered.map { it.id }).containsExactly("UC2", "UC1", "UCaa", "UCzz").inOrder()
    }
}
