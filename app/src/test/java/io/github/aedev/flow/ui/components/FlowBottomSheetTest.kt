package io.github.aedev.flow.ui.components

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.compose.material3.R as M3R

/**
 * Pins the current behaviour of the progress-based [FlowBottomSheet]: enter animation,
 * visible-height reporting, back and outside-tap dismissal, and the 55% drag-to-dismiss threshold.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class FlowBottomSheetTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val dragHandle
        get() = rule.onNodeWithContentDescription(context.getString(M3R.string.m3c_bottom_sheet_drag_handle_description))

    private fun setSheet(
        dismissOnOutsideTap: Boolean = true,
        onDismiss: () -> Unit = {},
        onVisibleHeightChange: (Float) -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                FlowBottomSheet(
                    onDismiss = onDismiss,
                    dismissOnOutsideTap = dismissOnOutsideTap,
                    onVisibleHeightChange = onVisibleHeightChange,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .testTag(CONTENT_TAG),
                    )
                }
            }
        }
    }

    @Test
    fun contentIsDisplayedOnceTheEnterAnimationSettles() {
        setSheet()

        rule.waitForIdle()

        rule.onNodeWithTag(CONTENT_TAG).assertIsDisplayed()
    }

    @Test
    fun reportsTheVisibleHeightAsTheSheetEnters() {
        val heights = mutableListOf<Float>()
        setSheet(onVisibleHeightChange = { heights += it })

        rule.waitForIdle()

        assertThat(heights.last()).isGreaterThan(0f)
        assertThat(heights.last()).isEqualTo(heights.max())
    }

    @Test
    fun backPressDismissesAfterTheExitAnimation() {
        var dismissed = false
        setSheet(onDismiss = { dismissed = true })
        rule.waitForIdle()

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertThat(dismissed).isTrue()
    }

    @Test
    fun outsideTapDismissesWhenEnabled() {
        var dismissed = false
        setSheet(dismissOnOutsideTap = true, onDismiss = { dismissed = true })
        rule.waitForIdle()

        rule.onRoot().performTouchInput { click(Offset(centerX, 20f)) }
        rule.waitForIdle()

        assertThat(dismissed).isTrue()
    }

    @Test
    fun outsideTapIsIgnoredWhenDisabled() {
        var dismissed = false
        setSheet(dismissOnOutsideTap = false, onDismiss = { dismissed = true })
        rule.waitForIdle()

        rule.onRoot().performTouchInput { click(Offset(centerX, 20f)) }
        rule.waitForIdle()

        assertThat(dismissed).isFalse()
        rule.onNodeWithTag(CONTENT_TAG).assertIsDisplayed()
    }

    @Test
    fun slowDragPastMoreThanHalfTheSheetDismisses() {
        var dismissed = false
        val heights = mutableListOf<Float>()
        setSheet(onDismiss = { dismissed = true }, onVisibleHeightChange = { heights += it })
        rule.waitForIdle()
        val sheetHeightPx = heights.last()

        dragHandle.performTouchInput {
            swipe(start = center, end = center + Offset(0f, sheetHeightPx * 0.7f), durationMillis = 1_000)
        }
        rule.waitForIdle()

        assertThat(dismissed).isTrue()
    }

    @Test
    fun slowDragShortOfTheThresholdSnapsBackOpen() {
        var dismissed = false
        val heights = mutableListOf<Float>()
        setSheet(onDismiss = { dismissed = true }, onVisibleHeightChange = { heights += it })
        rule.waitForIdle()
        val sheetHeightPx = heights.last()

        dragHandle.performTouchInput {
            swipe(start = center, end = center + Offset(0f, sheetHeightPx * 0.3f), durationMillis = 1_000)
        }
        rule.waitForIdle()

        assertThat(dismissed).isFalse()
        rule.onNodeWithTag(CONTENT_TAG).assertIsDisplayed()
        assertThat(heights.last()).isEqualTo(sheetHeightPx)
    }

    private companion object {
        const val CONTENT_TAG = "sheet-content"
    }
}
