package io.github.aedev.flow.data.recommendation.music.graph

import io.github.aedev.flow.data.local.entity.MusicGraphArtistEntity
import io.github.aedev.flow.data.local.entity.MusicGraphTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicGraphSectionsTest {
    private fun track(
        id: String,
        album: String,
        key: String = id,
        views: Long? = null,
        likes: Long? = null,
        isVideo: Boolean = false,
    ) = MusicGraphTrackEntity(
        videoId = id,
        canonicalKey = key,
        title = id,
        artist = "artist",
        artistId = "UCartist",
        albumId = album,
        albumTitle = album,
        thumbnailUrl = "",
        durationSec = 200,
        isVideo = isVideo,
        isExplicit = false,
        viewCount = views,
        likeCount = likes,
        relatedCrawledAt = null,
        lastSeenAt = 0L,
    )

    @Test
    fun `deep cuts skip played tracks and their variants, cap per album and follow album priority`() {
        val albumA =
            listOf(
                track("a1", "A", views = 100, likes = 1),
                track("a2", "A", views = 100, likes = 20),
                track("a3", "A"),
                track("a4", "A", key = "played-variant"),
            )
        val albumB = listOf(track("b1", "B"), track("b2", "B", isVideo = true))
        val result =
            MusicGraphSections.deepCuts(
                albumTracks = mapOf("A" to albumA, "B" to albumB),
                albumPriority = listOf("B", "A"),
                playedVideoIds = setOf("a1"),
                playedCanonicalKeys = setOf("played-variant"),
                limit = 10,
            )
        assertEquals(listOf("b1", "a2", "a3"), result.map { it.videoId })
    }

    @Test
    fun `artists for you drop the listener's own and hidden artists by id or name`() {
        val candidates =
            listOf(
                MusicGraphArtistEntity("UCa", "Alpha", "", 10, null, 0L),
                MusicGraphArtistEntity("UCb", "Beta", "", 9, null, 0L),
                MusicGraphArtistEntity("UCc", "Gamma", "", 8, null, 0L),
            )
        val result = MusicGraphSections.artistsForYou(candidates, excludedArtistKeys = setOf("UCa", "gamma"), limit = 5)
        assertEquals(listOf("UCb"), result.map { it.browseId })
    }
}
