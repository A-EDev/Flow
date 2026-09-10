/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.shared

import android.app.Application
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.window.core.layout.WindowSizeClass
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.utils.LocalWindowSizeClass
import io.github.aedev.flow.ui.utils.ProvideWindowSizeClass
import io.github.aedev.flow.ui.utils.isExpandedWidth
import io.github.aedev.flow.ui.utils.isMediumHeight
import io.github.aedev.flow.ui.utils.isMediumWidth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The window size class is the app's only breakpoint source, so this pins what the root provider
 * makes of a real window and what the shared grid helper reads back out of it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class AdaptiveWindowSizeClassTest {
    @get:Rule
    val rule = createComposeRule()

    private class Classified(
        val sizeClass: WindowSizeClass,
        val gridColumns: Int,
    )

    private fun classify(
        width: Int,
        height: Int,
    ): Classified {
        var sizeClass: WindowSizeClass? = null
        var gridColumns = 0
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowSize(DpSize(width.dp, height.dp)),
            ) {
                ProvideWindowSizeClass {
                    sizeClass = LocalWindowSizeClass.current
                    gridColumns = flowGridColumns(compact = 2, medium = 3, expanded = 4)
                }
            }
        }
        rule.waitForIdle()
        return Classified(requireNotNull(sizeClass), gridColumns)
    }

    @Test
    fun `a phone window is compact width`() {
        val classified = classify(width = 411, height = 891)

        assertThat(classified.sizeClass.isMediumWidth).isFalse()
        assertThat(classified.sizeClass.isExpandedWidth).isFalse()
        assertThat(classified.sizeClass.isMediumHeight).isTrue()
        assertThat(classified.gridColumns).isEqualTo(2)
    }

    @Test
    fun `a small tablet window is medium width`() {
        val classified = classify(width = 600, height = 960)

        assertThat(classified.sizeClass.isMediumWidth).isTrue()
        assertThat(classified.sizeClass.isExpandedWidth).isFalse()
        assertThat(classified.gridColumns).isEqualTo(3)
    }

    @Test
    fun `a tablet window is expanded width`() {
        val classified = classify(width = 840, height = 1200)

        assertThat(classified.sizeClass.isExpandedWidth).isTrue()
        assertThat(classified.gridColumns).isEqualTo(4)
    }

    @Test
    fun `a landscape tablet window is expanded width but only medium height`() {
        val classified = classify(width = 1280, height = 800)

        assertThat(classified.sizeClass.isExpandedWidth).isTrue()
        assertThat(classified.sizeClass.isMediumHeight).isTrue()
        assertThat(
            classified.sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND),
        ).isFalse()
        assertThat(classified.gridColumns).isEqualTo(4)
    }
}
