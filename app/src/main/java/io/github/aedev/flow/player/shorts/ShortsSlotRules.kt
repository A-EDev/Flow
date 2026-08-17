package io.github.aedev.flow.player.shorts

/**
 * How a content index maps onto one of the pool's fixed player slots, and who is allowed to touch it.
 *
 * Pulled out of [ShortsPlayerPool] because the pool itself needs a `Context` and three real
 * ExoPlayers to exist, which puts it out of reach of unit tests — while these rules are exactly the
 * part that was getting the wrong player.
 */
internal object ShortsSlotRules {
    fun slotFor(
        index: Int,
        poolSize: Int,
    ): Int = index % poolSize

    /**
     * Whether a page may bind its surface to the slot it maps onto.
     *
     * An unclaimed slot is fair game: the surface has to be attached before media loads. A slot
     * claimed by a *different* index is not, which is what used to give a page mid-fling the short
     * that was still playing several pages back.
     */
    fun canAttach(
        ownerIndex: Int?,
        requestedIndex: Int,
    ): Boolean = ownerIndex == null || ownerIndex == requestedIndex

    /** Whether the slot actually holds this index's media, i.e. commands and state reads are safe. */
    fun isOwnedBy(
        ownerIndex: Int?,
        requestedIndex: Int,
    ): Boolean = ownerIndex != null && ownerIndex == requestedIndex

    /**
     * Whether a slot is far enough from the current page to be torn down. Neighbours are kept so a
     * swipe either way resumes instantly.
     */
    fun shouldRelease(
        ownerIndex: Int?,
        currentIndex: Int,
    ): Boolean = ownerIndex != null && kotlin.math.abs(ownerIndex - currentIndex) > 1
}
