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

    @Test
    fun `two names on one channel give one artist seed`() {
        fun play(
            artist: String,
            channel: String,
        ) = MusicTrack(videoId = "$artist$channel", title = "Song", artist = artist, thumbnailUrl = "", duration = 200, channelId = channel)
        val history = List(5) { play("Artist", "UC1") } + List(4) { play("Artist & Guest", "UC1") } + List(3) { play("Other", "UC2") }

        repeat(50) { seed ->
            val seeds = SimilarToSections.topArtistSeeds(history, Random(seed))
            assertEquals(2, seeds.size)
            assertEquals(setOf("UC1", "UC2"), seeds.map { it.second.channelId }.toSet())
            assertEquals("Artist", seeds.first { it.second.channelId == "UC1" }.first)
        }
    }

    @Test
    fun `artists without a channel are skipped instead of taking a pick`() {
        val history =
            listOf(
                MusicTrack(videoId = "a", title = "Song", artist = "No channel", thumbnailUrl = "", duration = 200),
                MusicTrack(videoId = "b", title = "Song", artist = "No channel", thumbnailUrl = "", duration = 200),
                MusicTrack(videoId = "c", title = "Song", artist = "Known", thumbnailUrl = "", duration = 200, channelId = "UC3"),
            )

        assertEquals(listOf("Known" to history[2]), SimilarToSections.topArtistSeeds(history, Random(1)))
    }
}
