package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistPageTest {
    private val page = ArtistPage.fromBrowseResponse("UCEPMVbUzImPl4p8k4LkGevA", InnerTubeFixtures.browse("artist_queen"))

    private fun section(kind: ArtistSectionKind) = page.sections.single { it.kind == kind }

    @Test
    fun `header counts are read from the immersive header`() {
        assertEquals("19.1M", page.subscriberCountText)
        assertEquals("103M monthly audience", page.monthlyListenersText)
        assertEquals("Queen", page.artist.title)
    }

    @Test
    fun `release shelves are told apart by order not title`() {
        assertEquals("Albums", section(ArtistSectionKind.ALBUMS).title)
        assertEquals("Singles & EPs", section(ArtistSectionKind.SINGLES).title)
        assertTrue(section(ArtistSectionKind.ALBUMS).moreEndpoint?.browseId?.startsWith("MPAD") == true)
    }

    @Test
    fun `featured on is the playlist shelf without a more button`() {
        val featured = section(ArtistSectionKind.FEATURED_ON)
        assertEquals("Featured on", featured.title)
        assertNull(featured.moreEndpoint)
        assertTrue(featured.items.all { it is PlaylistItem })
        val ownPlaylists = page.sections.single { it.title == "Playlists by Queen" }
        assertEquals(ArtistSectionKind.OTHER, ownPlaylists.kind)
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
