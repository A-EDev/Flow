package io.github.aedev.flow.ui.components.shared

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Smoke test for the JVM Compose harness: proves Robolectric can host a Material 3 tree from the
 * alpha BOM. Every other Compose unit test in this source set relies on the same configuration.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class FlowLoadingIndicatorRobolectricTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders() {
        rule.setContent { MaterialTheme { FlowLoadingIndicator() } }

        rule.onRoot().assertExists().assertIsDisplayed()
    }
}
