/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.header

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * MusicSectionHeader replaced four headers that disagreed on trailing affordance. Each action
 * must render only its own control, and only the Navigate variant may make the header clickable.
 */
class MusicSectionHeaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val playAll = context.getString(R.string.action_play_all)
    private val viewAll = context.getString(R.string.action_view_all)
    private val navigate = context.getString(R.string.ui_navigate)

    @Test
    fun withoutAnActionOnlyTheTitleRenders() {
        composeRule.setContent { MaterialTheme { MusicSectionHeader(title = "Quick picks") } }

        composeRule.onNodeWithText("Quick picks").assertIsDisplayed()
        composeRule.onNodeWithText(playAll).assertDoesNotExist()
        composeRule.onNodeWithText(viewAll).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(navigate).assertDoesNotExist()
    }

    @Test
    fun playAllRendersOnlyThePlayPill() {
        composeRule.setContent {
            MaterialTheme {
                MusicSectionHeader(title = "Charts", action = MusicSectionAction.PlayAll {})
            }
        }

        composeRule.onNodeWithText(playAll).assertIsDisplayed()
        composeRule.onNodeWithText(viewAll).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(navigate).assertDoesNotExist()
    }

    @Test
    fun seeAllRendersOnlyTheViewAllButton() {
        composeRule.setContent {
            MaterialTheme {
                MusicSectionHeader(title = "Albums", action = MusicSectionAction.SeeAll {})
            }
        }

        composeRule.onNodeWithText(viewAll).assertIsDisplayed()
        composeRule.onNodeWithText(playAll).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(navigate).assertDoesNotExist()
    }

    @Test
    fun navigateRendersTheArrowAndFiresFromTheWholeHeader() {
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                MusicSectionHeader(
                    title = "Moods and genres",
                    action = MusicSectionAction.Navigate { clicks++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription(navigate).assertIsDisplayed()
        composeRule.onNodeWithText("Moods and genres").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun playAllFiresOnlyFromThePillNotFromTheTitle() {
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                MusicSectionHeader(title = "Listen again", action = MusicSectionAction.PlayAll { clicks++ })
            }
        }

        composeRule.onNodeWithText("Listen again").performClick()
        assertEquals(0, clicks)

        composeRule.onNodeWithText(playAll).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun subtitleAndLeadingSlotBothRender() {
        composeRule.setContent {
            MaterialTheme {
                MusicSectionHeader(
                    title = "Because you played",
                    subtitle = "Radio",
                    leading = { Text("art") },
                )
            }
        }

        composeRule.onNodeWithText("Radio").assertIsDisplayed()
        composeRule.onNodeWithText("art").assertIsDisplayed()
        composeRule.onNodeWithText("Because you played").assertIsDisplayed()
    }
}
