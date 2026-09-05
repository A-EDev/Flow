package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.pages.InnerTubeJson.ALBUM
import io.github.aedev.flow.innertube.pages.InnerTubeJson.ARTIST
import io.github.aedev.flow.innertube.pages.InnerTubeJson.ATV
import io.github.aedev.flow.innertube.pages.InnerTubeJson.CIRCLE
import io.github.aedev.flow.innertube.pages.InnerTubeJson.PLAYLIST
import io.github.aedev.flow.innertube.pages.InnerTubeJson.UGC
import io.github.aedev.flow.innertube.pages.InnerTubeJson.USER
import io.github.aedev.flow.innertube.pages.InnerTubeJson.browseEndpoint
import io.github.aedev.flow.innertube.pages.InnerTubeJson.carousel
import io.github.aedev.flow.innertube.pages.InnerTubeJson.run
import io.github.aedev.flow.innertube.pages.InnerTubeJson.separator
import io.github.aedev.flow.innertube.pages.InnerTubeJson.songRow
import io.github.aedev.flow.innertube.pages.InnerTubeJson.twoRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedPageTest {
    private val queenId = "UCEPMVbUzImPl4p8k4LkGevA"
    private val queen = run("Queen", browseEndpoint(queenId, ARTIST))

    private val page =
        RelatedPage.fromBrowseResponse(
            InnerTubeJson.browse(
                listOf(
                    carousel(
                        "You might also like",
                        listOf(
                            songRow(
                                "s1",
                                "Somebody To Love",
                                ATV,
                                listOf(queen),
                                album = run("A Day At The Races", browseEndpoint("MPREb_1", ALBUM)),
                            ),
                            songRow("s2", "Under Pressure", ATV, listOf(queen)),
                        ),
                    ),
                    carousel(
                        "Recommended playlists",
                        listOf(
                            twoRow(
                                "VLPL1",
                                PLAYLIST,
                                "Rock favorits",
                                listOf(
                                    run("Playlist"),
                                    separator(),
                                    run("Mateus Soares", browseEndpoint("UCuser", USER)),
                                    separator(),
                                    run("11K views"),
                                ),
                                playlistId = "PL1",
                            ),
                            twoRow(
                                "VLPL2",
                                PLAYLIST,
                                "Classic Rock",
                                listOf(run("Playlist"), separator(), run("YouTube Music"), separator(), run("2M views")),
                                playlistId = "PL2",
                            ),
                        ),
                    ),
                    carousel(
                        "Other performances",
                        listOf(
                            songRow("u1", "Bohemian Rhapsody Flashmob", UGC, listOf(run("Julien Cohen"), separator(), run("43M views"))),
                            songRow("s3", "Bohemian Rhapsody (Live)", ATV, listOf(queen)),
                        ),
                    ),
                    carousel(
                        "Similar artists",
                        listOf(
                            twoRow("UCfreddie", ARTIST, "Freddie Mercury", listOf(run("1.82M subscribers")), crop = CIRCLE),
                            twoRow("UCbrian", ARTIST, "Brian May", listOf(run("600K subscribers")), crop = CIRCLE),
                        ),
                    ),
                    carousel(
                        "Queen",
                        listOf(
                            twoRow(
                                "MPREb_a1",
                                ALBUM,
                                "Queen Budapest",
                                listOf(run("Album"), separator(), run("2026")),
                                playlistId = "OLAK1",
                            ),
                            twoRow("MPREb_a2", ALBUM, "The Miracle", listOf(run("Album"), separator(), run("1989")), playlistId = "OLAK2"),
                        ),
                        titleEndpoint = browseEndpoint(queenId, ARTIST),
                        strapline = "MORE FROM",
                    ),
                ),
            ),
        )

    @Test
    fun `shelves are typed by structure in the live order`() {
        assertEquals(
            listOf(
                RelatedShelfType.SIMILAR,
                RelatedShelfType.PLAYLISTS,
                RelatedShelfType.OTHER_PERFORMANCES,
                RelatedShelfType.SIMILAR_ARTISTS,
                RelatedShelfType.MORE_FROM_ARTIST,
            ),
            page.sections.map { it.type },
        )
    }

    @Test
    fun `songs and other performances come from their own shelves`() {
        assertEquals(listOf("s1", "s2"), page.songs.map { it.id })
        assertEquals(
            "MPREb_1",
            page.songs
                .first()
                .album
                ?.id,
        )
        assertEquals(listOf("u1", "s3"), page.otherPerformances.map { it.id })
    }

    @Test
    fun `other performances keep their video type instead of leaking into songs`() {
        assertTrue(page.otherPerformances.first().isVideoSong)
        assertFalse(page.otherPerformances.last().isVideoSong)
        assertTrue(page.songs.none { it.isVideoSong })
    }

    @Test
    fun `view counts on a user upload are not parsed as an artist`() {
        val flashmob = page.otherPerformances.first()
        assertEquals(listOf("Julien Cohen"), flashmob.artists.map { it.name })
        assertEquals("43M views", flashmob.viewCountText)
        assertNull(flashmob.album)
    }

    @Test
    fun `more from artist shelf carries the artist onto its albums`() {
        val shelf = page.sections.first { it.type == RelatedShelfType.MORE_FROM_ARTIST }
        assertEquals(queenId, shelf.artistBrowseId)
        val albums = shelf.items.filterIsInstance<AlbumItem>()
        assertEquals(listOf("MPREb_a1", "MPREb_a2"), albums.map { it.id })
        assertTrue(albums.all { album -> album.artists?.singleOrNull()?.let { it.name == "Queen" && it.id == queenId } == true })
        assertEquals(2026, albums.first().year)
        assertEquals(albums.map { it.id }, page.albums.map { it.id })
    }

    @Test
    fun `recommended playlists keep their curator and drop the view count`() {
        assertEquals(listOf("Mateus Soares", "YouTube Music"), page.playlists.map { it.author?.name })
        assertEquals(listOf("PL1", "PL2"), page.playlists.map { it.id })
        assertTrue(page.playlists.all { it.songCountText == null })
    }

    @Test
    fun `similar artists expose their channel ids and subscriber counts`() {
        val artists = page.artists
        assertEquals(listOf("Freddie Mercury", "Brian May"), artists.map { it.title })
        assertEquals(listOf("1.82M subscribers", "600K subscribers"), artists.map { it.subscriberCountText })
        assertTrue(artists.all { it.id.startsWith("UC") && it.channelId == it.id })
    }
}
