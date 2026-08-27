package io.github.aedev.flow.player.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `a preferred codec plus its fallback lead, in that order`() {
        val reordered = VideoCodecUtils.preferredVideoMimeTypes("av1,vp9")

        assertEquals("video/av01", reordered[0])
        assertEquals("video/x-vnd.on2.vp9", reordered[1])
        assertEquals(defaultOrder.toSet(), reordered.toSet())
        assertEquals(reordered.size, reordered.distinct().size)
    }

    @Test
    fun `a fallback only outranks the built-in order, never the preferred codec`() {
        val preference = "av1,vp9"

        assertTrue(
            VideoCodecUtils.codecRankWithPreference("av1", preference) <
                VideoCodecUtils.codecRankWithPreference("vp9", preference),
        )
        assertTrue(
            VideoCodecUtils.codecRankWithPreference("vp9", preference) <
                VideoCodecUtils.codecRankWithPreference("h264", preference),
        )
    }

    // Without a fallback the decode-cost order still applies, which puts H.264 ahead of VP9 (#812).
    @Test
    fun `a lone preferred codec leaves the rest in the built-in order`() {
        assertEquals(-1, VideoCodecUtils.codecRankWithPreference("av1", "av1"))
        assertTrue(
            VideoCodecUtils.codecRankWithPreference("h264", "av1") <
                VideoCodecUtils.codecRankWithPreference("vp9", "av1"),
        )
    }

    @Test
    fun `an empty or auto preference ranks nothing above the built-in order`() {
        listOf(null, "", "auto", " , auto ").forEach { preference ->
            assertEquals(emptyList<String>(), VideoCodecUtils.codecPriorityList(preference))
            assertEquals(
                VideoCodecUtils.playbackCodecRank("av1"),
                VideoCodecUtils.codecRankWithPreference("av1", preference),
            )
        }
    }

    @Test
    fun `the priority list is normalised and deduplicated`() {
        assertEquals(listOf("av1", "vp9"), VideoCodecUtils.codecPriorityList(" AV1 , vp9 , av1 "))
    }
}
