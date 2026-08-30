package io.github.aedev.flow.ui.screens.music

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.YouTube.SearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MusicSearchComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedFilterExposesSelectionSemantics() {
        composeRule.setContent {
            MaterialTheme {
                SearchFilterChips(
                    activeFilter = SearchFilter.FILTER_ALBUM,
                    onFilterClick = {},
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule
            .onNodeWithText(context.getString(R.string.filter_albums))
            .assertIsSelected()
    }

    @Test
    fun tappingSelectedFilterClearsIt() {
        var selectedFilter: SearchFilter? = SearchFilter.FILTER_ALBUM
        composeRule.setContent {
            MaterialTheme {
                SearchFilterChips(
                    activeFilter = selectedFilter,
                    onFilterClick = { selectedFilter = it },
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule
            .onNodeWithText(context.getString(R.string.filter_albums))
            .performClick()

        assertEquals(null, selectedFilter)
    }

    @Test
    fun searchActionsKeepMinimumTouchTargets() {
        composeRule.setContent {
            MaterialTheme {
                MusicSearchBar(
                    query = "Muse",
                    onQueryChange = {},
                    onSearch = {},
                    onBackClick = {},
                    onClearClick = {},
                    onVoiceSearchClick = {},
                )
            }
        }

        composeRule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clearBounds =
            composeRule
                .onNodeWithContentDescription(context.getString(R.string.clear))
                .getUnclippedBoundsInRoot()
        val voiceBounds =
            composeRule
                .onNodeWithContentDescription(context.getString(R.string.voice_search_cd))
                .getUnclippedBoundsInRoot()

        assertTrue(clearBounds.width.value >= 48f)
        assertTrue(clearBounds.height.value >= 48f)
        assertTrue(voiceBounds.width.value >= 48f)
        assertTrue(voiceBounds.height.value >= 48f)
    }
}
