package io.github.aedev.flow.ui.screens.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SyncSetupContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun manualEntryRequiresDataAndSubmitsTrimmedPayload() {
        var submitted: String? = null
        composeRule.setContent {
            MaterialTheme {
                Column {
                    SyncManualEntryContent(onSubmit = { submitted = it })
                }
            }
        }

        val connectButton = composeRule.onNodeWithText(context.getString(R.string.sync_connect))
        connectButton.assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextInput("  pairing-data  ")
        connectButton.assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals("pairing-data", submitted)
        }
    }
}
