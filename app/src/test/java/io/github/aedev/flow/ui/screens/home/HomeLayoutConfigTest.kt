/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.screens.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.ui.components.FeedGridLayout
import org.junit.Test

/** Issues #855 and #925: the home grid must offer a denser layout than one card per row. */
class HomeLayoutConfigTest {
    private fun baseFor(width: Dp) =
        when {
            width < 480.dp -> FeedGridLayout(columns = 1, contentPadding = 0.dp, cardSpacing = 12.dp)
            width < 700.dp -> FeedGridLayout(columns = 1, contentPadding = 12.dp, cardSpacing = 14.dp)
            width < 900.dp -> FeedGridLayout(columns = 2, contentPadding = 16.dp, cardSpacing = 12.dp)
            width < 1200.dp -> FeedGridLayout(columns = 3, contentPadding = 20.dp, cardSpacing = 14.dp)
            else -> FeedGridLayout(columns = 4, contentPadding = 24.dp, cardSpacing = 16.dp)
        }

    private fun resolve(
        width: Dp,
        preference: HomeFeedColumns = HomeFeedColumns.AUTO,
    ) = resolveHomeLayoutConfig(baseFor(width), width, preference)

    @Test
    fun `a phone still gets one card per row on auto`() {
        assertThat(resolve(360.dp).columns).isEqualTo(1)
        assertThat(resolve(430.dp).columns).isEqualTo(1)
        assertThat(resolve(599.dp).columns).isEqualTo(1)
    }

    @Test
    fun `a large screen no longer renders a single full-width poster`() {
        assertThat(resolve(600.dp).columns).isEqualTo(2)
        assertThat(resolve(699.dp).columns).isEqualTo(2)
    }

    @Test
    fun `auto leaves the wider breakpoints exactly as they were`() {
        assertThat(resolve(800.dp).columns).isEqualTo(2)
        assertThat(resolve(1000.dp).columns).isEqualTo(3)
        assertThat(resolve(1400.dp).columns).isEqualTo(4)
    }

    @Test
    fun `a fixed count overrides the screen size in both directions`() {
        assertThat(resolve(360.dp, HomeFeedColumns.THREE).columns).isEqualTo(3)
        assertThat(resolve(1400.dp, HomeFeedColumns.ONE).columns).isEqualTo(1)
        assertThat(resolve(360.dp, HomeFeedColumns.TWO).columns).isEqualTo(2)
    }

    @Test
    fun `the shorts shelf always starts on a fresh row`() {
        HomeFeedColumns.entries.forEach { preference ->
            listOf(360.dp, 600.dp, 800.dp, 1000.dp, 1400.dp).forEach { width ->
                val config = resolve(width, preference)
                assertThat(config.shortsShelfAfterIndex % config.columns).isEqualTo(0)
            }
        }
    }

    @Test
    fun `the shelf position is unchanged for every auto breakpoint`() {
        assertThat(resolve(360.dp).shortsShelfAfterIndex).isEqualTo(1)
        assertThat(resolve(800.dp).shortsShelfAfterIndex).isEqualTo(2)
        assertThat(resolve(1000.dp).shortsShelfAfterIndex).isEqualTo(3)
        assertThat(resolve(1400.dp).shortsShelfAfterIndex).isEqualTo(4)
    }

    @Test
    fun `padding and spacing keep coming from the shared feed layout`() {
        val config = resolve(360.dp, HomeFeedColumns.THREE)

        assertThat(config.contentPadding).isEqualTo(0.dp)
        assertThat(config.cardSpacing).isEqualTo(12.dp)
    }

    @Test
    fun `the shimmer matches the real grid so the layout does not jump when loading ends`() {
        HomeFeedColumns.entries.forEach { preference ->
            listOf(360.dp, 600.dp, 1000.dp).forEach { width ->
                val config = resolve(width, preference)
                assertThat(config.shimmerColumns).isEqualTo(config.columns)
            }
        }
    }
}
