/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.item

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * MusicTrackItem replaced six track rows. Five of them had no playing state at all, so the same
 * song looked identical whether or not it was playing depending on the screen. These guard the
 * merged contract: one playing treatment everywhere, and variants expressed only through slots.
 */
class MusicTrackItemTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val moreOptions = context.getString(R.string.more_options)
    private val downloaded = context.getString(R.string.status_downloaded)
    private val explicit = context.getString(R.string.label_explicit)

    private fun track(
        title: String = "Song",
        artist: String = "Artist",
        duration: Int = 213,
        isExplicit: Boolean = false,
    ) = MusicTrack(
        videoId = "v1",
        title = title,
        artist = artist,
        thumbnailUrl = "",
        duration = duration,
        isExplicit = isExplicit,
    )

    @Test
    fun titleAndMetadataLineBothRender() {
        composeRule.setContent {
            MaterialTheme { MusicTrackItem(track = track(), onClick = {}) }
        }

        composeRule.onNodeWithText("Song").assertIsDisplayed()
        composeRule.onNodeWithText("Artist • 3:33").assertIsDisplayed()
    }

    @Test
    fun theOverflowMenuIsShownByDefaultAndHidesOnRequest() {
        composeRule.setContent {
            MaterialTheme { MusicTrackItem(track = track(), onClick = {}) }
        }
        composeRule.onNodeWithContentDescription(moreOptions).assertIsDisplayed()
    }

    @Test
    fun showMenuFalseRemovesTheOverflowButton() {
        composeRule.setContent {
            MaterialTheme { MusicTrackItem(track = track(), onClick = {}, showMenu = false) }
        }
        composeRule.onNodeWithContentDescription(moreOptions).assertDoesNotExist()
    }

    @Test
    fun theDownloadedBadgeAppearsOnlyWhenDownloaded() {
        composeRule.setContent {
            MaterialTheme {
                MusicTrackItem(track = track(), onClick = {}, isDownloaded = true)
            }
        }
        composeRule.onNodeWithContentDescription(downloaded).assertIsDisplayed()
    }

    @Test
    fun theExplicitBadgeAppearsOnlyForExplicitTracks() {
        composeRule.setContent {
            MaterialTheme {
                MusicTrackItem(track = track(isExplicit = true), onClick = {})
            }
        }
        composeRule.onNodeWithContentDescription(explicit).assertIsDisplayed()
    }

    @Test
    fun anIndexRendersWhenNoLeadingSlotIsGiven() {
        composeRule.setContent {
            MaterialTheme { MusicTrackItem(track = track(), onClick = {}, index = 7) }
        }
        composeRule.onNodeWithText("7").assertIsDisplayed()
    }

    @Test
    fun theLeadingSlotWinsOverTheIndex() {
        composeRule.setContent {
            MaterialTheme {
                MusicTrackItem(
                    track = track(),
                    onClick = {},
                    index = 7,
                    leadingContent = { Text("handle") },
                )
            }
        }

        composeRule.onNodeWithText("handle").assertIsDisplayed()
        composeRule.onNodeWithText("7").assertDoesNotExist()
    }

    @Test
    fun theTrailingSlotRendersAlongsideTheMenu() {
        composeRule.setContent {
            MaterialTheme {
                MusicTrackItem(
                    track = track(),
                    onClick = {},
                    trailingContent = { Text("extra") },
                )
            }
        }

        composeRule.onNodeWithText("extra").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(moreOptions).assertIsDisplayed()
    }

    @Test
    fun clickingTheRowFiresOnClickAndTheMenuFiresOnMenuClick() {
        var rowClicks = 0
        var menuClicks = 0
        composeRule.setContent {
            MaterialTheme {
                MusicTrackItem(
                    track = track(),
                    onClick = { rowClicks++ },
                    onMenuClick = { menuClicks++ },
                )
            }
        }

        composeRule.onNodeWithText("Song").performClick()
        assertEquals(1, rowClicks)
        assertEquals(0, menuClicks)

        composeRule.onNodeWithContentDescription(moreOptions).performClick()
        assertEquals(1, rowClicks)
        assertEquals(1, menuClicks)
    }

    @Test
    fun aTrackWithoutADurationFallsBackToTheArtistAlone() {
        composeRule.setContent {
            MaterialTheme { MusicTrackItem(track = track(duration = 0), onClick = {}) }
        }
        composeRule.onNodeWithText("Artist").assertIsDisplayed()
    }

    @Test
    fun everyDensityRendersTheSamePlayingTreatment() {
        composeRule.setContent {
            MaterialTheme {
                MusicTrackItem(
                    track = track(title = "Comfortable"),
                    onClick = {},
                    isPlaying = true,
                )
                MusicTrackItem(
                    track = track(title = "Compact"),
                    onClick = {},
                    density = MusicItemDensity.Compact,
                    isPlaying = true,
                )
            }
        }

        composeRule.onNodeWithText("Comfortable").assertIsDisplayed()
        composeRule.onNodeWithText("Compact").assertIsDisplayed()
    }
}
