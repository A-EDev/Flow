package io.github.aedev.flow.sync

import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.sync.canonical.CanonicalLike
import io.github.aedev.flow.sync.canonical.CanonicalLikeMeta
import io.github.aedev.flow.sync.mapping.LikesMapper
import io.github.aedev.flow.sync.protocol.SyncSerialization
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks in the "Unknown Artist" fix: a music like carries its display metadata in the nested
 * `meta` object (desktop §6.3) and survives the encode → decode → apply round-trip. Android also
 * reads either shape (meta-first, flat fallback) so it interops with a peer that sends either.
 */
class LikeMetaTest {

    @Test
    fun music_like_exports_meta_and_round_trips() {
        val info = LikedVideoInfo(
            videoId = "abc123",
            title = "Bohemian Rhapsody",
            thumbnail = "https://img/x.jpg",
            channelName = "Queen",
            likedAt = 1_700_000_000_000,
            isMusic = true,
        )

        val canonical = LikesMapper.likedToCanonical(info, node = "node")
        assertEquals("Queen", canonical.meta.artist)
        assertEquals("Bohemian Rhapsody", canonical.meta.title)

        // Survives the wire (meta is nested, so it must canonicalize + decode intact).
        val wire = SyncSerialization.encodeLikes(listOf(canonical))
        val decoded = SyncSerialization.decodeLikes(wire.lines).single()
        val back = LikesMapper.toLikedInfo(decoded)
        assertEquals("Queen", back.channelName)
        assertEquals("Bohemian Rhapsody", back.title)
        assertEquals("https://img/x.jpg", back.thumbnail)
        assertEquals(true, back.isMusic)
    }

    @Test
    fun reads_meta_when_present() {
        val c = CanonicalLike(
            kind = CanonicalLike.KIND_MUSIC, id = "v", state = CanonicalLike.STATE_LIKED,
            meta = CanonicalLikeMeta(title = "T", artist = "A", thumbnailUrl = "U"),
        )
        val info = LikesMapper.toLikedInfo(c)
        assertEquals("A", info.channelName)
        assertEquals("T", info.title)
        assertEquals("U", info.thumbnail)
    }

    @Test
    fun falls_back_to_flat_fields_when_meta_absent() {
        // A peer that sent only the flat mirror (no meta) must still resolve.
        val c = CanonicalLike(
            kind = CanonicalLike.KIND_VIDEO, id = "v", state = CanonicalLike.STATE_LIKED,
            title = "FlatTitle", channelName = "FlatChannel", thumbnailUrl = "FlatThumb",
        )
        val info = LikesMapper.toLikedInfo(c)
        assertEquals("FlatChannel", info.channelName)
        assertEquals("FlatTitle", info.title)
        assertEquals("FlatThumb", info.thumbnail)
    }
}
