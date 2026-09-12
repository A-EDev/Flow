package io.github.aedev.flow.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import io.github.aedev.flow.data.sponsordetection.SponsorJournalStats
import org.junit.Rule
import org.junit.Test

class SponsorTrainingSettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun consentToggleOpensTheCollectionExplanation() {
        composeRule.setContent {
            MaterialTheme {
                SponsorTrainingSettingsSection(
                    consentEnabled = false,
                    stats = SponsorJournalStats(),
                    onConsentChange = {},
                    onExport = {},
                    onClear = {},
                )
                SponsorTrainingConsentDialog(onDismiss = {}, onConfirm = {})
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_consent_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_consent_dialog_body)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_export)).assertIsNotEnabled()
    }

    @Test
    fun fullJournalShowsPausedMessaging() {
        composeRule.setContent {
            MaterialTheme {
                SponsorTrainingSettingsSection(
                    consentEnabled = true,
                    stats = SponsorJournalStats(evaluationCount = 2, feedbackCount = 1, sizeBytes = 100L * 1024 * 1024, isFull = true),
                    onConsentChange = {},
                    onExport = {},
                    onClear = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_storage_full)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_export)).assertIsDisplayed()
    }

    @Test
    fun postExportClearUsesTheFollowUpCopy() {
        composeRule.setContent {
            MaterialTheme {
                SponsorTrainingClearDialog(afterExport = true, onDismiss = {}, onConfirm = {})
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_clear_after_export)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_delete)).performClick()
    }
}
