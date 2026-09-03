/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.section

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.music.header.MusicSectionAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * MusicShelf is the lane eleven inline home blocks collapsed into. It must draw nothing at all for
 * an empty list — the old blocks each guarded that themselves, and a shelf that renders a bare
 * header over an empty lane is the regression that guard prevented.
 */
class MusicShelfTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val playAll =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(R.string.action_play_all)

    @Test
    fun anEmptyShelfDrawsNothingNotEvenItsHeader() {
        composeRule.setContent {
            MaterialTheme {
                MusicShelf(
                    title = "Nothing here",
                    items = emptyList<String>(),
                    key = { it },
                ) { Text(it) }
            }
        }

        composeRule.onNodeWithText("Nothing here").assertDoesNotExist()
    }

    @Test
    fun aPopulatedShelfDrawsItsHeaderAndItems() {
        composeRule.setContent {
            MaterialTheme {
                MusicShelf(
                    title = "Listen again",
                    items = listOf("one", "two"),
                    key = { it },
                ) { Text(it) }
            }
        }

        composeRule.onNodeWithText("Listen again").assertIsDisplayed()
        composeRule.onNodeWithText("one").assertIsDisplayed()
    }

    @Test
    fun theShelfActionReachesTheHeader() {
        var plays = 0
        composeRule.setContent {
            MaterialTheme {
                MusicShelf(
                    title = "Charts",
                    items = listOf("one"),
                    key = { it },
                    action = MusicSectionAction.PlayAll { plays++ },
                ) { Text(it) }
            }
        }

        composeRule.onNodeWithText(playAll).performClick()
        assertEquals(1, plays)
    }

    @Test
    fun anEmptyTrackShelfAlsoDrawsNothing() {
        composeRule.setContent {
            MaterialTheme {
                MusicTrackShelf(
                    title = "Quick picks",
                    items = emptyList<String>(),
                    key = { it },
                ) { item, _ -> Text(item) }
            }
        }

        composeRule.onNodeWithText("Quick picks").assertDoesNotExist()
    }

    @Test
    fun aPopulatedTrackShelfDrawsItsHeader() {
        composeRule.setContent {
            MaterialTheme {
                MusicTrackShelf(
                    title = "Quick picks",
                    items = listOf("a"),
                    key = { it },
                ) { item, _ -> Text(item) }
            }
        }

        composeRule.onNodeWithText("Quick picks").assertIsDisplayed()
    }
}
