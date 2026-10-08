package io.github.aedev.flow.ui.components.videoplayer.gesture

import com.google.common.truth.Truth.assertThat
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
}
