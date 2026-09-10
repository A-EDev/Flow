package io.github.aedev.flow.ui.components.shared

import android.app.Application
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
 * Pins the shared settings rows: the accessibility roles the old hand-rolled `Surface` rows never
 * had, and the row heights, which Material 3's one-line/two-line minimums decide rather than the
 * content padding we hand them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class FlowRowsTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun string(id: Int) = context.getString(id)

    private fun setRow(content: @Composable () -> Unit) {
        rule.setContent { MaterialTheme { content() } }
        rule.waitForIdle()
    }

    private fun rowHeight(tag: String = ROW_TAG): Dp {
        val bounds = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return bounds.bottom - bounds.top
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun selectionRowIsARadioButtonThatReportsItsSelection() {
        var clicked = false
        setRow {
            FlowSelectionRow(
                title = "1080p",
                selected = true,
                onClick = { clicked = true },
                modifier = Modifier.testTag(ROW_TAG),
            )
        }

        rule.onNodeWithTag(ROW_TAG).assert(hasRole(Role.RadioButton))
        rule.onNodeWithTag(ROW_TAG).assertIsSelected()
        rule.onNodeWithText("1080p").assertIsDisplayed()

        rule.onNodeWithTag(ROW_TAG).performClick()
        rule.waitForIdle()
        assertThat(clicked).isTrue()
    }

    @Test
    fun unselectedSelectionRowReportsItsSelection() {
        setRow {
            FlowSelectionRow(
                title = "720p",
                selected = false,
                onClick = {},
                modifier = Modifier.testTag(ROW_TAG),
            )
        }

        rule.onNodeWithTag(ROW_TAG).assertIsNotSelected()
    }

    @Test
    fun selectionRowIsAOneLineRowWithoutSupportingText() {
        setRow {
            FlowSelectionRow(
                title = "1080p",
                selected = true,
                onClick = {},
                modifier = Modifier.testTag(ROW_TAG),
            )
        }

        assertThat(rowHeight()).isEqualTo(ONE_LINE_ROW_HEIGHT)
    }

    @Test
    fun selectionRowWithSupportingTextIsATwoLineRow() {
        setRow {
            FlowSelectionRow(
                title = "1080p",
                selected = true,
                onClick = {},
                modifier = Modifier.testTag(ROW_TAG),
                supportingText = "English",
            )
        }

        rule.onNodeWithText("English").assertExists()
        assertThat(rowHeight()).isEqualTo(TWO_LINE_ROW_HEIGHT)
    }

    @Test
    fun navRowShowsItsTrailingValueAndFiresOnClick() {
        var clicked = false
        setRow {
            FlowNavRow(
                title = string(R.string.playback_speed),
                onClick = { clicked = true },
                modifier = Modifier.testTag(ROW_TAG),
                leadingIcon = Icons.Filled.Speed,
                trailingText = string(R.string.normal),
            )
        }

        rule.onNodeWithText(string(R.string.playback_speed)).assertExists()
        rule.onNodeWithText(string(R.string.normal)).assertExists()
        assertThat(rowHeight()).isEqualTo(ONE_LINE_ROW_HEIGHT)

        rule.onNodeWithTag(ROW_TAG).performClick()
        rule.waitForIdle()
        assertThat(clicked).isTrue()
    }

    @Test
    fun navRowWithoutATrailingValueRendersOnlyTheChevron() {
        setRow {
            FlowNavRow(
                title = string(R.string.cast_to_tv),
                onClick = {},
                modifier = Modifier.testTag(ROW_TAG),
                leadingIcon = Icons.Filled.Cast,
            )
        }

        rule.onNodeWithText(string(R.string.cast_to_tv)).assertExists()
        assertThat(rowHeight()).isEqualTo(ONE_LINE_ROW_HEIGHT)
    }

    @Test
    fun switchRowIsASwitchThatTogglesFromAnywhereInTheRow() {
        var toggled: Boolean? = null
        setRow {
            FlowSwitchRow(
                title = string(R.string.loop_video),
                checked = false,
                onCheckedChange = { toggled = it },
                modifier = Modifier.testTag(ROW_TAG),
                leadingIcon = Icons.Rounded.Repeat,
            )
        }

        rule.onNodeWithTag(ROW_TAG).assert(hasRole(Role.Switch))
        rule.onNodeWithTag(ROW_TAG).assertIsOff()
        assertThat(rowHeight()).isEqualTo(ONE_LINE_ROW_HEIGHT)

        rule.onNodeWithTag(ROW_TAG).performClick()
        rule.waitForIdle()
        assertThat(toggled).isTrue()
    }

    @Test
    fun checkedSwitchRowReportsItsState() {
        setRow {
            FlowSwitchRow(
                title = string(R.string.loop_video),
                checked = true,
                onCheckedChange = {},
                modifier = Modifier.testTag(ROW_TAG),
            )
        }

        rule.onNodeWithTag(ROW_TAG).assertIsOn()
    }

    @Test
    fun disabledSwitchRowIsInertAndAnnouncedAsDisabled() {
        var toggled = false
        setRow {
            FlowSwitchRow(
                title = string(R.string.autoplay_next),
                checked = false,
                onCheckedChange = { toggled = true },
                modifier = Modifier.testTag(ROW_TAG),
                enabled = false,
            )
        }

        rule.onNodeWithTag(ROW_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(ROW_TAG).performClick()
        rule.waitForIdle()
        assertThat(toggled).isFalse()
    }

    @Test
    fun sectionHeaderRendersItsLabel() {
        setRow { FlowSectionHeader(text = string(R.string.audio_effects)) }

        rule.onNodeWithText(string(R.string.audio_effects)).assertIsDisplayed()
    }

    @Test
    fun audioTrackRowKeepsTheTrackLabelAndBitrateLine() {
        setRow {
            MediaAudioTrackRow(
                label = "English",
                selected = true,
                onClick = {},
                modifier = Modifier.testTag(ROW_TAG),
                supportingText = audioTrackBitrateLabel(128_000),
            )
        }

        rule.onNodeWithText("English").assertExists()
        rule.onNodeWithText("128 ${string(R.string.kbps)}").assertExists()
        rule.onNodeWithTag(ROW_TAG).assertIsSelected()
    }

    @Test
    fun audioTrackBitrateLabelIsDroppedBelowOneKilobit() {
        var label: String? = "unset"
        setRow { label = audioTrackBitrateLabel(999) }

        assertThat(label).isNull()
    }

    @Test
    fun audioTrackFallbackLabelNumbersTracksFromOne() {
        var label = ""
        setRow { label = audioTrackFallbackLabel(1) }

        assertThat(label).isEqualTo(
            context.getString(
                R.string.audio_track_number_template,
                string(R.string.audio_track),
                2,
            ),
        )
    }

    @Test
    fun playbackSpeedLabelSpellsNormalAndTheMultiplierTheSameWay() {
        var normal = ""
        var fast = ""
        setRow {
            normal = playbackSpeedLabel(1.0f)
            fast = playbackSpeedLabel(1.5f)
        }

        assertThat(normal).isEqualTo(string(R.string.normal))
        assertThat(fast).isEqualTo(
            context.getString(R.string.playback_speed_multiplier, "1.5"),
        )
    }

    private companion object {
        const val ROW_TAG = "row"

        /** Material 3's one-line list item minimum; it overrides the row's own content padding. */
        val ONE_LINE_ROW_HEIGHT = 56.dp

        /** Material 3's two-line list item minimum. */
        val TWO_LINE_ROW_HEIGHT = 72.dp
    }
}
