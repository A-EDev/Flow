package io.github.aedev.flow.ui.components.videoplayer.controls

import io.github.aedev.flow.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The ladder is asserted on the badge it picks, not on the rendered text: the marketing names are
 * localisable resources now, so the pure function returns which one to show.
 */
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
        assertEquals(PlayerQualityBadge.Named(R.string.filter_4k), compactPlayerQualityBadge("2160p"))
        assertEquals(PlayerQualityBadge.Named(R.string.quality_badge_qhd), compactPlayerQualityBadge("1440p"))
        assertEquals(PlayerQualityBadge.Named(R.string.quality_badge_fhd), compactPlayerQualityBadge("1080p"))
        assertEquals(PlayerQualityBadge.Named(R.string.filter_hd), compactPlayerQualityBadge("720p"))
        assertEquals(PlayerQualityBadge.Named(R.string.quality_badge_sd), compactPlayerQualityBadge("480p"))
    }

    @Test
    fun `low resolutions keep their pixel height`() {
        assertEquals(PlayerQualityBadge.Height(360), compactPlayerQualityBadge("360p"))
        assertEquals(PlayerQualityBadge.Height(240), compactPlayerQualityBadge("240p"))
        assertEquals(PlayerQualityBadge.Height(144), compactPlayerQualityBadge("144p"))
    }

    @Test
    fun `an off-ladder resolution is rendered as a pixel height`() {
        assertEquals(PlayerQualityBadge.Height(1250), compactPlayerQualityBadge("1250p"))
    }

    @Test
    fun `the first number in the label is the one that counts`() {
        assertEquals(PlayerQualityBadge.Named(R.string.quality_badge_fhd), compactPlayerQualityBadge("1080p60"))
    }

    @Test
    fun `a label without a resolution is passed through unchanged`() {
        assertEquals(PlayerQualityBadge.Verbatim("Auto"), compactPlayerQualityBadge("Auto"))
    }
}
