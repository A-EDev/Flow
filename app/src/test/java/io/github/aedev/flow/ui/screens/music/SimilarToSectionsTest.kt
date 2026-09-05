package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.data.music.model.ArtistDetails
import io.github.aedev.flow.data.music.model.MusicItemType
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.data.music.model.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimilarToSectionsTest {
    private val songs = List(20) { MusicTrack(videoId = "s$it", title = "Song $it", artist = "Artist", thumbnailUrl = "", duration = 200) }
    private val artists = List(10) { ArtistDetails(name = "Artist $it", channelId = "UC$it", thumbnailUrl = "", subscriberCount = 0) }
    private val playlists = List(10) { MusicPlaylist(id = "PL$it", title = "Playlist $it", thumbnailUrl = "", author = "Curator $it") }

    @Test
    fun `every song is kept, two to three artists and playlists are spread in without touching`() {
        repeat(50) { seed ->
            val mixed = SimilarToSections.mixed(songs, artists, playlists, Random(seed))
            assertEquals(songs, mixed.filter { it.itemType == MusicItemType.SONG })
            val artistCount = mixed.count { it.itemType == MusicItemType.ARTIST }
            val playlistCount = mixed.count { it.itemType == MusicItemType.PLAYLIST }
            assertTrue(artistCount in SimilarToSections.MIN_EXTRAS..SimilarToSections.MAX_EXTRAS)
            assertTrue(playlistCount in SimilarToSections.MIN_EXTRAS..SimilarToSections.MAX_EXTRAS)
            assertTrue(mixed.take(2).all { it.itemType == MusicItemType.SONG })
            mixed.zipWithNext().forEach { (a, b) ->
                assertTrue(a.itemType == MusicItemType.SONG || b.itemType == MusicItemType.SONG)
            }
        }
    }

    @Test
    fun `the same seed always produces the same lane`() {
        assertEquals(
            SimilarToSections.mixed(songs, artists, playlists, Random(7)),
            SimilarToSections.mixed(songs, artists, playlists, Random(7)),
        )
    }

    @Test
    fun `songs pass through untouched when nothing can be mixed in`() {
        assertSame(songs, SimilarToSections.mixed(songs, emptyList(), emptyList(), Random(1)))
        assertTrue(SimilarToSections.mixed(emptyList(), artists, playlists, Random(1)).isEmpty())
    }

    @Test
    fun `collection tracks carry the ids the feed routes on`() {
        val artist = artists.first().asCollectionTrack()
        assertEquals("UC0", artist.videoId)
        assertEquals("UC0", artist.channelId)
        assertEquals(MusicItemType.ARTIST, artist.itemType)
        val album = playlists.first().copy(authorId = "UC9", authorName = "Queen").asCollectionTrack(MusicItemType.ALBUM)
        assertEquals("PL0", album.videoId)
        assertEquals("Queen", album.artist)
        assertEquals("UC9", album.channelId)
        assertEquals(MusicItemType.ALBUM, album.itemType)
    }
}
