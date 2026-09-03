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
}
