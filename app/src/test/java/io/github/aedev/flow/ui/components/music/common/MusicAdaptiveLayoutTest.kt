/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.common

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Every music section sizes itself from the window, so a tablet never gets a phone layout blown
 * up to twice its size. These pin the breakpoints and the width maths the sections share.
 */
class MusicAdaptiveLayoutTest {
    @Test
    fun windowClassesFollowTheMaterialBreakpoints() {
        assertThat(360.dp.toMusicWindowWidth()).isEqualTo(MusicWindowWidth.Compact)
        assertThat(599.dp.toMusicWindowWidth()).isEqualTo(MusicWindowWidth.Compact)
        assertThat(600.dp.toMusicWindowWidth()).isEqualTo(MusicWindowWidth.Medium)
        assertThat(839.dp.toMusicWindowWidth()).isEqualTo(MusicWindowWidth.Medium)
        assertThat(840.dp.toMusicWindowWidth()).isEqualTo(MusicWindowWidth.Expanded)
        assertThat(1280.dp.toMusicWindowWidth()).isEqualTo(MusicWindowWidth.Expanded)
    }

    @Test
    fun laneItemsFillAPhoneWithTheNextItemPeeking() {
        val width = musicLaneItemWidthFor(windowWidth = 360.dp, maxWidth = 360.dp, peek = 48.dp)
        assertThat(width).isEqualTo(360.dp - 24.dp - 48.dp)
    }

    @Test
    fun laneItemsStopGrowingOnWideWindows() {
        val width = musicLaneItemWidthFor(windowWidth = 1200.dp, maxWidth = 360.dp, peek = 48.dp)
        assertThat(width).isEqualTo(360.dp)
    }

    @Test
    fun laneItemsNeverCollapseInANarrowWindow() {
        val width = musicLaneItemWidthFor(windowWidth = 240.dp, maxWidth = 360.dp, peek = 48.dp)
        assertThat(width).isEqualTo(240.dp)
    }

    @Test
    fun gridCellsShareTheWindowMinusPaddingAndGaps() {
        val width = musicGridCellWidthFor(windowWidth = 400.dp, columns = 2, gap = 8.dp)
        assertThat(width).isEqualTo((400.dp - 24.dp - 8.dp) / 2)
    }

    @Test
    fun heroArtworkIsCappedOnTablets() {
        assertThat(musicHeroArtworkSizeFor(360.dp)).isEqualTo(198.dp)
        assertThat(musicHeroArtworkSizeFor(1200.dp)).isEqualTo(240.dp)
        assertThat(musicHeroArtworkSizeFor(300.dp)).isEqualTo(180.dp)
    }

    @Test
    fun moodTonesCycleThroughTheThreeContainers() {
        assertThat(MusicMoodTone.forIndex(0)).isEqualTo(MusicMoodTone.Primary)
        assertThat(MusicMoodTone.forIndex(1)).isEqualTo(MusicMoodTone.Secondary)
        assertThat(MusicMoodTone.forIndex(2)).isEqualTo(MusicMoodTone.Tertiary)
        assertThat(MusicMoodTone.forIndex(3)).isEqualTo(MusicMoodTone.Primary)
    }
}
