package io.github.aedev.flow.ui.components.search

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.ui.components.feedGridLayoutFor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Search used to hardcode its gutters while Home, Subscriptions and the channel took theirs from
 * [feedGridLayoutFor], so a tablet showed search cards 8 dp closer to the edge than every other
 * feed. These pin search to the same single decision.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class SearchGridLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a compact window keeps list cards edge to edge`() {
        val layout = feedGridLayoutFor(411.dp)

        assertThat(layout.isCompact).isTrue()
        assertThat(layout.contentPadding).isEqualTo(0.dp)
        assertThat(layout.columns).isEqualTo(1)
    }

    @Test
    fun `a medium window uses the shared sixteen dp gutter`() {
        val layout = feedGridLayoutFor(700.dp)

        assertThat(layout.isCompact).isFalse()
        assertThat(layout.contentPadding).isEqualTo(16.dp)
    }

    @Test
    fun `a large window uses the shared twenty four dp gutter`() {
        val layout = feedGridLayoutFor(1200.dp)

        assertThat(layout.contentPadding).isEqualTo(24.dp)
        assertThat(layout.columns).isAtLeast(3)
    }

    @Test
    fun `the column preference pins the count search renders`() {
        assertThat(feedGridLayoutFor(1200.dp, HomeFeedColumns.TWO).columns).isEqualTo(2)
        assertThat(feedGridLayoutFor(1200.dp, HomeFeedColumns.AUTO).columns).isAtLeast(3)
    }

    @Test
    fun `the shimmer lays out inside a wide window`() {
        val layout = feedGridLayoutFor(1200.dp)
        rule.setContent {
            MaterialTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(DpSize(1200.dp, 891.dp))) {
                    Box(Modifier.fillMaxSize()) {
                        SearchResultsShimmer(isGridMode = true, feedLayout = layout)
                    }
                }
            }
        }
        rule.waitForIdle()

        assertThat(rule.onRoot().fetchSemanticsNode().children).isNotEmpty()
    }
}
