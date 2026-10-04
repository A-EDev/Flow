package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Test

class SearchHistoryEntriesTest {
    private val gson = Gson()
    private val filtered = SearchFilter(duration = Duration.OVER_20_MINUTES, uploadDate = UploadDate.TODAY)

    private fun entry(
        query: String,
        filters: SearchFilter? = null,
    ) = SearchHistoryItem(id = query, query = query, timestamp = 1L, filters = filters)

    @Test
    fun `searching the same words again replaces the entry and moves it to the top`() {
        val history = listOf(entry("cats"), entry("World news", SearchFilter.DEFAULT))

        val updated = history.withSearch("world news ", SearchType.TEXT, filtered, maxSize = 50, now = 2L)

        assertThat(updated.map { it.query }).containsExactly("world news ", "cats").inOrder()
        assertThat(updated.first().filters).isEqualTo(filtered)
    }

    @Test
    fun `an entry saved before filters were kept stays as it was`() {
        val updated = listOf(entry("cats")).withSearch("dogs", SearchType.TEXT, filtered, maxSize = 50, now = 2L)

        assertThat(updated.last()).isEqualTo(entry("cats"))
        assertThat(updated.last().filters).isNull()
    }

    @Test
    fun `history keeps to its size`() {
        val history = (1..5).map { entry("q$it") }

        assertThat(history.withSearch("new", SearchType.TEXT, null, maxSize = 3, now = 2L).map { it.query })
            .containsExactly("new", "q1", "q2")
            .inOrder()
    }

    @Test
    fun `entries stored without filters read back with none`() {
        val json = """[{"id":"a","query":"cats","timestamp":1,"type":"TEXT"}]"""

        val read = gson.fromJson<List<SearchHistoryItem>>(json, historyType).map(SearchHistoryItem::sanitized)

        assertThat(read.single().filters).isNull()
    }

    @Test
    fun `filters survive a round trip through the stored json`() {
        val withFeatures = filtered.copy(contentType = ContentType.VIDEOS, features = setOf(SearchFeature.FOUR_K))

        val json = gson.toJson(listOf(entry("world news", withFeatures)))
        val read = gson.fromJson<List<SearchHistoryItem>>(json, historyType).map(SearchHistoryItem::sanitized)

        assertThat(read.single().filters).isEqualTo(withFeatures)
    }

    @Test
    fun `a filter value from a newer version falls back to its default`() {
        val json =
            """[{"id":"a","query":"cats","timestamp":1,"type":"TEXT",""" +
                """"filters":{"contentType":"PODCASTS","duration":"OVER_20_MINUTES","uploadDate":"TODAY",""" +
                """"sortType":"RELEVANCE","features":["HD","SPATIAL_AUDIO"]}}]"""

        val read = gson.fromJson<List<SearchHistoryItem>>(json, historyType).map(SearchHistoryItem::sanitized)

        assertThat(read.single().filters)
            .isEqualTo(SearchFilter(duration = Duration.OVER_20_MINUTES, uploadDate = UploadDate.TODAY, features = setOf(SearchFeature.HD)))
    }

    @Test
    fun `a stored Shorts search runs over everything once Shorts are hidden`() {
        val shorts = SearchFilter(contentType = ContentType.SHORTS, uploadDate = UploadDate.TODAY)

        assertThat(shorts.availableWith(shortsEnabled = false)).isEqualTo(SearchFilter(uploadDate = UploadDate.TODAY))
        assertThat(shorts.availableWith(shortsEnabled = true)).isEqualTo(shorts)
    }

    private val historyType = object : TypeToken<List<SearchHistoryItem>>() {}.type
}
