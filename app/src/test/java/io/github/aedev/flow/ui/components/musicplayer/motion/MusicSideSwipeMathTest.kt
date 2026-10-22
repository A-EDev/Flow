package io.github.aedev.flow.ui.components.musicplayer.motion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicSideSwipeMathTest {
    private fun zone(
        x: Float,
        y: Float = 1000f,
        width: Float = 1080f,
        topBandPx: Float = 250f,
        edgePx: Float = 60f,
    ) = musicSwipeZoneAt(x, y, width, topBandPx, leftEdgePx = edgePx, rightEdgePx = edgePx)

    @Test
    fun `the player splits into thirds`() {
        assertThat(zone(x = 200f)).isEqualTo(MusicSwipeZone.SPEED)
        assertThat(zone(x = 540f)).isEqualTo(MusicSwipeZone.CENTER)
        assertThat(zone(x = 900f)).isEqualTo(MusicSwipeZone.VOLUME)
    }

    @Test
    fun `the thirds match the video fullscreen split`() {
        assertThat(zone(x = 355f)).isEqualTo(MusicSwipeZone.SPEED)
        assertThat(zone(x = 360f)).isEqualTo(MusicSwipeZone.CENTER)
        assertThat(zone(x = 720f)).isEqualTo(MusicSwipeZone.CENTER)
        assertThat(zone(x = 725f)).isEqualTo(MusicSwipeZone.VOLUME)
    }

    @Test
    fun `the top bar band always collapses`() {
        assertThat(zone(x = 200f, y = 249f)).isEqualTo(MusicSwipeZone.CENTER)
        assertThat(zone(x = 900f, y = 10f)).isEqualTo(MusicSwipeZone.CENTER)
        assertThat(zone(x = 200f, y = 250f)).isEqualTo(MusicSwipeZone.SPEED)
    }

    @Test
    fun `system gesture edges are left to the back swipe`() {
        assertThat(zone(x = 59f)).isEqualTo(MusicSwipeZone.CENTER)
        assertThat(zone(x = 1021f)).isEqualTo(MusicSwipeZone.CENTER)
        assertThat(zone(x = 61f)).isEqualTo(MusicSwipeZone.SPEED)
        assertThat(zone(x = 1019f)).isEqualTo(MusicSwipeZone.VOLUME)
    }

    @Test
    fun `an unmeasured player has no side columns`() {
        assertThat(zone(x = 0f, width = 0f, edgePx = 0f)).isEqualTo(MusicSwipeZone.CENTER)
    }

    @Test
    fun `volume follows the travel and stays in range`() {
        assertThat(MusicVolumeSwipe.levelFor(startLevel = 0.5f, travelUpPx = 0f, heightPx = 1500f)).isEqualTo(0.5f)
        assertThat(MusicVolumeSwipe.levelFor(startLevel = 0.5f, travelUpPx = 100f, heightPx = 1500f)).isWithin(1e-6f).of(0.6f)
        assertThat(MusicVolumeSwipe.levelFor(startLevel = 0.5f, travelUpPx = -100f, heightPx = 1500f)).isWithin(1e-6f).of(0.4f)
        assertThat(MusicVolumeSwipe.levelFor(startLevel = 0.9f, travelUpPx = 1500f, heightPx = 1500f)).isEqualTo(1f)
        assertThat(MusicVolumeSwipe.levelFor(startLevel = 0.1f, travelUpPx = -1500f, heightPx = 1500f)).isEqualTo(0f)
        assertThat(MusicVolumeSwipe.levelFor(startLevel = 0.3f, travelUpPx = 100f, heightPx = 0f)).isEqualTo(0.3f)
    }

    @Test
    fun `a step moves only after half a step of travel either way`() {
        val start = 7f / 15f
        assertThat(MusicVolumeSwipe.stepFor(start, maxStep = 15)).isEqualTo(7)
        assertThat(MusicVolumeSwipe.stepFor(start - 0.01f, maxStep = 15)).isEqualTo(7)
        assertThat(MusicVolumeSwipe.stepFor(start + 0.01f, maxStep = 15)).isEqualTo(7)
        assertThat(MusicVolumeSwipe.stepFor(start - 0.04f, maxStep = 15)).isEqualTo(6)
        assertThat(MusicVolumeSwipe.stepFor(start + 0.04f, maxStep = 15)).isEqualTo(8)
    }

    @Test
    fun `steps stay inside the stream range`() {
        assertThat(MusicVolumeSwipe.stepFor(0f, maxStep = 15)).isEqualTo(0)
        assertThat(MusicVolumeSwipe.stepFor(1f, maxStep = 15)).isEqualTo(15)
        assertThat(MusicVolumeSwipe.stepFor(0.5f, maxStep = 0)).isEqualTo(0)
    }
}
