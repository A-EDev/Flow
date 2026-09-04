/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier

private const val MARQUEE_PASSES = 1
private const val MARQUEE_START_DELAY_MILLIS = 1200

/**
 * The single marquee every scrolling title uses. One pass after a short pause, then it rests:
 * a lane of long titles animates for a few seconds when it appears, not for as long as it is on
 * screen, and a title that scrolls away during the pause never starts at all.
 */
fun Modifier.titleMarquee(): Modifier =
    basicMarquee(
        iterations = MARQUEE_PASSES,
        initialDelayMillis = MARQUEE_START_DELAY_MILLIS,
    )
