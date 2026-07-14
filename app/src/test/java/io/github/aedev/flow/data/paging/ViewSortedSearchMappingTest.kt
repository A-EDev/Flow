package io.github.aedev.flow.data.paging

import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.Duration
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.local.SortType
import io.github.aedev.flow.data.local.UploadDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewSortedSearchMappingTest {
    @Test
    fun mapsEveryContentTypeToItsViewSortedParameter() {
        val expectedParams = mapOf(
            ContentType.ALL to "CAM%3D",
            ContentType.VIDEOS to "CAMSAhAB",
            ContentType.SHORTS to "CAMSAhAB",
            ContentType.CHANNELS to "CAMSAhAC",
            ContentType.PLAYLISTS to "CAMSAhAD",
            ContentType.LIVE to "CAMSBBABQAE%3D",
        )

        expectedParams.forEach { (contentType, expected) ->
            assertEquals(
                expected,
                SearchFilter(
                    contentType = contentType,
                    sortType = SortType.VIEWS,
                ).toViewSortedSearchParams(),
            )
        }
    }

    @Test
    fun mapsAppFiltersToCombinedYouTubeParameter() {
        val filter = SearchFilter(
            contentType = ContentType.VIDEOS,
            duration = Duration.FROM_4_TO_20_MINUTES,
            uploadDate = UploadDate.TODAY,
            sortType = SortType.VIEWS,
        )

        assertEquals("CAMSBggCEAEYAw%3D%3D", filter.toViewSortedSearchParams())
    }

    @Test
    fun mapsLiveContentToVideoAndLiveFeatureFields() {
        val filter = SearchFilter(
            contentType = ContentType.LIVE,
            sortType = SortType.VIEWS,
        )

        assertEquals("CAMSBBABQAE%3D", filter.toViewSortedSearchParams())
    }

    @Test
    fun omitsVideoOnlyFiltersForChannelsAndPlaylists() {
        listOf(
            ContentType.CHANNELS to "CAMSAhAC",
            ContentType.PLAYLISTS to "CAMSAhAD",
        ).forEach { (contentType, expected) ->
            val filter = SearchFilter(
                contentType = contentType,
                duration = Duration.FROM_4_TO_20_MINUTES,
                uploadDate = UploadDate.TODAY,
                sortType = SortType.VIEWS,
            )

            assertEquals(expected, filter.toViewSortedSearchParams())
        }
    }
}
