package io.github.aedev.flow.ui.screens.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryOverviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun quickActionsRouteToTheirLibraryDestinations() {
        var destination = ""
        composeRule.setContent {
            MaterialTheme {
                LibraryOverviewCard(
                    onHistoryClick = { destination = "history" },
                    onPlaylistsClick = { destination = "playlists" },
                    onWatchLaterClick = { destination = "watch-later" },
                    onDownloadsClick = { destination = "downloads" },
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.onNodeWithText(context.getString(R.string.library_history_label)).performClick()
        assertEquals("history", destination)
        composeRule.onNodeWithText(context.getString(R.string.library_playlists_label)).performClick()
        assertEquals("playlists", destination)
        composeRule.onNodeWithText(context.getString(R.string.library_watch_later_label)).performClick()
        assertEquals("watch-later", destination)
        composeRule.onNodeWithText(context.getString(R.string.library_downloads_label)).performClick()
        assertEquals("downloads", destination)
    }
}
