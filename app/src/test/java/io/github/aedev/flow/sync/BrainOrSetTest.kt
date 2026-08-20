package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.canonical.OrSet
import io.github.aedev.flow.sync.merge.BrainCrdtState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blocklists as observed-remove sets. A plain `Set<String>` can say "blocked" but has no way to say
 * "explicitly unblocked", so before this an unblock on the phone was silently un-syncable — the
 * peer's stale add just resurrected it on the next merge.
 */
class BrainOrSetTest {

    private val early = "100:0:aaa"
    private val late = "200:0:bbb"

    @Test
    fun remove_with_a_later_stamp_wins_in_either_merge_order() {
        val blockedOnA = OrSet().add("UCbad", early)
        val unblockedOnB = blockedOnA.remove("UCbad", late)

        assertEquals(emptySet<String>(), blockedOnA.merge(unblockedOnB).members())
        assertEquals(emptySet<String>(), unblockedOnB.merge(blockedOnA).members())
    }

    @Test
    fun a_later_re_add_beats_an_earlier_remove() {
        val set = OrSet().add("UCbad", early).remove("UCbad", "150:0:bbb").add("UCbad", late)
        assertTrue(set.contains("UCbad"))
    }

    @Test
    fun add_wins_on_an_exact_tie() {
        val set = OrSet().add("politics", early).remove("politics", early)
        assertTrue("add-wins is what keeps 'blocked on either device' the default", set.contains("politics"))
    }

    @Test
    fun merge_is_commutative_associative_and_idempotent() {
        val a = OrSet().add("x", early).add("y", early)
        val b = OrSet().remove("x", late).add("z", late)
        val c = OrSet().add("x", "300:0:ccc").remove("y", "300:0:ccc")

        assertEquals(a.merge(b), b.merge(a))
        assertEquals(a.merge(b).merge(c), a.merge(b.merge(c)))
        val ab = a.merge(b)
        assertEquals(ab, ab.merge(ab))
    }

    @Test
    fun sidecar_turns_a_vanished_member_into_a_remove_tombstone() {
        // Sync 1: the topic is blocked locally and has never been stamped before.
        val afterBlock = BrainCrdtState.attributeSets(
            state = BrainCrdtState(),
            blockedTopics = setOf("politics"),
            blockedChannels = emptySet(),
            preferredTopics = emptySet(),
            hlc = early,
        )
        assertEquals(setOf("politics"), afterBlock.sets.blockedTopics.members())
        assertTrue(afterBlock.sets.blockedTopics.removes.isEmpty())

        // Sync 2: the user unblocked it, so it is simply gone from the brain's plain set.
        val afterUnblock = BrainCrdtState.attributeSets(
            state = afterBlock,
            blockedTopics = emptySet(),
            blockedChannels = emptySet(),
            preferredTopics = emptySet(),
            hlc = late,
        )
        assertEquals(late, afterUnblock.sets.blockedTopics.removes["politics"])
        assertFalse(afterUnblock.sets.blockedTopics.contains("politics"))

        // Sync 3 with no local edits must not restamp anything.
        val unchanged = BrainCrdtState.attributeSets(
            state = afterUnblock,
            blockedTopics = emptySet(),
            blockedChannels = emptySet(),
            preferredTopics = emptySet(),
            hlc = "300:0:aaa",
        )
        assertEquals(afterUnblock.sets, unchanged.sets)
    }
}
