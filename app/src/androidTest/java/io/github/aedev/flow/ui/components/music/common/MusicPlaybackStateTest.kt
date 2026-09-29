/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.music.item.MusicCollectionCard
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import org.junit.Rule
import org.junit.Test

/**
 * The now-playing indicator used to be a flag each call site computed for itself, so most rows
 * never showed it. Both item components now derive it from one composition-local id, and these
 * pin that: the same provided id lights up a row and a card without either being told.
 */
class MusicPlaybackStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val playing =
        MusicTrack(
            videoId = "playing-id",
            title = "Now playing",
            artist = "Artist",
            thumbnailUrl = "",
            duration = 100,
        )

    private val other = playing.copy(videoId = "other-id", title = "Not playing")

    @Test
    fun aTrackRowTakesItsPlayingStateFromTheProvidedId() {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPlayingVideoId provides "playing-id") {
                    Text(if (isTrackPlaying(playing.videoId)) "row-playing" else "row-idle")
                    MusicTrackItem(track = playing, onClick = {})
                }
            }
        }

        composeRule.onNodeWithText("row-playing").assertExists()
    }

    @Test
    fun aDifferentTrackIsNotMarkedPlaying() {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPlayingVideoId provides "playing-id") {
                    Text(if (isTrackPlaying(other.videoId)) "row-playing" else "row-idle")
                }
            }
        }

        composeRule.onNodeWithText("row-idle").assertExists()
    }

    @Test
    fun aCollectionCardTakesTheSamePlayingStateFromTheSameId() {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPlayingVideoId provides "playing-id") {
                    Text(if (isTrackPlaying("playing-id")) "card-playing" else "card-idle")
                    MusicCollectionCard(title = "Card", mediaId = "playing-id", onClick = {})
                }
            }
        }

        composeRule.onNodeWithText("card-playing").assertExists()
    }

    @Test
    fun aCardWithoutAMediaIdIsNeverMarkedPlaying() {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPlayingVideoId provides "playing-id") {
                    Text(if (isTrackPlaying(null)) "card-playing" else "card-idle")
                }
            }
        }

        composeRule.onNodeWithText("card-idle").assertExists()
    }

    @Test
    fun withNothingPlayingNoItemIsMarkedPlaying() {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPlayingVideoId provides null) {
                    Text(if (isTrackPlaying(playing.videoId)) "row-playing" else "row-idle")
                }
            }
        }

        composeRule.onNodeWithText("row-idle").assertExists()
    }

    @Test
    fun anEmptyVideoIdNeverMatchesAnEmptyPlayingId() {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPlayingVideoId provides "") {
                    Text(if (isTrackPlaying("")) "row-playing" else "row-idle")
                }
            }
        }

        composeRule.onNodeWithText("row-idle").assertExists()
    }
}
