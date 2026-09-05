package io.github.aedev.flow.data.recommendation.music.graph

import io.github.aedev.flow.data.music.model.ArtistDetails
import io.github.aedev.flow.data.music.model.MusicArtist
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.data.music.model.RelatedMusic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicGraphStoreTest {
    private val dao = FakeMusicGraphDao()
    private val store = MusicGraphStore(dao)
    private val eightDaysAgo = System.currentTimeMillis() - 8 * 24 * 60 * 60 * 1000L

    private fun track(index: Int) =
        MusicTrack(videoId = "v$index", title = "Song $index", artist = "Artist", thumbnailUrl = "", duration = 200)

    private fun playlist(
        id: String,
        size: Int,
    ) = PlaylistDetails(id = id, title = id, thumbnailUrl = "", author = "Curator", trackCount = size, tracks = List(size) { track(it) })

    @Test
    fun `recorded playlist tracks come back in order, capped per source, only for known playlists`() =
        runBlocking {
            store.recordPlaylist(playlist("PL1", 80))
            val tracks = store.playlistTracksFor(listOf("PL1", "PL2"))
            assertEquals(setOf("PL1"), tracks.keys)
            assertEquals(List(64) { "v$it" }, tracks.getValue("PL1").map { it.videoId })
        }

    @Test
    fun `re-recording a playlist replaces its membership`() =
        runBlocking {
            store.recordPlaylist(playlist("PL1", 3))
            store.recordPlaylist(playlist("PL1", 2))
            assertEquals(listOf("v0", "v1"), store.playlistTracksFor(listOf("PL1")).getValue("PL1").map { it.videoId })
        }

    @Test
    fun `stale playlist membership is not served`() =
        runBlocking {
            store.recordPlaylist(playlist("PL1", 3))
            dao.upsertEdges(dao.edges.values.map { it.copy(lastSeenAt = eightDaysAgo) })
            assertTrue(store.playlistTracksFor(listOf("PL1")).isEmpty())
        }

    @Test
    fun `related pages round-trip through the graph and expire after the ttl`() =
        runBlocking {
            val seed = track(0).copy(artists = listOf(MusicArtist("Artist", "UCa")))
            val related =
                RelatedMusic(
                    seed = seed,
                    seedArtistId = "UCa",
                    tracks = listOf(track(1), track(2)),
                    radioTracks = listOf(track(3)),
                    similarArtists = listOf(ArtistDetails(name = "Other", channelId = "UCb", thumbnailUrl = "", subscriberCount = 5)),
                    playlists = listOf(MusicPlaylist(id = "PL9", title = "Mix", thumbnailUrl = "", author = "Curator")),
                    artistAlbums = emptyList(),
                )
            assertNull(store.relatedFor("v0"))

            store.recordRelated("v0", seed, related)
            val fromGraph = store.relatedFor("v0")!!
            assertEquals(listOf("v1", "v2"), fromGraph.tracks.map { it.videoId })
            assertEquals(listOf("v3"), fromGraph.radioTracks.map { it.videoId })
            assertEquals(listOf("UCb"), fromGraph.similarArtists.map { it.channelId })
            assertEquals(listOf("PL9"), fromGraph.playlists.map { it.id })

            dao.upsertTracks(listOf(dao.tracks.getValue("v0").copy(relatedCrawledAt = eightDaysAgo)))
            assertNull(store.relatedFor("v0"))
        }
}
