package io.github.aedev.flow.player.shorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsSlotRulesTest {
    private val poolSize = 3

    @Test
    fun `three adjacent pages never share a slot`() {
        // The pager composes previous, current and next, so those three must map to distinct
        // players or two pages would fight over one.
        for (current in 1..40) {
            val slots = setOf(
                ShortsSlotRules.slotFor(current - 1, poolSize),
                ShortsSlotRules.slotFor(current, poolSize),
                ShortsSlotRules.slotFor(current + 1, poolSize),
            )
            assertEquals("indices ${current - 1}..${current + 1} collided", 3, slots.size)
        }
    }

    @Test
    fun `slots wrap by pool size`() {
        assertEquals(0, ShortsSlotRules.slotFor(0, poolSize))
        assertEquals(1, ShortsSlotRules.slotFor(1, poolSize))
        assertEquals(2, ShortsSlotRules.slotFor(2, poolSize))
        assertEquals(0, ShortsSlotRules.slotFor(3, poolSize))
    }

    // canAttach — the surface has to bind before media loads, but not to someone else's short.

    @Test
    fun `unclaimed slot can be attached`() {
        assertTrue(ShortsSlotRules.canAttach(ownerIndex = null, requestedIndex = 4))
    }

    @Test
    fun `own slot can be attached`() {
        assertTrue(ShortsSlotRules.canAttach(ownerIndex = 4, requestedIndex = 4))
    }

    @Test
    fun `slot held by another index cannot be attached`() {
        // index 3 and index 0 share slot 0. Mid-fling, page 3 must not take over the player that
        // is still showing short 0.
        assertFalse(ShortsSlotRules.canAttach(ownerIndex = 0, requestedIndex = 3))
    }

    // isOwnedBy — gates every command and state read.

    @Test
    fun `unprepared slot is not owned`() {
        assertFalse(ShortsSlotRules.isOwnedBy(ownerIndex = null, requestedIndex = 4))
    }

    @Test
    fun `slot is owned only by its exact index`() {
        assertTrue(ShortsSlotRules.isOwnedBy(ownerIndex = 4, requestedIndex = 4))
        assertFalse(ShortsSlotRules.isOwnedBy(ownerIndex = 1, requestedIndex = 4))
    }

    @Test
    fun `attachable is not the same as owned`() {
        // The distinction that fixes tap-to-pause landing on the previous short: a page may hold the
        // surface while the slot is still unclaimed, but it must not accept commands yet.
        val owner: Int? = null
        assertTrue(ShortsSlotRules.canAttach(owner, 4))
        assertFalse(ShortsSlotRules.isOwnedBy(owner, 4))
    }

    // shouldRelease — neighbours stay warm.

    @Test
    fun `current and neighbours are kept`() {
        assertFalse(ShortsSlotRules.shouldRelease(ownerIndex = 5, currentIndex = 5))
        assertFalse(ShortsSlotRules.shouldRelease(ownerIndex = 4, currentIndex = 5))
        assertFalse(ShortsSlotRules.shouldRelease(ownerIndex = 6, currentIndex = 5))
    }

    @Test
    fun `distant slots are released in both directions`() {
        assertTrue(ShortsSlotRules.shouldRelease(ownerIndex = 3, currentIndex = 5))
        assertTrue(ShortsSlotRules.shouldRelease(ownerIndex = 7, currentIndex = 5))
    }

    @Test
    fun `unclaimed slot needs no release`() {
        assertFalse(ShortsSlotRules.shouldRelease(ownerIndex = null, currentIndex = 5))
    }
}
