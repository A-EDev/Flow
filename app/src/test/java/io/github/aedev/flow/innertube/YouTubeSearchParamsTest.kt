package io.github.aedev.flow.innertube

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeSearchParamsTest {
    @Test
    fun buildsYouTubeVideosByViewCountParameter() {
        assertEquals("CAMSAhAB", YouTubeSearchParams.videosSortedByViewCount())
    }
}
