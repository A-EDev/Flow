package io.github.aedev.flow.sync.apply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncChangeLedgerTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun the_first_run_stamps_everything_zero_and_deletes_nothing() {
        val ledger = CollectionLedger().observe(mapOf("a" to 1, "b" to 2), nowMs = 500)

        assertTrue(ledger.initialized)
        assertEquals(0L, ledger.stampOf("a"))
        assertTrue(ledger.tombstones().isEmpty())
    }

    @Test
    fun only_records_that_changed_since_the_last_sync_get_a_new_stamp() {
        val synced = CollectionLedger().observe(mapOf("same" to 1, "edited" to 2), nowMs = 100)

        val next = synced.observe(mapOf("same" to 1, "edited" to 3, "added" to 4), nowMs = 900)

        assertEquals(0L, next.stampOf("same"))
        assertEquals(900L, next.stampOf("edited"))
        assertEquals(900L, next.stampOf("added"))
    }

    @Test
    fun a_record_missing_since_the_last_sync_becomes_a_tombstone_stamped_now() {
        val synced = CollectionLedger().observe(mapOf("kept" to 1, "gone" to 1), nowMs = 100, templates = mapOf("gone" to "{}"))

        val next = synced.observe(mapOf("kept" to 1), nowMs = 900)

        val tombstone = next.tombstones().getValue("gone")
        assertEquals(900L, tombstone.atMs)
        assertEquals("the template survives for building the tombstone", "{}", tombstone.record)
        assertFalse(tombstone.fromPeer)
        assertEquals("observing again changes nothing", next, next.observe(mapOf("kept" to 1), nowMs = 950))
    }

    @Test
    fun tombstones_expire_after_a_year() {
        val deleted = CollectionLedger().observe(mapOf("gone" to 1), nowMs = 0).observe(emptyMap(), nowMs = day)

        assertTrue(deleted.observe(emptyMap(), nowMs = day + TOMBSTONE_TTL_MS).tombstones().containsKey("gone"))
        assertFalse(deleted.observe(emptyMap(), nowMs = day + TOMBSTONE_TTL_MS + 1).tombstones().containsKey("gone"))
    }

    @Test
    fun a_record_added_back_after_a_deletion_is_live_with_a_new_stamp() {
        val deleted = CollectionLedger().observe(mapOf("v" to 1), nowMs = 0).observe(emptyMap(), nowMs = 100)

        val restored = deleted.observe(mapOf("v" to 1), nowMs = 200)

        assertTrue(restored.tombstones().isEmpty())
        assertEquals(200L, restored.stampOf("v"))
    }

    @Test
    fun settling_adopts_the_winning_stamps_and_keeps_every_deletion() {
        val observed =
            CollectionLedger()
                .observe(mapOf("mine" to 1, "edited" to 1, "deleted-here" to 1), nowMs = 0)
                .observe(mapOf("mine" to 1, "edited" to 2), nowMs = 500)

        val settled =
            observed.settle(
                current = mapOf("mine" to 1, "edited" to 2, "from-peer" to 7),
                stamps = mapOf("edited" to 800L),
                peerTombstones = mapOf("deleted-there" to PeerTombstone(600, "{\"x\":1}")),
            )

        assertEquals(0L, settled.stampOf("mine"))
        assertEquals("the peer's newer edit won, so its stamp is the one to resend", 800L, settled.stampOf("edited"))
        assertEquals(0L, settled.stampOf("from-peer"))
        assertEquals(500L, settled.tombstones().getValue("deleted-here").atMs)
        val relayed = settled.tombstones().getValue("deleted-there")
        assertTrue(relayed.fromPeer)
        assertEquals("{\"x\":1}", relayed.record)
    }

    @Test
    fun a_peer_tombstone_for_a_record_that_survived_the_merge_is_not_kept() {
        val settled =
            CollectionLedger(initialized = true).settle(
                current = mapOf("v" to 1),
                stamps = emptyMap(),
                peerTombstones = mapOf("v" to PeerTombstone(10, "{}")),
            )

        assertTrue(settled.tombstones().isEmpty())
    }
}
