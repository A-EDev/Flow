package io.github.aedev.flow.ui.components.layout.navigation

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class FlowNavigationBarTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val selections = mutableListOf<FlowTab>()

    private fun label(tab: FlowTab) = context.getString(tab.labelRes)

    private fun setBar(
        tabs: List<FlowTab>,
        selected: FlowTab,
    ) {
        rule.setContent {
            MaterialTheme {
                FlowNavigationBar(tabs = tabs, selectedTab = selected, onTabSelected = { selections += it })
            }
        }
    }

    @Test
    fun `the selected tab is announced as selected and the others are not`() {
        setBar(FlowTab.entries.take(5), selected = FlowTab.Music)

        rule.onNodeWithText(label(FlowTab.Music)).assertIsSelected()
        rule.onNodeWithText(label(FlowTab.Home)).assertIsNotSelected()
    }

    @Test
    fun `each tab is announced once`() {
        setBar(FlowTab.entries.take(5), selected = FlowTab.Home)

        rule
            .onNodeWithText(label(FlowTab.Home))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun `five tabs fit the bar without an overflow`() {
        setBar(FlowTab.entries.take(5), selected = FlowTab.Home)

        rule.onAllNodesWithText(context.getString(R.string.nav_more)).assertCountEquals(0)
    }

    @Test
    fun `tabs past the fourth move behind More and still navigate`() {
        setBar(FlowTab.entries, selected = FlowTab.Home)

        rule.onAllNodesWithText(label(FlowTab.Library)).assertCountEquals(0)
        rule.onNodeWithText(context.getString(R.string.nav_more)).performClick()
        rule.onNodeWithText(label(FlowTab.Explore)).performClick()

        assertThat(selections).containsExactly(FlowTab.Explore)
    }

    @Test
    fun `More reads as selected while an overflow tab is current`() {
        setBar(FlowTab.entries, selected = FlowTab.Search)

        rule.onNodeWithText(context.getString(R.string.nav_more)).assertIsSelected()
    }
}
