package io.github.aedev.flow.ui.screens.settings

import io.github.aedev.flow.data.local.VideoCodec
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.ui.screens.settings.playback.parseSpeedInput
import io.github.aedev.flow.ui.screens.settings.playback.parseSpeedPresets
import io.github.aedev.flow.ui.screens.settings.playback.serializeSpeedPresets
import io.github.aedev.flow.ui.screens.settings.quality.ShortsQualities
import io.github.aedev.flow.ui.screens.settings.quality.fallbackAfterPreferredChange
import io.github.aedev.flow.ui.screens.settings.quality.fallbackCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class QualityAndPlaybackOptionsTest {
    @Test
    fun `shorts quality stops at 1080p`() {
        assertFalse(VideoQuality.Q_1440P in ShortsQualities)
        assertFalse(VideoQuality.Q_2160P in ShortsQualities)
        assertEquals(VideoQuality.AUTO, ShortsQualities.first())
    }

    @Test
    fun `fallback codec never repeats the preferred one`() {
        assertFalse(VideoCodec.AV1 in fallbackCodecs(VideoCodec.AV1))
        assertEquals(VideoCodec.AUTO, fallbackAfterPreferredChange(VideoCodec.VP9, VideoCodec.VP9))
        assertEquals(VideoCodec.H264, fallbackAfterPreferredChange(VideoCodec.VP9, VideoCodec.H264))
    }

    @Test
    fun `speed presets round trip sorted and deduplicated`() {
        val presets = parseSpeedPresets("1.5, 0.75,abc,1.5,12,0.05")
        assertEquals(listOf(0.75f, 1.5f), presets)
        assertEquals("0.75,1.5", serializeSpeedPresets(presets))
    }

    @Test
    fun `typed speeds accept a comma and reject out of range`() {
        assertEquals(1.35f, parseSpeedInput(" 1,35 "))
        assertNull(parseSpeedInput("11"))
        assertNull(parseSpeedInput("fast"))
    }
}
