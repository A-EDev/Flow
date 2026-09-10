/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [FeedGridLayout.columns] mirrors the count [androidx.compose.foundation.lazy.grid.GridCells.Adaptive]
 * derives, and the shorts shelf and the shimmer trust it. This renders the real grid at each window
 * size and counts the cards the platform actually put on the first row.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class FeedGridRenderTest {
    @get:Rule
    val rule = createComposeRule()

    private fun renderedColumnsAt(
        width: Int,
        height: Int,
    ): Int {
        val layout = feedGridLayoutFor(width.dp)
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowSize(DpSize(width.dp, height.dp)),
            ) {
                LazyVerticalGrid(
                    columns = layout.cells,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = layout.contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(layout.cardSpacing),
                    verticalArrangement = Arrangement.spacedBy(layout.cardSpacing),
                ) {
                    items(items = (0 until 24).toList(), key = { it }) {
                        Box(Modifier.height(80.dp).testTag("cell"))
                    }
                }
            }
        }
        rule.waitForIdle()
        val tops = rule.onAllNodesWithTag("cell").fetchSemanticsNodes().map { node -> node.positionInRoot.y }
        val firstRowTop = tops.min()
        return tops.count { it == firstRowTop }
    }

    private fun assertMirrored(
        width: Int,
        height: Int,
    ) {
        assertThat(renderedColumnsAt(width, height)).isEqualTo(feedGridLayoutFor(width.dp).columns)
    }

    @Test
    fun `a phone renders the single column the layout promises`() {
        assertMirrored(width = 411, height = 891)
    }

    @Test
    fun `a medium window renders the columns the layout promises`() {
        assertMirrored(width = 600, height = 960)
    }

    @Test
    fun `an expanded window renders the columns the layout promises`() {
        assertMirrored(width = 840, height = 1200)
    }

    @Test
    fun `a landscape tablet renders the columns the layout promises`() {
        assertMirrored(width = 1280, height = 800)
    }

    @Test
    fun `a pane beside the navigation rail renders the columns the layout promises`() {
        assertMirrored(width = 744, height = 1200)
    }
}
