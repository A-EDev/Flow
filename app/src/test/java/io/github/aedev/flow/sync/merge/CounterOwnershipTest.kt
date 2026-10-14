package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.sync.canonical.BrainCounters
import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalMusicAffinity
import io.github.aedev.flow.sync.canonical.CanonicalMusicBrain
import io.github.aedev.flow.sync.canonical.GCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CounterOwnershipTest {
    private val own = setOf("phone123", "phone123-full-uuid")
    private val sender = setOf("desk-uuid", "deskuuid")

    @Test
    fun the_senders_correction_of_its_own_entry_replaces_the_inflated_copy() {
        // A desktop build without the attribution fix inflated its own entry to 900; the fixed
        // build corrects it once to 300. A plain max merge would keep 900 here forever.
        val local = GCounter(mapOf("phone123" to 50L, "desk-uuid" to 900L, "tablet" to 10L))
        val remote = GCounter(mapOf("phone123" to 70L, "desk-uuid" to 300L, "tablet" to 12L))

        val settled = CounterOwnership.reconcile(local.merge(remote), local, listOf(remote), own, sender)

        assertEquals("the sender owns its entry", 300L, settled.perDevice["desk-uuid"])
        assertEquals("this device owns its entry; a peer's copy is ignored", 50L, settled.perDevice["phone123"])
        assertEquals("anyone else still merges by max", 12L, settled.perDevice["tablet"])
    }

    @Test
    fun an_own_entry_this_device_no_longer_has_is_not_brought_back_by_a_peer() {
        val local = GCounter(mapOf("desk-uuid" to 5L))
        val remote = GCounter(mapOf("phone123-full-uuid" to 40L))

        val settled = CounterOwnership.reconcile(local.merge(remote), local, listOf(remote), own, sender)

        assertFalse("phone123-full-uuid" in settled.perDevice)
    }

    @Test
    fun a_sender_that_does_not_report_an_entry_leaves_the_local_copy_alone() {
        val local = GCounter(mapOf("desk-uuid" to 5L))

        val settled = CounterOwnership.reconcile(local, local, listOf(GCounter()), own, sender)

        assertEquals(5L, settled.perDevice["desk-uuid"])
    }

    @Test
    fun brain_counters_and_word_frequencies_follow_the_same_rules() {
        val local =
            CanonicalBrain(
                deviceId = "phone123",
                counters = BrainCounters(idfTotalDocuments = GCounter(mapOf("phone123" to 20L, "desk-uuid" to 800L))),
                idfWordFrequency = mapOf("kotlin" to GCounter(mapOf("desk-uuid" to 90L))),
            )
        val remote =
            CanonicalBrain(
                deviceId = "desk-uuid",
                counters = BrainCounters(idfTotalDocuments = GCounter(mapOf("phone123" to 25L, "desk-uuid" to 200L))),
                idfWordFrequency = mapOf("kotlin" to GCounter(mapOf("desk-uuid" to 30L))),
            )

        val settled = CounterOwnership.brain(BrainMerger.merge(local, remote), local, listOf(remote), own, sender)

        assertEquals(220L, settled.counters.idfTotalDocuments.sum())
        assertEquals(30L, settled.idfWordFrequency.getValue("kotlin").sum())
    }

    @Test
    fun music_plays_follow_the_same_rules() {
        val local =
            CanonicalMusicBrain(
                totalPlays = GCounter(mapOf("phone123" to 4L, "desk-uuid" to 60L)),
                artistAffinity = mapOf("UCx" to CanonicalMusicAffinity(plays = GCounter(mapOf("desk-uuid" to 60L)))),
            )
        val remote =
            CanonicalMusicBrain(
                totalPlays = GCounter(mapOf("phone123" to 9L, "desk-uuid" to 20L)),
                artistAffinity = mapOf("UCx" to CanonicalMusicAffinity(plays = GCounter(mapOf("desk-uuid" to 20L)))),
            )

        val settled = CounterOwnership.music(MusicBrainMerger.merge(local, remote), local, remote, own, sender)

        assertEquals(mapOf("phone123" to 4L, "desk-uuid" to 20L), settled.totalPlays.perDevice)
        assertEquals(
            20L,
            settled.artistAffinity
                .getValue("UCx")
                .plays
                .sum(),
        )
    }
}
