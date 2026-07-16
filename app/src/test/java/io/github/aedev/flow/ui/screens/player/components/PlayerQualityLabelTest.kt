package io.github.aedev.flow.ui.screens.player.components

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
            )
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
            )
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
            )
        )
    }
}
