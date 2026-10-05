package io.github.aedev.flow.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import io.github.aedev.flow.data.sponsordetection.SponsorModelState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SponsorModelSettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun modelSectionShowsSourceAndThreeCategoryDescription() {
        composeRule.setContent {
            MaterialTheme {
                SponsorModelSettingsSection(
                    enabled = true,
                    modelState = SponsorModelState.Installed(61_677_989),
                    onEnabledChange = {},
                    onDownload = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.sponsor_model_source)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.sponsor_model_toggle_subtitle)).assertIsDisplayed()
    }

    @Test
    fun updateSettingsOfferExplicitDownloadAction() {
        var downloads = 0
        composeRule.setContent {
            MaterialTheme {
                SponsorModelSettingsSection(
                    enabled = true,
                    modelState = SponsorModelState.UpdateAvailable,
                    onEnabledChange = {},
                    onDownload = { downloads++ },
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.sponsor_model_update_download)).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, downloads) }
    }

    @Test
    fun updateNoticeOffersDownloadAndDeferral() {
        var downloads = 0
        var dismissals = 0
        composeRule.setContent {
            MaterialTheme {
                SponsorModelUpdateDialog(onDownload = { downloads++ }, onDismiss = { dismissals++ })
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.sponsor_model_update_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.sponsor_model_update_later)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.sponsor_model_update_download)).performClick()
        composeRule.runOnIdle {
            assertEquals(1, downloads)
            assertEquals(1, dismissals)
        }
    }
}
