package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.Artist
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.pages.SearchSummary
import io.github.aedev.flow.innertube.pages.SearchSummaryPage
import org.junit.Assert.assertEquals
import org.junit.Test

class YTItemKeysTest {

    @Test
    fun `identity includes item type so different result types do not collide`() {
        val items = listOf(
            song(id = "shared"),
            album(id = "shared")
        )

        val result = items.distinctByStableIdentity()

        assertEquals(listOf("song:shared", "album:shared"), result.map { it.stableIdentityKey() })
    }

    @Test
    fun `summary normalization removes duplicates that share a lazy-key namespace`() {
        val page = SearchSummaryPage(
            summaries = listOf(
                SearchSummary(
                    title = "More results",
                    items = listOf(song("first"), song("second"), song("first"))
                ),
                SearchSummary(
                    title = "More results",
                    items = listOf(song("second"), song("third"))
                ),
                SearchSummary(
                    title = "Songs",
                    items = listOf(song("first"))
                )
            ),
            continuation = "next"
        )

        val result = page.distinctItemsForLazyKeys()

        assertEquals(
            listOf(
                listOf("first", "second"),
                listOf("third"),
                listOf("first")
            ),
            result.summaries.map { summary -> summary.items.map { it.id } }
        )
        assertEquals("next", result.continuation)
    }

    @Test
    fun `blank music identities are not exposed to keyed lazy layouts`() {
        val result = listOf(song(""), song("valid")).distinctByStableIdentity()

        assertEquals(listOf("valid"), result.map { it.id })
    }

    private fun song(id: String) = SongItem(
        id = id,
        title = id,
        artists = listOf(Artist(name = "Artist", id = "channel")),
        thumbnail = "thumbnail"
    )

    private fun album(id: String) = AlbumItem(
        browseId = id,
        playlistId = "playlist-$id",
        title = id,
        artists = emptyList(),
        thumbnail = "thumbnail"
    )
}
