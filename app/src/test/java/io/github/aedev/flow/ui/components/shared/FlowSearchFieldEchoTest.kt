package io.github.aedev.flow.ui.components.shared

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class FlowSearchFieldEchoTest {
    @get:Rule
    val rule = createComposeRule()

    private var query by mutableStateOf("")
    private val reported = mutableListOf<String>()

    private fun showField(echoImmediately: Boolean) {
        rule.setContent {
            MaterialTheme {
                FlowSearchField(
                    query = query,
                    onQueryChange = {
                        reported += it
                        if (echoImmediately) query = it
                    },
                    placeholder = "Search",
                )
            }
        }
    }

    @Test
    fun `a late echo does not rewind what the user typed`() {
        showField(echoImmediately = false)
        val field = rule.onNode(hasSetTextAction())
        field.performTextInput("a")
        rule.waitForIdle()
        field.performTextInput("b")
        rule.waitForIdle()

        // The caller's pipeline hands both edits back only now, oldest first.
        query = "a"
        rule.waitForIdle()
        query = "ab"
        rule.waitForIdle()

        field.assertTextEquals("ab", includeEditableText = true)
        assertThat(reported).containsExactly("a", "ab").inOrder()
    }

    @Test
    fun `a query set from outside still replaces the text`() {
        showField(echoImmediately = true)
        val field = rule.onNode(hasSetTextAction())
        field.performTextInput("cat")
        rule.waitForIdle()

        query = "dogs"
        rule.waitForIdle()

        field.assertTextEquals("dogs", includeEditableText = true)
    }
}
