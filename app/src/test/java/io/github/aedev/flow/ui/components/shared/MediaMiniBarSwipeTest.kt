package io.github.aedev.flow.ui.components.shared

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.MiniBarSwipeAction
import org.junit.Test

class MediaMiniBarSwipeTest {
    @Test
    fun `a swipe past a third of the width commits`() {
        assertThat(isMiniBarSwipeCommitted(travelX = -140f, velocityX = 0f, widthPx = 400f)).isTrue()
        assertThat(isMiniBarSwipeCommitted(travelX = 120f, velocityX = 0f, widthPx = 400f)).isFalse()
    }

    @Test
    fun `a short fling commits only towards the side it travelled`() {
        assertThat(isMiniBarSwipeCommitted(travelX = 60f, velocityX = 1500f, widthPx = 400f)).isTrue()
        assertThat(isMiniBarSwipeCommitted(travelX = 60f, velocityX = -1500f, widthPx = 400f)).isFalse()
    }

    @Test
    fun `an unknown or missing stored action reads as close`() {
        assertThat(MiniBarSwipeAction.fromString(null)).isEqualTo(MiniBarSwipeAction.CLOSE)
        assertThat(MiniBarSwipeAction.fromString("SOMETHING_OLD")).isEqualTo(MiniBarSwipeAction.CLOSE)
        assertThat(MiniBarSwipeAction.fromString("NEXT")).isEqualTo(MiniBarSwipeAction.NEXT)
    }

    @Test
    fun `the music bar acts only on close, next and previous`() {
        assertThat(MiniBarSwipeAction.entries.filter { it.appliesToMusic })
            .containsExactly(MiniBarSwipeAction.CLOSE, MiniBarSwipeAction.NEXT, MiniBarSwipeAction.PREVIOUS)
    }
}
