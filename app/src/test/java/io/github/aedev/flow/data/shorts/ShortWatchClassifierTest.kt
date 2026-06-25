/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.shorts

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.InteractionType
import org.junit.Test

/** I-5: a Short's watch signal reflects what actually happened, not a fabricated full watch. */
class ShortWatchClassifierTest {

    @Test
    fun `sub-2s flick is a skip with its real fraction`() {
        val s = ShortWatchClassifier.classify(positionMs = 1_500L, durationMs = 30_000L, videoDurationSec = 30)
        assertThat(s.interaction).isEqualTo(InteractionType.SKIPPED)
        assertThat(s.percent).isWithin(1e-4f).of(0.05f)
        assertThat(s.position).isEqualTo(1_500L)
    }

    @Test
    fun `a partial watch reports its real fraction, not 90 percent`() {
        val s = ShortWatchClassifier.classify(positionMs = 9_000L, durationMs = 30_000L, videoDurationSec = 30)
        assertThat(s.interaction).isEqualTo(InteractionType.WATCHED)
        assertThat(s.percent).isWithin(1e-4f).of(0.30f)
    }

    @Test
    fun `position is clamped to duration`() {
        val s = ShortWatchClassifier.classify(positionMs = 99_000L, durationMs = 30_000L, videoDurationSec = 30)
        assertThat(s.position).isEqualTo(30_000L)
        assertThat(s.percent).isEqualTo(1f)
    }

    @Test
    fun `falls back to video duration when player duration is missing`() {
        val s = ShortWatchClassifier.classify(positionMs = 5_000L, durationMs = 0L, videoDurationSec = 20)
        assertThat(s.safeDuration).isEqualTo(20_000L)
        assertThat(s.interaction).isEqualTo(InteractionType.WATCHED)
    }
}
