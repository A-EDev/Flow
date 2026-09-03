package io.github.aedev.flow.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import io.github.aedev.flow.data.model.FilterType
import io.github.aedev.flow.data.model.ParametricEQ
import io.github.aedev.flow.data.model.ParametricEQBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CustomEqualizerAudioProcessorTest {

    private lateinit var processor: CustomEqualizerAudioProcessor

    @Before
    fun setUp() {
        processor = CustomEqualizerAudioProcessor()
    }

    @Test
    fun testConfigureAndEnable() {
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val configured = processor.configure(format)

        assertEquals(format, configured)
        assertTrue(processor.isActive)
    }

    @Test
    fun testFlushAndReset() {
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)

        val eq = ParametricEQ(
            preamp = 0.0,
            bands = listOf(
                ParametricEQBand(frequency = 1000.0, gain = 3.0, q = 1.0, filterType = FilterType.PK, enabled = true)
            )
        )
        processor.applyProfile(eq)

        processor.flush()
        assertTrue(processor.isActive)

        processor.reset()
        assertFalse(processor.isActive)
    }
}
