package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
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

class OnboardingComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun skipKeepsMinimumTouchTargetAndReportsClick() {
        var skipClicks = 0
        composeRule.setContent {
            MaterialTheme {
                OnboardingBottomBar(
                    isFirstStep = true,
                    isLastStep = false,
                    canAdvance = true,
                    onBack = {},
                    onNext = {},
                    onSkip = { skipClicks++ },
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val skipNode = composeRule.onNodeWithText(context.getString(R.string.onboarding_btn_skip))
        val bounds = skipNode.getUnclippedBoundsInRoot()

        assertTrue(bounds.width.value >= 48f)
        assertTrue(bounds.height.value >= 48f)
        skipNode.performClick()
        assertEquals(1, skipClicks)
    }

    @Test
    fun continueIsDisabledWhenStepCannotAdvance() {
        composeRule.setContent {
            MaterialTheme {
                OnboardingBottomBar(
                    isFirstStep = true,
                    isLastStep = false,
                    canAdvance = false,
                    onBack = {},
                    onNext = {},
                    onSkip = {},
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule
            .onNodeWithText(context.getString(R.string.onboarding_btn_continue))
            .assertIsNotEnabled()
    }
}
