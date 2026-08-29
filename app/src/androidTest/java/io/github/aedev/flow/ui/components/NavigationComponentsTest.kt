package io.github.aedev.flow.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

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
        val centers =
            listOf(R.string.nav_home, R.string.nav_subs, R.string.nav_library)
                .map { stringRes ->
                    val bounds =
                        composeRule
                            .onNodeWithText(context.getString(stringRes))
                            .getUnclippedBoundsInRoot()
                    ((bounds.left + bounds.right) / 2f).value
                }

        assertTrue(abs((centers[1] - centers[0]) - (centers[2] - centers[1])) < 1f)
    }

    @Test
    fun selectedDestinationExposesSelectionSemantics() {
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
        composeRule
            .onNodeWithText(context.getString(R.string.nav_home))
            .assertIsSelected()
    }

    @Test
    fun tappingDestinationReportsItsNavigationIndex() {
        var selectedIndex = -1
        composeRule.setContent {
            MaterialTheme {
                FloatingBottomNavBar(
                    selectedIndex = 0,
                    onItemSelected = { selectedIndex = it },
                    isHomeEnabled = true,
                    isShortsEnabled = false,
                    isMusicEnabled = false,
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule
            .onNodeWithText(context.getString(R.string.nav_subs))
            .performClick()

        assertEquals(3, selectedIndex)
    }
}
