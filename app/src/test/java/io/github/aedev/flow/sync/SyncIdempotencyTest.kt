package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.canonical.CanonicalPlaylist
import io.github.aedev.flow.sync.canonical.CanonicalPlaylistItem
import io.github.aedev.flow.sync.merge.PlaylistMerger
import io.github.aedev.flow.sync.protocol.SyncSerialization
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncIdempotencyTest {

    private fun item(id: String, pos: Long) = CanonicalPlaylistItem(
        videoId = id, position = pos, addedAtMs = 1, title = id, hlc = "100:0:remote",
    )

    private val remote = listOf(
        CanonicalPlaylist(
            syncId = "p1", title = "Gym", updatedHlc = "100:0:remote",
            items = listOf(item("v1", 0), item("v2", 1), item("v3", 2)),
        ),
        CanonicalPlaylist(syncId = "p2", title = "Chill", updatedHlc = "100:0:remote", items = listOf(item("v9", 0))),
    )

    @Test
    fun crdt_merge_is_stable_under_repeated_application() {
        var local = listOf(
            CanonicalPlaylist(syncId = "p1", title = "Gym (local)", updatedHlc = "90:0:local", items = listOf(item("v1", 0))),
        )
        val once = PlaylistMerger.merge(local, remote)
        var acc = once
        repeat(3) { acc = PlaylistMerger.merge(acc, remote) }
        assertEquals("re-merging the same remote must not change the converged state", once, acc)

        // The merged playlist holds the union of items exactly once (no duplication).
        val p1 = once.first { it.syncId == "p1" }
        assertEquals(listOf("v1", "v2", "v3"), p1.items.map { it.videoId })
    }

    @Test
    fun ledger_dedup_skips_already_applied_payloads() {
        val seen = HashSet<String>()
        var local = emptyList<CanonicalPlaylist>()
        var applies = 0

        repeat(3) {
            val wire = SyncSerialization.encodePlaylists(remote) // identical bytes + hash every round
            if (seen.add(wire.hash)) {
                local = PlaylistMerger.merge(local, SyncSerialization.decodePlaylists(wire.lines))
                applies++
            }
        }

        assertEquals("payload must be applied exactly once across 3 syncs", 1, applies)
        assertEquals(setOf("p1", "p2"), local.map { it.syncId }.toSet())
        assertEquals(3, local.first { it.syncId == "p1" }.items.size)
    }
}
