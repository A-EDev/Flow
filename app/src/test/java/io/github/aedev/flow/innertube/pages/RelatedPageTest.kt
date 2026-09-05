package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedPageTest {
    private val queen = RelatedPage.fromBrowseResponse(InnerTubeFixtures.browse("related_queen"))
    private val edSheeran = RelatedPage.fromBrowseResponse(InnerTubeFixtures.browse("related_ed_sheeran"))

    @Test
    fun `shelves are typed by structure in the live order`() {
        val expected =
            listOf(
                RelatedShelfType.SIMILAR,
                RelatedShelfType.PLAYLISTS,
                RelatedShelfType.OTHER_PERFORMANCES,
                RelatedShelfType.SIMILAR_ARTISTS,
                RelatedShelfType.MORE_FROM_ARTIST,
            )
        assertEquals(expected, queen.sections.map { it.type })
        assertEquals(expected, edSheeran.sections.map { it.type })
    }

    @Test
    fun `songs come from the similar shelf only`() {
        val similarIds =
            queen.sections
                .first { it.type == RelatedShelfType.SIMILAR }
                .items
                .map { it.id }
        assertEquals(similarIds, queen.songs.map { it.id })
        val otherPerformances = queen.sections.first { it.type == RelatedShelfType.OTHER_PERFORMANCES }.items
        assertTrue(otherPerformances.none { it.id in similarIds })
    }

    @Test
    fun `other performances keep their video type instead of leaking into songs`() {
        val performances =
            edSheeran.sections
                .first { it.type == RelatedShelfType.OTHER_PERFORMANCES }
                .items
                .filterIsInstance<SongItem>()
        assertTrue(performances.any { it.isVideoSong })
        assertTrue(edSheeran.songs.none { it.isVideoSong })
    }

    @Test
    fun `view counts on a user upload are not parsed as an artist`() {
        val flashmob =
            queen.sections
                .first { it.type == RelatedShelfType.OTHER_PERFORMANCES }
                .items
                .filterIsInstance<SongItem>()
                .first()
        assertEquals(listOf("Julien Cohen"), flashmob.artists.map { it.name })
        assertEquals("43M views", flashmob.viewCountText)
        assertNull(flashmob.album)
    }

    @Test
    fun `more from artist shelf carries the artist onto its albums`() {
        val shelf = queen.sections.first { it.type == RelatedShelfType.MORE_FROM_ARTIST }
        assertEquals("UCEPMVbUzImPl4p8k4LkGevA", shelf.artistBrowseId)
        val albums = shelf.items.filterIsInstance<AlbumItem>()
        assertFalse(albums.isEmpty())
        assertTrue(
            albums.all { album ->
                album.artists?.singleOrNull()?.let { it.name == "Queen" && it.id == shelf.artistBrowseId } == true
            },
        )
        assertEquals(albums.map { it.id }, queen.albums.map { it.id })
    }

    @Test
    fun `recommended playlists keep their curator and drop the view count`() {
        val playlists = queen.playlists
        assertEquals(listOf("Mateus Soares", "INV SPORTMARKET 2020 CA", "YouTube Music"), playlists.map { it.author?.name })
        assertTrue(playlists.all { it.songCountText == null })
    }

    @Test
    fun `similar artists expose their channel ids`() {
        val artists = queen.artists
        assertEquals(listOf("Freddie Mercury", "Brian May", "Elton John"), artists.map { it.title })
        assertTrue(artists.all { it.id.startsWith("UC") && it.channelId == it.id })
    }
}
