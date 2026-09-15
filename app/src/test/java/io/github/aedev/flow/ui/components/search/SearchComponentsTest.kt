package io.github.aedev.flow.ui.components.search

import android.app.Application
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.Duration
import io.github.aedev.flow.data.local.SearchFeature
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.local.SortType
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.ui.components.shared.FlowSuggestionRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The search surface's own pixels: the creator card's metadata line, the filter row's state, the
 * typeahead row and the paging tail.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class SearchComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun string(id: Int) = context.getString(id)

    private fun show(content: @Composable () -> Unit) {
        rule.setContent { MaterialTheme { content() } }
        rule.waitForIdle()
    }

    private val samSulek =
        Channel(
            id = "UCAuk798iHprjTtwlClkFxMA",
            name = "Sam Sulek",
            thumbnailUrl = "https://yt3.test/a.jpg",
            subscriberCount = 4_540_000L,
            description = "Bodybuilding content",
            handle = "@sam_sulek",
            videoCount = 412,
            isVerified = true,
        )

    @Test
    fun `the creator card shows the handle and the subscriber count together`() {
        show { SearchChannelHeroCard(samSulek, isSubscribed = false, onSubscribeToggle = {}, onClick = {}) }

        rule.onNodeWithText("Sam Sulek").assertIsDisplayed()
        rule.onNodeWithText("@sam_sulek", substring = true).assertIsDisplayed()
        rule.onNodeWithText("subscribers", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.verified)).assertIsDisplayed()
    }

    @Test
    fun `the creator card leads into the channel`() {
        var opened = false
        show {
            SearchChannelHeroCard(samSulek, isSubscribed = false, onSubscribeToggle = {}, onClick = { opened = true })
        }

        rule.onNodeWithText(string(R.string.search_channel_go_to)).performClick()

        assertThat(opened).isTrue()
    }

    @Test
    fun `the creator card leaves out counts youtube did not send`() {
        show {
            SearchChannelHeroCard(
                channel = samSulek.copy(subscriberCount = 0, videoCount = 0, isVerified = false),
                isSubscribed = false,
                onSubscribeToggle = {},
                onClick = {},
            )
        }

        rule.onNodeWithText("@sam_sulek", substring = true).assertIsDisplayed()
        assertThat(nodesContaining("subscribers")).isEmpty()
    }

    @Test
    fun `the creator card offers a subscribe action`() {
        var toggled = false
        show {
            SearchChannelHeroCard(samSulek, isSubscribed = false, onSubscribeToggle = { toggled = true }, onClick = {})
        }

        rule.onNodeWithText(string(R.string.subscribe)).performClick()

        assertThat(toggled).isTrue()
    }

    @Test
    fun `the creator card leaves the strip out when the response carried none`() {
        show { SearchChannelHeroCard(samSulek, isSubscribed = false, onSubscribeToggle = {}, onClick = {}) }

        assertThat(nodesContaining("Latest from")).isEmpty()
    }

    @Test
    fun `the top bar badges how many narrowing choices are active`() {
        val filter =
            SearchFilter(
                contentType = ContentType.VIDEOS,
                duration = Duration.OVER_20_MINUTES,
                sortType = SortType.VIEW_COUNT,
                features = setOf(SearchFeature.FOUR_K),
            )
        show {
            androidx.compose.foundation.layout.Row {
                SearchTopBarActions(
                    activeFilterCount = filter.activeCount,
                    isGridMode = false,
                    onOpenFilters = {},
                    onToggleGridMode = {},
                )
            }
        }

        rule.onNodeWithText("4").assertIsDisplayed()
    }

    @Test
    fun `the chip row offers shorts second, where youtube puts it`() {
        show {
            SearchFilterBar(
                selected = ContentType.ALL,
                shortsEnabled = true,
                onContentTypeSelected = {},
            )
        }

        rule.onNodeWithText(string(R.string.tab_shorts)).assertIsDisplayed()
    }

    @Test
    fun `the chip row hides shorts when the user turned shorts off`() {
        show {
            SearchFilterBar(
                selected = ContentType.ALL,
                shortsEnabled = false,
                onContentTypeSelected = {},
            )
        }

        assertThat(nodesContaining(string(R.string.tab_shorts))).isEmpty()
    }

    @Test
    fun `picking a type reports it once`() {
        val picked = mutableListOf<ContentType>()
        show {
            SearchFilterBar(
                selected = ContentType.ALL,
                shortsEnabled = true,
                onContentTypeSelected = picked::add,
            )
        }

        rule.onNodeWithText(string(R.string.channels_header)).performClick()

        assertThat(picked).containsExactly(ContentType.CHANNELS)
    }

    @Test
    fun `a typeahead row reports the row and its fill action separately`() {
        var submitted = false
        var filled = false
        show {
            FlowSuggestionRow(
                text = "sam sulek bulking",
                leadingIcon = Icons.Rounded.History,
                onClick = { submitted = true },
                query = "sam",
                trailingIcon = Icons.Rounded.History,
                trailingContentDescription = string(R.string.resize_fill),
                onTrailingClick = { filled = true },
            )
        }

        rule.onNodeWithText("sam sulek bulking").performClick()
        rule.onNodeWithContentDescription(string(R.string.resize_fill)).performClick()

        assertThat(submitted).isTrue()
        assertThat(filled).isTrue()
    }

    @Test
    fun `the paging tail says when the results have run out`() {
        show {
            SearchPagingFooter(
                appendState = LoadState.NotLoading(endOfPaginationReached = true),
                itemCount = 12,
                onRetry = {},
            )
        }

        rule.onNodeWithText(string(R.string.end_of_results)).assertIsDisplayed()
    }

    @Test
    fun `the paging tail offers a retry after a failed append`() {
        var retried = false
        show {
            SearchPagingFooter(
                appendState = LoadState.Error(IllegalStateException("offline")),
                itemCount = 12,
                onRetry = { retried = true },
            )
        }

        rule.onNodeWithText(string(R.string.retry)).performClick()

        assertThat(retried).isTrue()
    }

    @Test
    fun `an empty page shows no tail at all`() {
        show {
            SearchPagingFooter(
                appendState = LoadState.NotLoading(endOfPaginationReached = true),
                itemCount = 0,
                onRetry = {},
            )
        }

        assertThat(nodesContaining(string(R.string.end_of_results))).isEmpty()
    }

    private fun nodesContaining(text: String) =
        rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes()
}
