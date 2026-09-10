/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.layout

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
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

    private fun setChrome(
        width: Int,
        height: Int,
    ) {
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowSize(DpSize(width.dp, height.dp)),
            ) {
                ProvideWindowSizeClass {
                    MaterialTheme {
                        Box(modifier = Modifier.fillMaxSize()) {
                            FlowNavigationChrome(
                                selectedIndex = 0,
                                onItemSelected = {},
                                visible = true,
                                scrolledAway = false,
                                onRailWidthChanged = { reportedRailWidth = it },
                            )
                        }
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
    fun `the rail reports the width the content has to reserve`() {
        setChrome(width = 1280, height = 800)

        assertThat(reportedRailWidth.value).isGreaterThan(0f)
    }
}
