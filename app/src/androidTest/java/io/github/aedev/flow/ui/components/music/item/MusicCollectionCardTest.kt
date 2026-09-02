/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.item

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * MusicCollectionCard replaced five grid cards plus every raw GridItem call. The variants that
 * used to be separate composables — circular artists, 16:9 videos, an overflow affordance — must
 * all be reachable through its parameters.
 */
class MusicCollectionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val downloaded = context.getString(R.string.status_downloaded)
    private val moreOptions = context.getString(R.string.more_options)

    @Test
    fun titleRendersAndSubtitleIsOptional() {
        composeRule.setContent {
            MaterialTheme {
                MusicCollectionCard(title = "Rumours", onClick = {})
            }
        }

        composeRule.onNodeWithText("Rumours").assertIsDisplayed()
    }

    @Test
    fun aBlankSubtitleIsNotRendered() {
        composeRule.setContent {
            MaterialTheme {
                MusicCollectionCard(title = "Rumours", subtitle = "   ", onClick = {})
            }
        }

        composeRule.onNodeWithText("   ").assertDoesNotExist()
    }

    @Test
    fun subtitleRendersWhenPresent() {
        composeRule.setContent {
            MaterialTheme {
                MusicCollectionCard(title = "Rumours", subtitle = "Fleetwood Mac", onClick = {})
            }
        }

        composeRule.onNodeWithText("Fleetwood Mac").assertIsDisplayed()
    }

    @Test
    fun theDownloadedBadgeAppearsOnlyWhenDownloaded() {
        composeRule.setContent {
            MaterialTheme {
                MusicCollectionCard(title = "Offline", onClick = {}, isDownloaded = true)
            }
        }

        composeRule.onNodeWithContentDescription(downloaded).assertIsDisplayed()
    }

    @Test
    fun withoutTheDownloadedFlagNoBadgeIsDrawn() {
        composeRule.setContent {
            MaterialTheme { MusicCollectionCard(title = "Streamed", onClick = {}) }
        }

        composeRule.onNodeWithContentDescription(downloaded).assertDoesNotExist()
    }

    @Test
    fun theTrailingSlotCarriesTheOverflowAffordance() {
        var menuClicks = 0
        composeRule.setContent {
            MaterialTheme {
                MusicCollectionCard(
                    title = "Album",
                    onClick = {},
                    trailingContent = { MusicCardOverflowButton(onClick = { menuClicks++ }) },
                )
            }
        }

        composeRule.onNodeWithContentDescription(moreOptions).performClick()
        assertEquals(1, menuClicks)
    }

    @Test
    fun theArtistVariantIsJustAShapeAndAnAlignment() {
        composeRule.setContent {
            MaterialTheme {
                MusicCollectionCard(
                    title = "Nina Simone",
                    onClick = {},
                    thumbnailHeight = 100.dp,
                    shape = CircleShape,
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
            }
        }

        composeRule.onNodeWithText("Nina Simone").assertIsDisplayed()
    }

    @Test
    fun theVideoVariantIsJustAnAspectRatio() {
        composeRule.setContent {
            MaterialTheme {
                MusicCollectionCard(
                    title = "Live at Montreux",
                    onClick = {},
                    thumbnailHeight = 124.dp,
                    aspectRatio = 16f / 9f,
                )
            }
        }

        composeRule.onNodeWithText("Live at Montreux").assertIsDisplayed()
    }

    @Test
    fun tappingTheCardFiresOnClick() {
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                MusicCollectionCard(title = "Tap me", onClick = { clicks++ })
            }
        }

        composeRule.onNodeWithText("Tap me").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun aTrailingSlotDoesNotSwallowTheCardClick() {
        var cardClicks = 0
        composeRule.setContent {
            MaterialTheme {
                MusicCollectionCard(
                    title = "Album",
                    onClick = { cardClicks++ },
                    trailingContent = { MusicCardOverflowButton(onClick = {}) },
                )
            }
        }

        composeRule.onNodeWithText("Album").performClick()
        assertEquals(1, cardClicks)
    }
}
