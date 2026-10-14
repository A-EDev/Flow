package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.pages.InnerTubeJson.ALBUM
import io.github.aedev.flow.innertube.pages.InnerTubeJson.ARTIST
import io.github.aedev.flow.innertube.pages.InnerTubeJson.ATV
import io.github.aedev.flow.innertube.pages.InnerTubeJson.CIRCLE
import io.github.aedev.flow.innertube.pages.InnerTubeJson.DISCOGRAPHY
import io.github.aedev.flow.innertube.pages.InnerTubeJson.PLAYLIST
import io.github.aedev.flow.innertube.pages.InnerTubeJson.USER
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

    private val ceroId = "UCWpo-gnaSXpFw--KfmfKZKw"
    private val ceroChannel = "UCPPLrBZV76cmzFoo-PPzr9Q"
    private val ceroReleases = "MPAD$ceroId"
    private val albumsParams = "ggMIegYIARoCAQI%3D"
    private val singlesParams = "ggMIegYIAhoCAQI%3D"

    private fun ceroAlbum(id: String) = twoRow(id, ALBUM, "Album $id", listOf(run("2026")), playlistId = "OLAK$id")

    private fun ceroSingle(id: String) =
        twoRow(id, ALBUM, "Single $id", listOf(run("Сингл"), separator(), run("2026")), playlistId = "OLAK$id")

    private fun singlesShelf(title: String) =
        carousel(
            title,
            listOf(ceroSingle("MPREb_s1"), ceroSingle("MPREb_s2")),
            titleEndpoint = browseEndpoint(ceroReleases, DISCOGRAPHY, singlesParams),
            moreBrowseId = ceroReleases,
            moreParams = singlesParams,
        )

    private fun ceroPage(vararg sections: String) =
        ArtistPage.fromBrowseResponse(
            ceroId,
            InnerTubeJson.browse(
                sections = sections.toList(),
                header = immersiveHeader("Cero*", ceroChannel, "12K", "114K monthly audience"),
                singleColumn = true,
            ),
        )

    @Test
    fun `an albums shelf without a discography link is still the albums shelf`() {
        val cero =
            ceroPage(
                musicShelf("Популярные треки", listOf(songRow("s1", "p4m", ATV, listOf(run("Cero*", browseEndpoint(ceroId, ARTIST)))))),
                carousel("Альбомы", listOf(ceroAlbum("MPREb_a1"), ceroAlbum("MPREb_a2"))),
                singlesShelf("Синглы и выпуски"),
            )

        assertEquals("Альбомы", cero.sections.single { it.kind == ArtistSectionKind.ALBUMS }.title)
        assertEquals("Синглы и выпуски", cero.sections.single { it.kind == ArtistSectionKind.SINGLES }.title)
        assertTrue(
            cero.sections
                .single { it.kind == ArtistSectionKind.ALBUMS }
                .items
                .all { it is AlbumItem },
        )
    }

    @Test
    fun `discography links outrank page order`() {
        val cero =
            ceroPage(
                singlesShelf("Singles & EPs"),
                carousel(
                    "Albums",
                    listOf(ceroAlbum("MPREb_a1")),
                    moreBrowseId = ceroReleases,
                    moreParams = albumsParams,
                ),
            )

        assertEquals("Albums", cero.sections.single { it.kind == ArtistSectionKind.ALBUMS }.title)
        assertEquals("Singles & EPs", cero.sections.single { it.kind == ArtistSectionKind.SINGLES }.title)
    }

    @Test
    fun `a lone unlinked shelf of singles is the singles shelf`() {
        val cero = ceroPage(carousel("Singles & EPs", listOf(ceroSingle("MPREb_s1"))))

        assertEquals(ArtistSectionKind.SINGLES, cero.sections.single().kind)
    }

    @Test
    fun `the artist's own playlists are never featured on`() {
        val cero =
            ceroPage(
                carousel(
                    "Playlists by Cero*",
                    listOf(
                        twoRow(
                            "VLPLcero",
                            PLAYLIST,
                            "p4m",
                            listOf(
                                run("Playlist"),
                                separator(),
                                run("Cero*", browseEndpoint(ceroChannel, USER)),
                                separator(),
                                run("13K views"),
                            ),
                            playlistId = "PLcero",
                        ),
                    ),
                ),
            )

        val playlists = cero.sections.single()
        assertEquals(ArtistSectionKind.OTHER, playlists.kind)
        assertEquals("Cero*", (playlists.items.single() as PlaylistItem).author?.name)
    }

    @Test
    fun `discography params decode to the release shelf they open`() {
        assertEquals(ArtistSectionKind.ALBUMS, ArtistDiscographyParams.releaseKind(albumsParams))
        assertEquals(ArtistSectionKind.SINGLES, ArtistDiscographyParams.releaseKind("ggMIegYIAhoCAQI="))
        assertNull(ArtistDiscographyParams.releaseKind("ggMCCAI%3D"))
        assertNull(ArtistDiscographyParams.releaseKind("not base64 at all"))
        assertNull(ArtistDiscographyParams.releaseKind(null))
    }
}
