package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.pages.InnerTubeJson.ALBUM
import io.github.aedev.flow.innertube.pages.InnerTubeJson.ARTIST
import io.github.aedev.flow.innertube.pages.InnerTubeJson.ATV
import io.github.aedev.flow.innertube.pages.InnerTubeJson.CIRCLE
import io.github.aedev.flow.innertube.pages.InnerTubeJson.DISCOGRAPHY
import io.github.aedev.flow.innertube.pages.InnerTubeJson.PLAYLIST
import io.github.aedev.flow.innertube.pages.InnerTubeJson.browseEndpoint
import io.github.aedev.flow.innertube.pages.InnerTubeJson.carousel
import io.github.aedev.flow.innertube.pages.InnerTubeJson.immersiveHeader
import io.github.aedev.flow.innertube.pages.InnerTubeJson.musicShelf
import io.github.aedev.flow.innertube.pages.InnerTubeJson.run
import io.github.aedev.flow.innertube.pages.InnerTubeJson.separator
import io.github.aedev.flow.innertube.pages.InnerTubeJson.songRow
import io.github.aedev.flow.innertube.pages.InnerTubeJson.twoRow
import io.github.aedev.flow.innertube.pages.InnerTubeJson.videoTwoRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistPageTest {
    private val queenId = "UCEPMVbUzImPl4p8k4LkGevA"
    private val releases = "MPAD$queenId"

    private val page =
        ArtistPage.fromBrowseResponse(
            queenId,
            InnerTubeJson.browse(
                sections =
                    listOf(
                        musicShelf(
                            "Top songs",
                            listOf(songRow("s1", "Bohemian Rhapsody", ATV, listOf(run("Queen", browseEndpoint(queenId, ARTIST))))),
                        ),
                        carousel(
                            "Albums",
                            listOf(
                                twoRow(
                                    "MPREb_a1",
                                    ALBUM,
                                    "A Night At The Opera",
                                    listOf(run("Album"), separator(), run("1975")),
                                    playlistId = "OLAK1",
                                ),
                            ),
                            titleEndpoint = browseEndpoint(releases, DISCOGRAPHY),
                            moreBrowseId = releases,
                        ),
                        carousel(
                            "Singles & EPs",
                            listOf(
                                twoRow(
                                    "MPREb_s1",
                                    ALBUM,
                                    "Seven Seas Of Rhye",
                                    listOf(run("Single"), separator(), run("2026")),
                                    playlistId = "OLAK2",
                                ),
                            ),
                            titleEndpoint = browseEndpoint(releases, DISCOGRAPHY),
                            moreBrowseId = releases,
                        ),
                        carousel(
                            "Videos",
                            listOf(videoTwoRow("v1", "I See You Now", "Roger Taylor")),
                            moreBrowseId = "VLOLAK3",
                            morePageType = PLAYLIST,
                        ),
                        carousel(
                            "Featured on",
                            listOf(
                                twoRow(
                                    "VLPL1",
                                    PLAYLIST,
                                    "Rock Classics",
                                    listOf(run("Playlist"), separator(), run("YouTube Music")),
                                    playlistId = "PL1",
                                ),
                            ),
                        ),
                        carousel(
                            "Fans might also like",
                            listOf(twoRow("UCfreddie", ARTIST, "Freddie Mercury", listOf(run("1.82M subscribers")), crop = CIRCLE)),
                        ),
                        carousel(
                            "Playlists by Queen",
                            listOf(
                                twoRow(
                                    "VLPL9",
                                    PLAYLIST,
                                    "Queen Essentials",
                                    listOf(run("Playlist"), separator(), run("Queen")),
                                    playlistId = "PL9",
                                ),
                            ),
                            moreBrowseId = "VLPL9",
                            morePageType = PLAYLIST,
                        ),
                    ),
                header = immersiveHeader("Queen", "UCiMhD4jzUqG-IgPzUmmytRQ", "19.1M", "103M monthly audience"),
                singleColumn = true,
            ),
        )

    private fun section(kind: ArtistSectionKind) = page.sections.single { it.kind == kind }

    @Test
    fun `header counts are read from the immersive header`() {
        assertEquals("19.1M", page.subscriberCountText)
        assertEquals("103M monthly audience", page.monthlyListenersText)
        assertEquals("Queen", page.artist.title)
        assertEquals("UCiMhD4jzUqG-IgPzUmmytRQ", page.artist.channelId)
    }

    @Test
    fun `release shelves are told apart by order not title`() {
        assertEquals("Albums", section(ArtistSectionKind.ALBUMS).title)
        assertEquals("Singles & EPs", section(ArtistSectionKind.SINGLES).title)
        assertEquals(releases, section(ArtistSectionKind.ALBUMS).moreEndpoint?.browseId)
    }

    @Test
    fun `featured on is the playlist shelf without a more button`() {
        val featured = section(ArtistSectionKind.FEATURED_ON)
        assertEquals("Featured on", featured.title)
        assertNull(featured.moreEndpoint)
        assertTrue(featured.items.all { it is PlaylistItem })
        assertEquals(ArtistSectionKind.OTHER, page.sections.single { it.title == "Playlists by Queen" }.kind)
    }

    @Test
    fun `top songs videos and related artists are typed`() {
        assertEquals("Top songs", section(ArtistSectionKind.TOP_SONGS).title)
        assertEquals("Videos", section(ArtistSectionKind.VIDEOS).title)
        val related = section(ArtistSectionKind.RELATED_ARTISTS)
        assertEquals("Fans might also like", related.title)
        assertTrue(related.items.all { it is ArtistItem })
    }
}
