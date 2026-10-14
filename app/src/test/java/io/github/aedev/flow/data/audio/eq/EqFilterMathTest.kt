package io.github.aedev.flow.data.audio.eq

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.audio.eq.EqFilterType.HIGH_PASS
import io.github.aedev.flow.data.audio.eq.EqFilterType.HIGH_SHELF
import io.github.aedev.flow.data.audio.eq.EqFilterType.LOW_PASS
import io.github.aedev.flow.data.audio.eq.EqFilterType.LOW_SHELF
import io.github.aedev.flow.data.audio.eq.EqFilterType.PEAK
import org.junit.Test

class EqFilterMathTest {
    private fun db(
        band: EqBand,
        frequency: Double,
    ) = EqFilterMath.magnitudeDb(listOf(band), frequency)

    @Test
    fun `a peak reaches its gain at its centre and leaves far frequencies alone`() {
        val band = EqBand(1_000.0, 6.0, 1.0, PEAK)
        assertThat(db(band, 1_000.0)).isWithin(0.01).of(6.0)
        assertThat(db(band, 20.0)).isWithin(0.05).of(0.0)
    }

    @Test
    fun `shelves lift their side of the spectrum`() {
        assertThat(db(EqBand(100.0, 6.0, 0.707, LOW_SHELF), 20.0)).isWithin(0.3).of(6.0)
        assertThat(db(EqBand(100.0, 6.0, 0.707, LOW_SHELF), 10_000.0)).isWithin(0.05).of(0.0)
        assertThat(db(EqBand(5_000.0, -4.0, 0.707, HIGH_SHELF), 18_000.0)).isWithin(0.3).of(-4.0)
    }

    @Test
    fun `shelf Q now changes the sound`() {
        val gentle = db(EqBand(100.0, 6.0, 0.707, LOW_SHELF), 150.0)
        val resonant = db(EqBand(100.0, 6.0, 2.0, LOW_SHELF), 150.0)
        assertThat(resonant).isNotWithin(0.2).of(gentle)
    }

    @Test
    fun `low and high pass cut instead of peaking`() {
        assertThat(db(EqBand(100.0, 0.0, 0.707, HIGH_PASS), 100.0)).isWithin(0.05).of(-3.01)
        assertThat(db(EqBand(100.0, 0.0, 0.707, HIGH_PASS), 20.0)).isLessThan(-20.0)
        assertThat(db(EqBand(5_000.0, 0.0, 0.707, LOW_PASS), 15_000.0)).isLessThan(-15.0)
        assertThat(db(EqBand(5_000.0, 0.0, 0.707, LOW_PASS), 100.0)).isWithin(0.05).of(0.0)
    }

    @Test
    fun `flat, disabled and ultrasonic bands are bypassed`() {
        assertThat(EqFilterMath.isBypassed(EqBand(1_000.0, 0.0), 48_000)).isTrue()
        assertThat(EqFilterMath.isBypassed(EqBand(1_000.0, 3.0, enabled = false), 48_000)).isTrue()
        assertThat(EqFilterMath.isBypassed(EqBand(22_000.0, 3.0), 44_100)).isTrue()
        assertThat(EqFilterMath.isBypassed(EqBand(1_000.0, 0.0, type = LOW_PASS), 48_000)).isFalse()
    }

    @Test
    fun `the #810 sample peaks where the old processor clipped it`() {
        val hefty =
            listOf(
                EqBand(60.0, 4.0, 0.71),
                EqBand(230.0, 1.0, 0.71),
                EqBand(910.0, 9.0, 0.71),
                EqBand(3_000.0, 3.0, 0.71),
            )
        assertThat(EqFilterMath.peakDb(hefty)).isWithin(0.05).of(9.71)
        assertThat(EqFilterMath.autoPreampDb(hefty)).isWithin(0.05).of(-9.71)
    }

    @Test
    fun `auto preamp never boosts`() {
        assertThat(EqFilterMath.autoPreampDb(listOf(EqBand(1_000.0, -6.0)))).isEqualTo(0.0)
        assertThat(EqFilterMath.autoPreampDb(emptyList())).isEqualTo(0.0)
    }

    @Test
    fun `the log axis round trips`() {
        listOf(20.0, 63.0, 1_000.0, 12_345.0, 20_000.0).forEach { frequency ->
            assertThat(EqFilterMath.frequencyAt(EqFilterMath.fractionOf(frequency))).isWithin(0.01).of(frequency)
        }
    }
}
