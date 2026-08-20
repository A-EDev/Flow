package io.github.aedev.flow.data.shorts.queue

import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.shorts.mergeDiscoveryCandidates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What a mutation did, so the caller can run the follow-up it owns.
 */
enum class ShortsQueueChange {
    /** Items or order changed, but the short at the current position did not. */
    ListOnly,

    /** A different short now occupies the current position — the pool has to be re-pointed. */
    CurrentItemChanged,

    /** Nothing changed. */
    None,
}

/**
 * Owns a Shorts queue: its order, the current position, and paging.
 *
 * The behaviour every entry point shares lives here exactly once — opening on the short the user
 * tapped, de-duplicating appends, and handing over to the next of [continuations] when [primary]
 * runs dry so a shelf of twenty never dead-ends at twenty.
 */
class ShortsQueueController(
    private val primary: ShortsQueueLoader,
    /**
     * Sources to fall through to, in order, as each one before them runs out.
     *
     * A list rather than a single loader because the subscriptions queue has two fall-backs of very
     * different character: the subscribed channels' own older reels first, and only then anything
     * algorithmic.
     */
    private val continuations: List<ShortsQueueLoader> = emptyList(),
    /**
     * Whether late-arriving algorithmic discovery may be interleaved into this queue.
     *
     * Only true when [primary] *is* the algorithmic feed. Saved Shorts is a deliberate collection
     * and a channel tab is that channel's work — injecting recommendations into either
     * misrepresents it, and background discovery finishes on its own schedule, so without this a
     * discovery pass started on the Shorts tab lands in whatever queue happens to be open.
     */
    private val acceptsDiscovery: Boolean = false,
) {
    private val _items = MutableStateFlow<List<ShortVideo>>(emptyList())
    val items: StateFlow<List<ShortVideo>> = _items.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    /** Every id ever admitted, so an append can never re-add one — including a rejected short. */
    private val seenIds = mutableSetOf<String>()

    /** [primary] first, then each continuation; the queue walks this in order and never back. */
    private val legs: List<ShortsQueueLoader> = listOf(primary) + continuations

    private var cursor: String? = null
    private var legIndex = 0
    private var legStarted = false

    /** False only once every loader is done, which is what stops the pager asking for more. */
    val hasMore: Boolean
        get() = legIndex < legs.size

    val currentItem: ShortVideo?
        get() = _items.value.getOrNull(_currentIndex.value)

    /**
     * Loads the first page and opens on [startVideoId] when the source names one.
     *
     * The list keeps the order the user was just looking at and the position moves instead — so
     * tapping the third item of a shelf can still be swiped *backwards* through the first two.
     */
    suspend fun loadInitial(startVideoId: String?) {
        legStarted = true
        val page = primary.initial()
        consume(page)

        val items = page.items.distinctById()
        seenIds += items.map { it.id }
        _items.value = items

        val anchor = startVideoId?.takeIf { it.isNotBlank() }
        _currentIndex.value = anchor?.let(::indexOf) ?: 0
        if (anchor != null && indexOf(anchor) == null) pageToAnchor(anchor)
    }

    /**
     * Pages forward looking for a start anchor the first page did not contain.
     *
     * A paginated source can hold the tapped short well past page one — a channel's Shorts grid
     * pages as the user scrolls, so a tap forty items down resolves to nothing on the first page.
     * Without this the queue silently opens at position 0 and plays a short the user did not pick.
     *
     * Bounded: if it is not found, opening at the top is still better than paging a channel forever.
     */
    private suspend fun pageToAnchor(anchor: String) {
        repeat(MAX_ANCHOR_PAGES) {
            if (!hasMore) return
            val before = _items.value.size
            loadMore()
            if (_items.value.size == before) return
            indexOf(anchor)?.let { found ->
                _currentIndex.value = found
                return
            }
        }
    }

    private fun indexOf(id: String): Int? = _items.value.indexOfFirst { it.id == id }.takeIf { it >= 0 }

    /**
     * Appends the next page, moving on to the next of [continuations] each time the leg it is
     * walking reports itself exhausted.
     *
     * Re-entrant calls are dropped: the pager asks as the user approaches the end, from more than
     * one place.
     */
    suspend fun loadMore() {
        if (!hasMore || _isLoadingMore.value) return
        _isLoadingMore.value = true
        try {
            repeat(MAX_DEDUPE_ATTEMPTS) {
                val page = fetchNext() ?: return
                val fresh = page.items.filter { it.id !in seenIds }.distinctById()
                if (fresh.isNotEmpty()) {
                    seenIds += fresh.map { it.id }
                    _items.value = _items.value + fresh
                    return
                }
                if (!hasMore) return
            }
        } finally {
            _isLoadingMore.value = false
        }
    }

    fun setCurrentIndex(index: Int) {
        if (index < 0 || index >= _items.value.size) return
        _currentIndex.value = index
    }

    /**
     * Drops a short — "Not interested".
     *
     * Reports [ShortsQueueChange.CurrentItemChanged] when the removed short was the one on screen:
     * the index stays put while a different short slides into it, and the player pool has to be told
     * or it keeps playing the one that was just rejected.
     */
    fun remove(id: String): ShortsQueueChange {
        val current = _items.value
        val removedIndex = current.indexOfFirst { it.id == id }
        if (removedIndex < 0) return ShortsQueueChange.None

        val updated = current.filterNot { it.id == id }
        // Deliberately kept in seenIds so a rejected short cannot come back on the next append.
        _items.value = updated

        if (updated.isEmpty()) {
            _currentIndex.value = 0
            return ShortsQueueChange.CurrentItemChanged
        }

        val position = _currentIndex.value
        val wasCurrent = removedIndex == position
        // Removing something above the cursor shifts the whole list under it; the index has to move
        // with it or the user is silently pushed onto the next short.
        val shifted = if (removedIndex < position) position - 1 else position
        _currentIndex.value = shifted.coerceIn(0, updated.lastIndex)
        return if (wasCurrent) ShortsQueueChange.CurrentItemChanged else ShortsQueueChange.ListOnly
    }

    /** Replaces items in place with enriched copies. Order and position are untouched. */
    fun applyEnrichment(enriched: List<ShortVideo>): ShortsQueueChange {
        if (enriched.isEmpty()) return ShortsQueueChange.None
        val current = _items.value
        if (current.isEmpty()) return ShortsQueueChange.None

        val byId = enriched.associateBy { it.id }
        val updated = current.map { existing -> byId[existing.id] ?: existing }
        if (updated == current) return ShortsQueueChange.None
        _items.value = updated
        return ShortsQueueChange.ListOnly
    }

    /**
     * Interleaves late-arriving discovery items after the current position, reusing the existing
     * ordering helper so this behaves exactly as the pre-queue feed did.
     */
    fun mergeDiscovery(discovery: List<ShortVideo>): ShortsQueueChange {
        if (!acceptsDiscovery || discovery.isEmpty()) return ShortsQueueChange.None
        val current = _items.value
        val merged =
            mergeDiscoveryCandidates(
                current = current,
                discovery = discovery.filter { it.id !in seenIds },
                currentIndex = _currentIndex.value,
                id = { it.id },
            )
        if (merged === current) return ShortsQueueChange.None
        seenIds += merged.map { it.id }
        _items.value = merged
        return ShortsQueueChange.ListOnly
    }

    private suspend fun fetchNext(): ShortsQueuePage? {
        val leg = legs.getOrNull(legIndex) ?: return null
        val page =
            if (legStarted) {
                leg.more(cursor)
            } else {
                legStarted = true
                leg.initial()
            }
        consume(page)
        return page
    }

    private fun consume(page: ShortsQueuePage) {
        cursor = page.cursor
        if (!page.exhausted) return
        legIndex++
        legStarted = false
        cursor = null
    }

    private companion object {
        const val MAX_DEDUPE_ATTEMPTS = 3
        const val MAX_ANCHOR_PAGES = 3
    }
}

private fun List<ShortVideo>.distinctById(): List<ShortVideo> = distinctBy { it.id }
