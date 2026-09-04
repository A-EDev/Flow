/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.shared

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Every adaptive surface sizes itself from the window, so a tablet never gets a phone layout
 * blown up to twice its size. These pin the breakpoints and the width maths those surfaces share.
 */
class AdaptiveLayoutTest {
    @Test
    fun windowClassesFollowTheMaterialBreakpoints() {
        assertThat(360.dp.toFlowWindowWidth()).isEqualTo(FlowWindowWidth.Compact)
        assertThat(599.dp.toFlowWindowWidth()).isEqualTo(FlowWindowWidth.Compact)
        assertThat(600.dp.toFlowWindowWidth()).isEqualTo(FlowWindowWidth.Medium)
        assertThat(839.dp.toFlowWindowWidth()).isEqualTo(FlowWindowWidth.Medium)
        assertThat(840.dp.toFlowWindowWidth()).isEqualTo(FlowWindowWidth.Expanded)
        assertThat(1280.dp.toFlowWindowWidth()).isEqualTo(FlowWindowWidth.Expanded)
    }

    @Test
    fun laneItemsFillAPhoneWithTheNextItemPeeking() {
        val width = flowLaneItemWidthFor(windowWidth = 360.dp, maxWidth = 360.dp, peek = 48.dp)
        assertThat(width).isEqualTo(360.dp - 24.dp - 48.dp)
    }

    @Test
    fun laneItemsStopGrowingOnWideWindows() {
        val width = flowLaneItemWidthFor(windowWidth = 1200.dp, maxWidth = 360.dp, peek = 48.dp)
        assertThat(width).isEqualTo(360.dp)
    }

    @Test
    fun laneItemsNeverCollapseInANarrowWindow() {
        val width = flowLaneItemWidthFor(windowWidth = 240.dp, maxWidth = 360.dp, peek = 48.dp)
        assertThat(width).isEqualTo(240.dp)
    }

    @Test
    fun gridCellsShareTheWindowMinusPaddingAndGaps() {
        val width = flowGridCellWidthFor(windowWidth = 400.dp, columns = 2, gap = 8.dp)
        assertThat(width).isEqualTo((400.dp - 24.dp - 8.dp) / 2)
    }

    @Test
    fun heroArtworkIsCappedOnTablets() {
        assertThat(flowHeroArtworkSizeFor(360.dp)).isEqualTo(198.dp)
        assertThat(flowHeroArtworkSizeFor(1200.dp)).isEqualTo(240.dp)
        assertThat(flowHeroArtworkSizeFor(300.dp)).isEqualTo(180.dp)
    }
}
