package io.github.aedev.flow.ui.components.videoplayer.gesture

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.DoubleTapSeekZone
import org.junit.Test

class DoubleTapSeekZonesTest {
    @Test
    fun `thirds split the width into back, centre and forward`() {
        assertThat(tapZoneOf(x = 100f, width = 900f, sideFraction = 1f / 3f)).isEqualTo(TapZone.BACK)
        assertThat(tapZoneOf(x = 450f, width = 900f, sideFraction = 1f / 3f)).isEqualTo(TapZone.CENTER)
        assertThat(tapZoneOf(x = 800f, width = 900f, sideFraction = 1f / 3f)).isEqualTo(TapZone.FORWARD)
    }

    @Test
    fun `a quarter on each side leaves half the width to the centre`() {
        assertThat(tapZoneOf(x = 249f, width = 1000f, sideFraction = 0.25f)).isEqualTo(TapZone.BACK)
        assertThat(tapZoneOf(x = 260f, width = 1000f, sideFraction = 0.25f)).isEqualTo(TapZone.CENTER)
        assertThat(tapZoneOf(x = 740f, width = 1000f, sideFraction = 0.25f)).isEqualTo(TapZone.CENTER)
        assertThat(tapZoneOf(x = 751f, width = 1000f, sideFraction = 0.25f)).isEqualTo(TapZone.FORWARD)
    }

    @Test
    fun `no side fraction makes the whole width the centre`() {
        assertThat(tapZoneOf(x = 0f, width = 1000f, sideFraction = 0f)).isEqualTo(TapZone.CENTER)
        assertThat(tapZoneOf(x = 999f, width = 1000f, sideFraction = 0f)).isEqualTo(TapZone.CENTER)
    }

    @Test
    fun `an unmeasured player treats every tap as centre`() {
        assertThat(tapZoneOf(x = 10f, width = 0f, sideFraction = 1f / 3f)).isEqualTo(TapZone.CENTER)
    }

    @Test
    fun `the strips the back gesture owns are edge, never a seek side`() {
        assertThat(tapZoneOf(x = 20f, width = 1000f, sideFraction = 1f / 3f, edgePx = 66f)).isEqualTo(TapZone.EDGE)
        assertThat(tapZoneOf(x = 980f, width = 1000f, sideFraction = 1f / 3f, edgePx = 66f)).isEqualTo(TapZone.EDGE)
        assertThat(tapZoneOf(x = 70f, width = 1000f, sideFraction = 1f / 3f, edgePx = 66f)).isEqualTo(TapZone.BACK)
        assertThat(tapZoneOf(x = 930f, width = 1000f, sideFraction = 1f / 3f, edgePx = 66f)).isEqualTo(TapZone.FORWARD)
    }

    @Test
    fun `edges hold even with double-tap seek turned off`() {
        assertThat(tapZoneOf(x = 10f, width = 1000f, sideFraction = 0f, edgePx = 66f)).isEqualTo(TapZone.EDGE)
        assertThat(tapZoneOf(x = 500f, width = 1000f, sideFraction = 0f, edgePx = 66f)).isEqualTo(TapZone.CENTER)
    }

    @Test
    fun `edge to edge zones take taps right up to the edges`() {
        val zone = DoubleTapSeekZone.EDGE_TO_EDGE
        val edgePx = tapEdgePx(zone.reachesEdges, backGestureEdgePx = 66f)
        assertThat(tapZoneOf(x = 5f, width = 1000f, sideFraction = zone.sideFraction, edgePx = edgePx)).isEqualTo(TapZone.BACK)
        assertThat(tapZoneOf(x = 995f, width = 1000f, sideFraction = zone.sideFraction, edgePx = edgePx)).isEqualTo(TapZone.FORWARD)
        assertThat(tapZoneOf(x = 500f, width = 1000f, sideFraction = zone.sideFraction, edgePx = edgePx)).isEqualTo(TapZone.CENTER)
    }

    @Test
    fun `normal and narrow keep the back gesture strip`() {
        listOf(DoubleTapSeekZone.NORMAL, DoubleTapSeekZone.NARROW).forEach { zone ->
            val edgePx = tapEdgePx(zone.reachesEdges, backGestureEdgePx = 66f)
            assertThat(tapZoneOf(x = 5f, width = 1000f, sideFraction = zone.sideFraction, edgePx = edgePx)).isEqualTo(TapZone.EDGE)
        }
    }

    @Test
    fun `edge to edge is as wide as normal`() {
        assertThat(DoubleTapSeekZone.EDGE_TO_EDGE.sideFraction).isEqualTo(DoubleTapSeekZone.NORMAL.sideFraction)
    }
}
