/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The music surface used to carry three private copies of this formatter, none of which handled
 * durations past an hour: a 74-minute mix rendered as "74:12". They now all route here, so the
 * hour rollover and the zero-padding are the contract every music row depends on.
 */
class FormatDurationTest {
    @Test
    fun `sub minute durations keep a zero minute field`() {
        assertThat(formatDuration(0)).isEqualTo("0:00")
        assertThat(formatDuration(7)).isEqualTo("0:07")
        assertThat(formatDuration(59)).isEqualTo("0:59")
    }

    @Test
    fun `minutes are not zero padded but seconds always are`() {
        assertThat(formatDuration(60)).isEqualTo("1:00")
        assertThat(formatDuration(213)).isEqualTo("3:33")
        assertThat(formatDuration(599)).isEqualTo("9:59")
        assertThat(formatDuration(600)).isEqualTo("10:00")
    }

    @Test
    fun `durations past an hour roll into an hours field`() {
        assertThat(formatDuration(3599)).isEqualTo("59:59")
        assertThat(formatDuration(3600)).isEqualTo("1:00:00")
        assertThat(formatDuration(3661)).isEqualTo("1:01:01")
        assertThat(formatDuration(4452)).isEqualTo("1:14:12")
        assertThat(formatDuration(36000)).isEqualTo("10:00:00")
    }

    @Test
    fun `the millisecond overload truncates to whole seconds`() {
        assertThat(formatDurationMillis(0L)).isEqualTo("0:00")
        assertThat(formatDurationMillis(59_000L)).isEqualTo("0:59")
        assertThat(formatDurationMillis(59_999L)).isEqualTo("0:59")
        assertThat(formatDurationMillis(61_000L)).isEqualTo("1:01")
        assertThat(formatDurationMillis(3_661_000L)).isEqualTo("1:01:01")
        assertThat(formatDurationMillis(36_000_000L)).isEqualTo("10:00:00")
    }

    @Test
    fun `minutes are never zero padded unless the caller asks`() {
        assertThat(formatDurationMillis(61_000L)).isEqualTo("1:01")
        assertThat(formatDuration(61)).isEqualTo("1:01")
    }

    @Test
    fun `padded minutes are what the on-video controls render`() {
        assertThat(formatDurationMillis(0L, padMinutes = true)).isEqualTo("00:00")
        assertThat(formatDurationMillis(59_000L, padMinutes = true)).isEqualTo("00:59")
        assertThat(formatDurationMillis(61_000L, padMinutes = true)).isEqualTo("01:01")
        assertThat(formatDuration(61, padMinutes = true)).isEqualTo("01:01")
    }

    @Test
    fun `the hours field is never padded, with or without padded minutes`() {
        assertThat(formatDurationMillis(3_661_000L, padMinutes = true)).isEqualTo("1:01:01")
        assertThat(formatDurationMillis(36_000_000L, padMinutes = true)).isEqualTo("10:00:00")
        assertThat(formatDurationMillis(3_600_000L)).isEqualTo("1:00:00")
    }

    @Test
    fun `negative durations leak a minus sign into the fields`() {
        // Pins current behaviour: nothing clamps a negative input, so the sign lands inside the
        // zero-padded seconds field.
        assertThat(formatDuration(-61)).isEqualTo("-1:-1")
        assertThat(formatDurationMillis(-1_000L)).isEqualTo("0:-1")
        assertThat(formatDurationMillis(-1_000L, padMinutes = true)).isEqualTo("00:-1")
        assertThat(formatDurationMillis(-999L)).isEqualTo("0:00")
        assertThat(formatDurationMillis(-999L, padMinutes = true)).isEqualTo("00:00")
    }
}

/**
 * The playback-speed and speed-boost labels. Whole values lose their decimal point, everything else
 * is rounded to two places and trimmed, and the value is clamped into `0.1..maxValue`.
 */
class FormatMultiplierLabelTest {
    @Test
    fun `whole multipliers drop the decimal point`() {
        assertThat(formatMultiplierLabel(1.0f)).isEqualTo("1x")
        assertThat(formatMultiplierLabel(2.0f)).isEqualTo("2x")
        assertThat(formatMultiplierLabel(1.005f)).isEqualTo("1x")
    }

    @Test
    fun `fractional multipliers keep the digits that matter`() {
        assertThat(formatMultiplierLabel(0.25f)).isEqualTo("0.25x")
        assertThat(formatMultiplierLabel(0.75f)).isEqualTo("0.75x")
        assertThat(formatMultiplierLabel(1.25f)).isEqualTo("1.25x")
        assertThat(formatMultiplierLabel(1.5f)).isEqualTo("1.5x")
        assertThat(formatMultiplierLabel(1.1f)).isEqualTo("1.1x")
        assertThat(formatMultiplierLabel(1.333f)).isEqualTo("1.33x")
        assertThat(formatMultiplierLabel(1.999f)).isEqualTo("2x")
    }

    @Test
    fun `the value is clamped into the allowed range`() {
        assertThat(formatMultiplierLabel(0f)).isEqualTo("0.1x")
        assertThat(formatMultiplierLabel(0.05f)).isEqualTo("0.1x")
        assertThat(formatMultiplierLabel(12f)).isEqualTo("10x")
        assertThat(formatMultiplierLabel(5f, maxValue = 4.0f)).isEqualTo("4x")
    }

    @Test
    fun `the default ceiling is the settings ceiling, not the boost ceiling`() {
        assertThat(formatMultiplierLabel(5f)).isEqualTo("5x")
    }
}
