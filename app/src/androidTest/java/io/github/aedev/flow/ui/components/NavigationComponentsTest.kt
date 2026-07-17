package io.github.aedev.flow.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun threeDestinationsUseEqualWidthSlots() {
        composeRule.setContent {
            MaterialTheme {
                FloatingBottomNavBar(
                    selectedIndex = 0,
                    onItemSelected = {},
                    isHomeEnabled = true,
                    isShortsEnabled = false,
                    isMusicEnabled = false,
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val centers = listOf(R.string.nav_home, R.string.nav_subs, R.string.nav_library)
            .map { stringRes ->
                val bounds = composeRule.onNodeWithText(context.getString(stringRes))
                    .getUnclippedBoundsInRoot()
                (bounds.left.value + bounds.right.value) / 2f
            }

        assertTrue(abs((centers[1] - centers[0]) - (centers[2] - centers[1])) < 1f)
    }
}
