package io.github.aedev.flow.ui.components.videoplayer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerImmersivePolicyTest {
    @Test
    fun `expanding in a compact landscape window is immersive without fullscreen`() {
        assertThat(
            isImmersivePlayer(isExpanded = true, isFullscreen = false, isLandscape = true, isLargeWindow = false),
        ).isTrue()
    }

    @Test
    fun `a large landscape window keeps its page until fullscreen is asked for`() {
        assertThat(
            isImmersivePlayer(isExpanded = true, isFullscreen = false, isLandscape = true, isLargeWindow = true),
        ).isFalse()
        assertThat(
            isImmersivePlayer(isExpanded = true, isFullscreen = true, isLandscape = true, isLargeWindow = true),
        ).isTrue()
    }

    @Test
    fun `portrait is immersive only in fullscreen`() {
        assertThat(
            isImmersivePlayer(isExpanded = true, isFullscreen = false, isLandscape = false, isLargeWindow = false),
        ).isFalse()
        assertThat(
            isImmersivePlayer(isExpanded = true, isFullscreen = true, isLandscape = false, isLargeWindow = false),
        ).isTrue()
    }

    @Test
    fun `the mini player is never immersive`() {
        assertThat(
            isImmersivePlayer(isExpanded = false, isFullscreen = true, isLandscape = true, isLargeWindow = false),
        ).isFalse()
        assertThat(
            isImmersivePlayer(isExpanded = false, isFullscreen = false, isLandscape = true, isLargeWindow = false),
        ).isFalse()
    }

    @Test
    fun `swipe up reaches fullscreen from a landscape tablet but not a landscape phone`() {
        assertThat(canSwipeUpToFullscreen(isFullscreen = false, isLandscape = true, isLargeWindow = true)).isTrue()
        assertThat(canSwipeUpToFullscreen(isFullscreen = false, isLandscape = true, isLargeWindow = false)).isFalse()
    }

    @Test
    fun `swipe up reaches fullscreen from portrait on any window`() {
        assertThat(canSwipeUpToFullscreen(isFullscreen = false, isLandscape = false, isLargeWindow = false)).isTrue()
        assertThat(canSwipeUpToFullscreen(isFullscreen = false, isLandscape = false, isLargeWindow = true)).isTrue()
    }

    @Test
    fun `swipe up does nothing once fullscreen`() {
        assertThat(canSwipeUpToFullscreen(isFullscreen = true, isLandscape = true, isLargeWindow = true)).isFalse()
        assertThat(canSwipeUpToFullscreen(isFullscreen = true, isLandscape = false, isLargeWindow = false)).isFalse()
    }
}
