package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.pages.InnerTubeJson.ALBUM
import io.github.aedev.flow.innertube.pages.InnerTubeJson.ARTIST
import io.github.aedev.flow.innertube.pages.InnerTubeJson.ATV
import io.github.aedev.flow.innertube.pages.InnerTubeJson.OMV
import io.github.aedev.flow.innertube.pages.InnerTubeJson.browseEndpoint
import io.github.aedev.flow.innertube.pages.InnerTubeJson.queueRow
import io.github.aedev.flow.innertube.pages.InnerTubeJson.run
import io.github.aedev.flow.innertube.pages.InnerTubeJson.separator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextPageTest {
    private val queen = run("Queen", browseEndpoint("UCEPMVbUzImPl4p8k4LkGevA", ARTIST))

    private val queueRows =
        InnerTubeJson
            .next(
                listOf(
                    queueRow(
                        "fJ9rUzIMcZQ",
                        "Bohemian Rhapsody (Live in Budapest)",
                        "6:00",
                        OMV,
                        listOf(queen, separator(), run("2B views"), separator(), run("14M likes")),
                        selected = true,
                    ),
                    queueRow(
                        "BSTsnWoslP4",
                        "Somebody To Love",
                        "4:56",
                        ATV,
                        listOf(queen, separator(), run("A Day At The Races", browseEndpoint("MPREb_x", ALBUM)), separator(), run("1976")),
                    ),
                ),
            ).contents.singleColumnMusicWatchNextResultsRenderer!!
            .tabbedRenderer!!
            .watchNextTabbedResultsRenderer!!
            .tabs
            .first()
            .tabRenderer.content!!
            .musicQueueRenderer!!
            .content!!
            .playlistPanelRenderer.contents
            .mapNotNull { it.playlistPanelVideoRenderer }
            .map { checkNotNull(NextPage.fromPlaylistPanelVideoRenderer(it)) }

    @Test
    fun `the seed queue row carries view and like counts without an album`() {
        val seed = queueRows.first()
        assertEquals("fJ9rUzIMcZQ", seed.id)
        assertEquals(listOf("Queen"), seed.artists.map { it.name })
        assertNull(seed.album)
        assertEquals("2B views", seed.viewCountText)
        assertEquals("14M likes", seed.likeCountText)
    }

    @Test
    fun `a song row keeps its album and does not read the year as a count`() {
        val song = queueRows.last()
        assertEquals("MPREb_x", song.album?.id)
        assertNull(song.viewCountText)
        assertNull(song.likeCountText)
        assertEquals(296, song.duration)
    }
}
