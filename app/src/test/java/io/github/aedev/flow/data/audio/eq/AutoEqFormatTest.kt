package io.github.aedev.flow.data.audio.eq

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutoEqFormatTest {
    @Test
    fun `reads an AutoEQ file and skips its empty filters`() {
        val text =
            """
            Preamp: -2.0 dB
            Filter 1: ON PK Fc 60 Hz Gain 4.0 dB Q 0.710
            Filter 2: ON LSC Fc 105 Hz Gain 5.5 dB Q 0.70
            Filter 3: ON HSC Fc 10000 Hz Gain -2.1 dB Q 0.70
            Filter 4: OFF PK Fc 0 Hz Gain 0.0 dB Q 0.000
            Filter 5: OFF PK Fc 0 Hz Gain 0.0 dB Q 0.000
            """.trimIndent()

        val result = AutoEqFormat.parse(text)!!

        assertThat(result.curve.preamp).isEqualTo(-2.0)
        assertThat(result.curve.bands)
            .containsExactly(
                EqBand(60.0, 4.0, 0.71, EqFilterType.PEAK),
                EqBand(105.0, 5.5, 0.70, EqFilterType.LOW_SHELF),
                EqBand(10_000.0, -2.1, 0.70, EqFilterType.HIGH_SHELF),
            ).inOrder()
        assertThat(result.skippedFilters).isEqualTo(2)
    }

    @Test
    fun `an ON filter at 0 Hz or Q 0 is refused rather than turned into NaN`() {
        val text = "Filter 1: ON PK Fc 0 Hz Gain 3 dB Q 1\nFilter 2: ON PK Fc 500 Hz Gain 3 dB Q 0\nFilter 3: ON PK Fc 800 Hz Gain 1 dB Q 1"
        val result = AutoEqFormat.parse(text)!!
        assertThat(result.curve.bands).containsExactly(EqBand(800.0, 1.0, 1.0))
        assertThat(result.skippedFilters).isEqualTo(2)
        assertThat(AutoEqFormat.parse("Filter 1: ON PK Fc 0 Hz Gain 3 dB Q 0")).isNull()
    }

    @Test
    fun `accepts comma decimals, lower case, short type names and bandwidth`() {
        val text =
            """
            preamp: -3,5 dB
            filter 1: on pk fc 1000 hz gain 2,5 db q 1,41
            Filter 2: ON LS Fc 80 Hz Gain 3 dB
            Filter 3: ON PK Fc 2000 Hz Gain -1 dB BW Oct 1.0
            Filter 4: ON HP Fc 30 Hz Q 0.5
            Filter 5: ON LSC 12 dB Fc 90 Hz Gain 2 dB
            """.trimIndent()

        val bands = AutoEqFormat.parse(text)!!.curve.bands

        assertThat(bands[0]).isEqualTo(EqBand(1_000.0, 2.5, 1.41, EqFilterType.PEAK))
        assertThat(bands[1]).isEqualTo(EqBand(80.0, 3.0, EqLimits.DEFAULT_SHELF_Q, EqFilterType.LOW_SHELF))
        assertThat(bands[2].q).isWithin(0.001).of(1.414)
        assertThat(bands[3]).isEqualTo(EqBand(30.0, 0.0, 0.5, EqFilterType.HIGH_PASS))
        assertThat(bands[4]).isEqualTo(EqBand(90.0, 2.0, EqLimits.DEFAULT_SHELF_Q, EqFilterType.LOW_SHELF))
    }

    @Test
    fun `a thousands separator in a frequency is not a decimal mark`() {
        val band =
            AutoEqFormat
                .parse("Filter 1: ON PK Fc 1,000 Hz Gain 2 dB Q 1")!!
                .curve.bands
                .single()
        assertThat(band.frequency).isEqualTo(1_000.0)
    }

    @Test
    fun `counts unsupported filters and other commands`() {
        val text =
            """
            # headphone target
            Device: Speakers
            Channel: L
            Filter 1: ON NO Fc 50 Hz Q 30
            Filter 2: ON PK Fc 500 Hz Gain 1 dB Q 1
            """.trimIndent()

        val result = AutoEqFormat.parse(text)!!

        assertThat(result.curve.bands).hasSize(1)
        assertThat(result.skippedFilters).isEqualTo(1)
        assertThat(result.ignoredLines).isEqualTo(2)
    }

    @Test
    fun `keeps the first twenty filters`() {
        val text = (1..25).joinToString("\n") { "Filter $it: ON PK Fc ${it * 100} Hz Gain 1 dB Q 1" }
        val result = AutoEqFormat.parse(text)!!
        assertThat(result.curve.bands).hasSize(EqLimits.MAX_BANDS)
        assertThat(result.truncatedFilters).isEqualTo(5)
    }

    @Test
    fun `clamps values outside the limits`() {
        val band =
            AutoEqFormat
                .parse("Filter 1: ON PK Fc 5 Hz Gain 40 dB Q 50")!!
                .curve.bands
                .single()
        assertThat(band).isEqualTo(EqBand(EqLimits.MIN_FREQUENCY, EqLimits.MAX_GAIN, EqLimits.MAX_Q, EqFilterType.PEAK))
    }

    @Test
    fun `text without filters or preamp is not a preset`() {
        assertThat(AutoEqFormat.parse("")).isNull()
        assertThat(AutoEqFormat.parse("hello\nworld")).isNull()
    }

    @Test
    fun `exported text reads back to the same curve`() {
        val curve =
            EqCurve(
                preamp = -4.5,
                bands =
                    listOf(
                        EqBand(64.0, 3.5, 0.7, EqFilterType.LOW_SHELF),
                        EqBand(910.0, -2.0, 1.41),
                        EqBand(15_000.0, 0.0, 0.707, EqFilterType.LOW_PASS),
                        EqBand(3_000.0, 2.0, 1.0, enabled = false),
                    ),
            )

        val text = AutoEqFormat.format(curve)
        val parsed = AutoEqFormat.parse(text)!!

        assertThat(text).contains("Filter 3: ON LPQ Fc 15000 Hz Q 0.707")
        assertThat(parsed.curve).isEqualTo(curve.copy(bands = curve.bands.take(3)))
        assertThat(parsed.skippedFilters).isEqualTo(1)
    }
}
