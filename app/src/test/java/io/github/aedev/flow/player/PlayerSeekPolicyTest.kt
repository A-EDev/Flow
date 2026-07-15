package io.github.aedev.flow.player

import androidx.media3.common.C
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerSeekPolicyTest {
    @Test
    fun `seek at duration is an end boundary seek`() {
        assertThat(isEndBoundarySeek(requestedPositionMs = 120_000L, durationMs = 120_000L)).isTrue()
    }

    @Test
    fun `seek before duration is not an end boundary seek`() {
        assertThat(isEndBoundarySeek(requestedPositionMs = 119_999L, durationMs = 120_000L)).isFalse()
    }

    @Test
    fun `unknown duration is not an end boundary seek`() {
        assertThat(isEndBoundarySeek(requestedPositionMs = 120_000L, durationMs = C.TIME_UNSET)).isFalse()
    }
}
