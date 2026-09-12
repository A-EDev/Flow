package io.github.aedev.flow.ui.screens.player.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import io.github.aedev.flow.data.sponsordetection.SponsorComparison
import io.github.aedev.flow.data.sponsordetection.SponsorDetectionStatus
import io.github.aedev.flow.data.sponsordetection.SponsorDetectionUiState
import io.github.aedev.flow.data.sponsordetection.SponsorPredictedSpan
import org.junit.Rule
import org.junit.Test

class SponsorDetectionReviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun reviewChipIsHiddenUntilASuccessfulOptInEvaluation() {
        composeRule.setContent {
            MaterialTheme {
                SponsorDetectionReviewUi(
                    videoId = "video",
                    durationMs = 60_000,
                    state = SponsorDetectionUiState(videoId = "video", status = SponsorDetectionStatus.READY),
                    snackbarHostState = remember { SnackbarHostState() },
                    onSeekTo = {},
                    currentPositionMs = { 0 },
                    onRecordFeedback = { _, _, _ -> true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_review)).assertDoesNotExist()
    }

    @Test
    fun zeroPredictionReviewOffersConfirmAndMissedActions() {
        composeRule.setContent {
            MaterialTheme {
                SponsorDetectionReviewUi(
                    videoId = "video",
                    durationMs = 60_000,
                    state =
                        SponsorDetectionUiState(
                            videoId = "video",
                            status = SponsorDetectionStatus.READY,
                            reviewAvailable = true,
                            comparison = SponsorComparison(emptyList(), emptyList(), emptyList()),
                        ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onSeekTo = {},
                    currentPositionMs = { 0 },
                    onRecordFeedback = { _, _, _ -> true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_review)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_confirm_none)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_add_missed)).assertIsDisplayed()
    }

    @Test
    fun predictedSpanShowsAcceptRejectAndEdit() {
        composeRule.setContent {
            MaterialTheme {
                SponsorDetectionReviewUi(
                    videoId = "video",
                    durationMs = 60_000,
                    state =
                        SponsorDetectionUiState(
                            videoId = "video",
                            status = SponsorDetectionStatus.READY,
                            reviewAvailable = true,
                            predictions = listOf(SponsorPredictedSpan("p1", 1_000, 2_000, 0.9)),
                            comparison = SponsorComparison(emptyList(), listOf("p1"), emptyList()),
                        ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onSeekTo = {},
                    currentPositionMs = { 0 },
                    onRecordFeedback = { _, _, _ -> true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_review)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_accept)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_reject)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_edit)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.sponsor_training_model_only)).assertIsDisplayed()
    }

    @Test
    fun timestampEditorDisablesSaveForInvalidRange() {
        composeRule.setContent {
            MaterialTheme {
                SponsorTimestampEditor(
                    initialStartMs = 1_000,
                    initialEndMs = 2_000,
                    durationMs = 5_000,
                    onDismiss = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.btn_save)).assertIsEnabled()
        composeRule.onNodeWithText("00:02").performTextReplacement("00:00")
        composeRule.onNodeWithText(context.getString(R.string.btn_save)).assertIsNotEnabled()
    }
}
