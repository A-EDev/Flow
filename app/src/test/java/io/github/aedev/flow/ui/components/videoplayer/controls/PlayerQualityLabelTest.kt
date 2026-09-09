package io.github.aedev.flow.ui.components.videoplayer.controls

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerQualityLabelTest {
    @Test
    fun `loading state does not expose zero as a resolution`() {
        assertEquals(
            "Auto",
            resolvePlayerQualityLabel(
                currentQuality = 0,
                effectiveQuality = 0,
                autoLabel = "Auto",
                autoWithHeightLabel = "Auto (0p)",
            ),
        )
    }

    @Test
    fun `automatic quality includes a known effective resolution`() {
        assertEquals(
            "Auto (1080p)",
            resolvePlayerQualityLabel(
                currentQuality = 0,
                effectiveQuality = 1080,
                autoLabel = "Auto",
                autoWithHeightLabel = "Auto (1080p)",
            ),
        )
    }

    @Test
    fun `manual quality uses the selected resolution`() {
        assertEquals(
            "720",
            resolvePlayerQualityLabel(
                currentQuality = 720,
                effectiveQuality = 1080,
                autoLabel = "Auto",
                autoWithHeightLabel = "Auto (1080p)",
            ),
        )
    }

    @Test
    fun `high resolutions collapse to their marketing name`() {
        assertEquals("4K", compactPlayerQualityLabel("2160p"))
        assertEquals("QHD", compactPlayerQualityLabel("1440p"))
        assertEquals("FHD", compactPlayerQualityLabel("1080p"))
        assertEquals("HD", compactPlayerQualityLabel("720p"))
        assertEquals("SD", compactPlayerQualityLabel("480p"))
    }

    @Test
    fun `low resolutions keep their pixel height`() {
        assertEquals("360p", compactPlayerQualityLabel("360p"))
        assertEquals("240p", compactPlayerQualityLabel("240p"))
        assertEquals("144p", compactPlayerQualityLabel("144p"))
    }

    @Test
    fun `an off-ladder resolution is rendered as a pixel height`() {
        assertEquals("1250p", compactPlayerQualityLabel("1250p"))
    }

    @Test
    fun `the first number in the label is the one that counts`() {
        assertEquals("FHD", compactPlayerQualityLabel("1080p60"))
    }

    @Test
    fun `a label without a resolution is passed through unchanged`() {
        assertEquals("Auto", compactPlayerQualityLabel("Auto"))
    }
}
