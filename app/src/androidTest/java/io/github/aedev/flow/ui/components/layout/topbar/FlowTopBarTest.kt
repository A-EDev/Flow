package io.github.aedev.flow.ui.components.layout.topbar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * Guards the rule that fixes the "settings and notifications disappear when Home is disabled" bug:
 * a top bar with no back affordance is a root destination and must offer both shell actions.
 */
class FlowTopBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setBar(
        unread: Int = 0,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            MaterialTheme {
                ProvideFlowGlobalActions(
                    unreadNotifications = MutableStateFlow(unread),
                    onOpenNotifications = {},
                    onOpenSettings = {},
                    content = content,
                )
            }
        }
    }

    @Test
    fun rootDestinationShowsNotificationsAndSettings() {
        setBar { FlowTopBar(title = "Library") }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.notifications))
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.settings))
            .assertIsDisplayed()
    }

    @Test
    fun detailDestinationShowsNeitherShellAction() {
        setBar { FlowTopBar(title = "Buffer", onBack = {}) }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.notifications))
            .assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.settings))
            .assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.btn_back))
            .assertIsDisplayed()
    }

    @Test
    fun brandedLeadingSlotStillCountsAsARootDestination() {
        setBar { FlowTopBar(title = "FLOW", leading = {}) }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.settings))
            .assertIsDisplayed()
    }

    @Test
    fun unreadCountRendersABadge() {
        setBar(unread = 3) { FlowTopBar(title = "Home") }

        composeRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun searchBarNeverShowsShellActions() {
        setBar {
            FlowSearchTopBar(
                query = "",
                onQueryChange = {},
                onClose = {},
                placeholder = "Search settings",
                autoFocus = false,
            )
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.settings))
            .assertDoesNotExist()
    }
}
