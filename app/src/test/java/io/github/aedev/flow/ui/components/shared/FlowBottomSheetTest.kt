package io.github.aedev.flow.ui.components.shared

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
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
 * visible-height reporting, back and outside-tap dismissal, the 55% drag-to-dismiss threshold, and
 * the fixed-height, collapsed-floor, hosted and width-capped modes the player sheets rely on.
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
        expandedHeight: Dp? = null,
        collapsedHeight: Dp = 0.dp,
        dismissible: Boolean = true,
        onDismiss: () -> Unit = {},
        onVisibleHeightChange: (Float) -> Unit = {},
        onProgressChange: (Float) -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                FlowBottomSheet(
                    onDismiss = onDismiss,
                    expandedHeight = expandedHeight,
                    collapsedHeight = collapsedHeight,
                    dismissible = dismissible,
                    dismissOnOutsideTap = dismissOnOutsideTap,
                    onProgressChange = onProgressChange,
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

    @Test
    fun expandedHeightSizesTheSheetSurface() {
        setSheet(expandedHeight = 240.dp)

        rule.waitForIdle()

        rule.onNodeWithTag(CONTENT_TAG).onParent().assertHeightIsEqualTo(240.dp)
    }

    @Test
    fun collapsedHeightLeavesTheSheetStandingWhenDismissIsReported() {
        var dismissed = false
        val heights = mutableListOf<Float>()
        setSheet(
            expandedHeight = 300.dp,
            collapsedHeight = 100.dp,
            onDismiss = { dismissed = true },
            onVisibleHeightChange = { heights += it },
        )
        rule.waitForIdle()

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        val collapsedPx = with(rule.density) { 100.dp.toPx() }
        assertThat(dismissed).isTrue()
        assertThat(heights.last()).isWithin(1f).of(collapsedPx)
    }

    @Test
    fun progressRunsFromZeroToOneAndBackOnDismiss() {
        val progress = mutableListOf<Float>()
        setSheet(expandedHeight = 300.dp, onProgressChange = { progress += it })
        rule.waitForIdle()

        assertThat(progress.first()).isEqualTo(0f)
        assertThat(progress.last()).isEqualTo(1f)

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertThat(progress.last()).isEqualTo(0f)
    }

    @Test
    fun aHostedSheetIgnoresDrag() {
        var dismissed = false
        val heights = mutableListOf<Float>()
        setSheet(
            expandedHeight = 300.dp,
            dismissible = false,
            onDismiss = { dismissed = true },
            onVisibleHeightChange = { heights += it },
        )
        rule.waitForIdle()
        val sheetHeightPx = heights.last()

        dragHandle.performTouchInput {
            swipe(start = center, end = center + Offset(0f, sheetHeightPx * 0.7f), durationMillis = 1_000)
        }
        rule.waitForIdle()

        assertThat(dismissed).isFalse()
        assertThat(heights.last()).isEqualTo(sheetHeightPx)
        rule.onNodeWithTag(CONTENT_TAG).assertIsDisplayed()
    }

    @Test
    fun aHostedSheetStillDismissesOnBackWithoutAnimating() {
        // The fullscreen side panel hosts these sheets and closes itself from this callback, so a
        // non-dismissible sheet must still answer back — it just reports straight away.
        var dismissed = false
        val progress = mutableListOf<Float>()
        setSheet(
            expandedHeight = 300.dp,
            dismissible = false,
            onDismiss = { dismissed = true },
            onProgressChange = { progress += it },
        )
        rule.waitForIdle()

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertThat(dismissed).isTrue()
        assertThat(progress.last()).isEqualTo(1f)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun theSheetIsCappedAtTheMaterialSheetWidthOnALargeScreen() {
        setSheet()

        rule.waitForIdle()

        rule.onNodeWithTag(CONTENT_TAG).assertWidthIsEqualTo(BottomSheetDefaults.SheetMaxWidth)
    }

    private companion object {
        const val CONTENT_TAG = "sheet-content"
    }
}
