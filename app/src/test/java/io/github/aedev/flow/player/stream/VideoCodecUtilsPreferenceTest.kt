package io.github.aedev.flow.player.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The track selector's codec order is the only place a codec preference can reach an adaptive
 * source, which is what makes a livestream honour the setting at all (#727).
 */
class VideoCodecUtilsPreferenceTest {
    private val defaultOrder = VideoCodecUtils.preferredVideoMimeTypes()

    @Test
    fun `no preference keeps the decode-cost order`() {
        assertArrayEquals(defaultOrder, VideoCodecUtils.preferredVideoMimeTypes("auto"))
        assertArrayEquals(defaultOrder, VideoCodecUtils.preferredVideoMimeTypes(null))
        assertArrayEquals(defaultOrder, VideoCodecUtils.preferredVideoMimeTypes(""))
    }

    @Test
    fun `the chosen codec leads`() {
        assertEquals("video/av01", VideoCodecUtils.preferredVideoMimeTypes("av1").first())
        assertEquals("video/x-vnd.on2.vp9", VideoCodecUtils.preferredVideoMimeTypes("vp9").first())
        assertEquals("video/avc", VideoCodecUtils.preferredVideoMimeTypes("h264").first())
    }

    // Reordering, not filtering: a livestream that has no AV1 variant must still be playable.
    @Test
    fun `every codec stays available, exactly once`() {
        val reordered = VideoCodecUtils.preferredVideoMimeTypes("av1")

        assertEquals(defaultOrder.size, reordered.size)
        assertEquals(defaultOrder.toSet(), reordered.toSet())
        assertEquals(reordered.size, reordered.distinct().size)
    }

    @Test
    fun `an unknown codec key falls back rather than dropping the list`() {
        assertArrayEquals(defaultOrder, VideoCodecUtils.preferredVideoMimeTypes("theora"))
    }
}
