/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.layout.navigation

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.utils.ProvideWindowSizeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Owner decision D-4: the bottom bar is the navigation surface up to the expanded breakpoint, and
 * the wide navigation rail takes over above it. Nothing may render both.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class FlowNavigationChromeTest {
    @get:Rule
    val rule = createComposeRule()

    private var reportedRailWidth: Dp = 0.dp
    private var reportedBarHeight: Dp = 0.dp

    private fun setChrome(
        width: Int,
        height: Int,
        railVisible: Boolean = true,
    ) {
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowSize(DpSize(width.dp, height.dp)),
            ) {
                ProvideWindowSizeClass {
                    MaterialTheme {
                        FlowNavigationChrome(
                            tabs = FlowTab.entries.take(5),
                            selectedTab = FlowTab.Home,
                            onTabSelected = {},
                            barVisible = true,
                            railVisible = railVisible,
                            onBarHeightChanged = { reportedBarHeight = it },
                            onRailWidthChanged = { reportedRailWidth = it },
                        ) {}
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a phone window navigates from the bottom bar`() {
        setChrome(width = 411, height = 891)

        rule.onNodeWithTag(FLOW_NAV_BAR_TAG).assertIsDisplayed()
        rule.onNodeWithTag(FLOW_NAV_RAIL_TAG).assertDoesNotExist()
    }

    @Test
    fun `an expanded window navigates from the rail`() {
        setChrome(width = 1280, height = 800)

        rule.onNodeWithTag(FLOW_NAV_RAIL_TAG).assertIsDisplayed()
        rule.onNodeWithTag(FLOW_NAV_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun `the rail reports its width for overlays drawn outside the layout`() {
        setChrome(width = 1280, height = 800)

        assertThat(reportedRailWidth.value).isGreaterThan(0f)
    }

    @Test
    fun `the bar reports at least the material container height`() {
        setChrome(width = 411, height = 891)

        assertThat(reportedBarHeight.value).isAtLeast(FlowNavigationDefaults.BarHeight.value)
    }

    @Test
    fun `a hidden rail leaves no navigation surface`() {
        setChrome(width = 1280, height = 800, railVisible = false)

        rule.onNodeWithTag(FLOW_NAV_RAIL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(FLOW_NAV_BAR_TAG).assertDoesNotExist()
    }
}
