package io.github.aedev.flow.data.shorts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class ShortsClassifierTest {
    private fun item(
        url: String,
        durationSeconds: Long,
        shortFormContent: Boolean = false,
        streamType: StreamType = StreamType.VIDEO_STREAM,
    ): StreamInfoItem =
        StreamInfoItem(0, url, "item", streamType).apply {
            duration = durationSeconds
            setShortFormContent(shortFormContent)
        }

    // The contract: duration is never a signal.

    @Test
    fun `short music video is not a reel`() {
        val musicVideo = item(url = "https://www.youtube.com/watch?v=abcdefghijk", durationSeconds = 45)

        assertFalse(ShortsClassifier.isReel(musicVideo))
    }

    @Test
    fun `sub-minute normal upload is not a reel`() {
        val clip = item(url = "https://www.youtube.com/watch?v=abcdefghijk", durationSeconds = 12)

        assertFalse(ShortsClassifier.isReel(clip))
    }

    @Test
    fun `two minute upload is not a reel`() {
        val video = item(url = "https://www.youtube.com/watch?v=abcdefghijk", durationSeconds = 118)

        assertFalse(ShortsClassifier.isReel(video))
    }

    @Test
    fun `long video carrying the reel marker is a reel`() {
        // Duration must not veto the marker either: YouTube reports odd durations for reels.
        val marked =
            item(
                url = "https://www.youtube.com/watch?v=abcdefghijk",
                durationSeconds = 240,
                shortFormContent = true,
            )

        assertTrue(ShortsClassifier.isReel(marked))
    }

    // Marker paths.

    @Test
    fun `extractor short form marker is a reel`() {
        val marked =
            item(
                url = "https://www.youtube.com/watch?v=abcdefghijk",
                durationSeconds = 30,
                shortFormContent = true,
            )

        assertTrue(ShortsClassifier.isReel(marked))
    }

    @Test
    fun `shorts url is a reel even without the marker`() {
        val byUrl = item(url = "https://www.youtube.com/shorts/abcdefghijk", durationSeconds = 0)

        assertTrue(ShortsClassifier.isReel(byUrl))
    }

    @Test
    fun `shorts url casing does not matter`() {
        assertTrue(ShortsClassifier.isReelUrl("https://www.youtube.com/SHORTS/abcdefghijk"))
    }

    // isReelUrl edges.

    @Test
    fun `null and blank urls are not reels`() {
        assertFalse(ShortsClassifier.isReelUrl(null))
        assertFalse(ShortsClassifier.isReelUrl(""))
    }

    @Test
    fun `watch url is not a reel url`() {
        assertFalse(ShortsClassifier.isReelUrl("https://www.youtube.com/watch?v=abcdefghijk"))
    }

    @Test
    fun `channel shorts tab url is not mistaken for a reel watch url`() {
        // A channel's Shorts *tab* has no /shorts/<id> segment, so it must not classify as an item.
        assertFalse(ShortsClassifier.isReelUrl("https://www.youtube.com/@channel/shorts"))
    }
}
