package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.canonical.CanonicalPlaylist
import io.github.aedev.flow.sync.canonical.CanonicalPlaylistItem
import io.github.aedev.flow.sync.merge.PlaylistMerger
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks in the duplication fix: a user-created playlist that exists on both devices but was
 * created independently (different `syncId`s) is reconciled by normalized title onto the local
 * `syncId` instead of surfacing as a second copy. YouTube-sourced and cross-bucket lists are
 * deliberately NOT title-merged.
 */
class PlaylistReconcileTest {

    private fun item(id: String, pos: Long, hlcStr: String) =
        CanonicalPlaylistItem(videoId = id, position = pos, hlc = hlcStr)

    @Test
    fun same_title_different_syncId_merges_onto_local_id() {
        val local = listOf(
            CanonicalPlaylist(
                syncId = "local-uuid", title = " My  Gym Mix ", updatedHlc = "90:0:local",
                items = listOf(item("v1", 0, "90:0:local")),
            ),
        )
        val remote = listOf(
            CanonicalPlaylist(
                syncId = "desktop-uuid", title = "my gym mix", updatedHlc = "200:0:remote",
                items = listOf(item("v2", 0, "200:0:remote"), item("v3", 1, "200:0:remote")),
            ),
        )

        val merged = PlaylistMerger.merge(local, remote)

        assertEquals("must collapse to a single playlist, not duplicate", 1, merged.size)
        val p = merged.single()
        assertEquals("keeps the local syncId so it maps back to the existing row", "local-uuid", p.syncId)
        assertEquals(setOf("v1", "v2", "v3"), p.items.map { it.videoId }.toSet())
    }

    @Test
    fun youtube_playlists_are_not_title_merged() {
        val local = listOf(
            CanonicalPlaylist(
                syncId = "a", origin = CanonicalPlaylist.ORIGIN_YOUTUBE, youtubeId = "PL_A",
                isUserCreated = false, title = "Top Hits", updatedHlc = "100:0:l",
            ),
        )
        val remote = listOf(
            CanonicalPlaylist(
                syncId = "b", origin = CanonicalPlaylist.ORIGIN_YOUTUBE, youtubeId = "PL_B",
                isUserCreated = false, title = "Top Hits", updatedHlc = "100:0:r",
            ),
        )

        val merged = PlaylistMerger.merge(local, remote)

        assertEquals("distinct YouTube ids stay distinct despite identical title", 2, merged.size)
    }

    @Test
    fun music_and_video_with_same_title_do_not_merge() {
        val local = listOf(
            CanonicalPlaylist(syncId = "a", title = "Focus", isMusic = true, updatedHlc = "100:0:l"),
        )
        val remote = listOf(
            CanonicalPlaylist(syncId = "b", title = "Focus", isMusic = false, updatedHlc = "100:0:r"),
        )

        val merged = PlaylistMerger.merge(local, remote)

        assertEquals("different music/video buckets are different lists", 2, merged.size)
    }
}
