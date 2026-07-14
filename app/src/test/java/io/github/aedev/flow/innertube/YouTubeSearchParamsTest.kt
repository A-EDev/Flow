package io.github.aedev.flow.innertube

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeSearchParamsTest {
    @Test
    fun buildsViewCountParameterWithoutImplicitContentFilter() {
        assertEquals("CAM%3D", YouTubeSearchParams.sortedByViewCount())
    }

    @Test
    fun buildsVideosByViewCountParameter() {
        assertEquals(
            "CAMSAhAB",
            YouTubeSearchParams.sortedByViewCount(
                contentType = YouTubeSearchParams.ContentType.VIDEO,
            ),
        )
    }

    @Test
    fun combinesViewCountWithContentDateAndDurationFilters() {
        assertEquals(
            "CAMSBggCEAEYAw%3D%3D",
            YouTubeSearchParams.sortedByViewCount(
                contentType = YouTubeSearchParams.ContentType.VIDEO,
                duration = YouTubeSearchParams.Duration.MEDIUM,
                uploadDate = YouTubeSearchParams.UploadDate.TODAY,
            ),
        )
    }

    @Test
    fun buildsChannelAndPlaylistParameters() {
        assertEquals(
            "CAMSAhAC",
            YouTubeSearchParams.sortedByViewCount(
                contentType = YouTubeSearchParams.ContentType.CHANNEL,
            ),
        )
        assertEquals(
            "CAMSAhAD",
            YouTubeSearchParams.sortedByViewCount(
                contentType = YouTubeSearchParams.ContentType.PLAYLIST,
            ),
        )
    }

    @Test
    fun buildsLiveVideosByViewCountParameter() {
        assertEquals(
            "CAMSBBABQAE%3D",
            YouTubeSearchParams.sortedByViewCount(
                contentType = YouTubeSearchParams.ContentType.VIDEO,
                liveOnly = true,
            ),
        )
    }
}
