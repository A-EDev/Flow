package io.github.aedev.flow.ui.components.videoplayer.sheet

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Pins the smallest of the hand-rolled height-driven sheets, [FlowLiveChatBottomSheet], so the
 * consolidation onto one sheet primitive can prove it kept the title, close, progress and height
 * contract.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class FlowLiveChatBottomSheetTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun setSheet(
        expandedHeight: Dp? = 320.dp,
        onDismiss: () -> Unit = {},
        onSheetProgressChange: (Float) -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                FlowLiveChatBottomSheet(
                    messages = emptyList(),
                    isLoading = false,
                    onDismiss = onDismiss,
                    expandedHeight = expandedHeight,
                    onSheetProgressChange = onSheetProgressChange,
                )
            }
        }
    }

    @Test
    fun showsTheLiveChatTitle() {
        setSheet()

        rule.onNodeWithText(context.getString(R.string.live_chat)).assertIsDisplayed()
    }

    @Test
    fun closeIconDismissesAfterTheExitAnimation() {
        var dismissed = false
        setSheet(onDismiss = { dismissed = true })
        rule.waitForIdle()

        rule.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        rule.waitForIdle()

        assertThat(dismissed).isTrue()
    }

    @Test
    fun progressReachesOneOnceExpanded() {
        val progress = mutableListOf<Float>()
        setSheet(onSheetProgressChange = { progress += it })

        rule.waitForIdle()

        assertThat(progress.first()).isEqualTo(0f)
        assertThat(progress.last()).isEqualTo(1f)
    }

    @Test
    fun expandedHeightSizesTheSheetSurface() {
        setSheet(expandedHeight = 320.dp)
        rule.waitForIdle()

        rule
            .onNodeWithText(context.getString(R.string.live_chat))
            .onParent()
            .assertHeightIsEqualTo(320.dp)
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
}
