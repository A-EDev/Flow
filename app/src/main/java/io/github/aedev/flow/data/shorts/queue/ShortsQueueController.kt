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
 * tapped, de-duplicating appends, and handing over to [continuation] when [primary] runs dry so a
 * shelf of twenty never dead-ends at twenty.
 */
class ShortsQueueController(
    private val primary: ShortsQueueLoader,
    private val continuation: ShortsQueueLoader? = null,
) {
    private val _items = MutableStateFlow<List<ShortVideo>>(emptyList())
    val items: StateFlow<List<ShortVideo>> = _items.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    /** Every id ever admitted, so an append can never re-add one — including a rejected short. */
    private val seenIds = mutableSetOf<String>()

    private var cursor: String? = null
    private var primaryExhausted = false
    private var continuationStarted = false
    private var continuationExhausted = false

    /** False only once every loader is done, which is what stops the pager asking for more. */
    val hasMore: Boolean
        get() = !primaryExhausted || (continuation != null && !continuationExhausted)

    val currentItem: ShortVideo?
        get() = _items.value.getOrNull(_currentIndex.value)

    /**
     * Loads the first page and opens on [startVideoId] when the source names one.
     *
     * The list keeps the order the user was just looking at and the position moves instead — so
     * tapping the third item of a shelf can still be swiped *backwards* through the first two.
     */
    suspend fun loadInitial(startVideoId: String?) {
        val page = primary.initial()
        primaryExhausted = page.exhausted
        cursor = page.cursor

        val items = page.items.distinctById()
        seenIds += items.map { it.id }
        _items.value = items
        _currentIndex.value =
            startVideoId
                ?.takeIf { it.isNotBlank() }
                ?.let { id -> items.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
                ?: 0
    }

    /**
     * Appends the next page, switching from [primary] to [continuation] the first time [primary]
     * reports itself exhausted.
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

        val wasCurrent = removedIndex == _currentIndex.value
        _currentIndex.value = _currentIndex.value.coerceAtMost(updated.lastIndex)
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
        if (discovery.isEmpty()) return ShortsQueueChange.None
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
        if (!primaryExhausted) {
            val page = primary.more(cursor)
            cursor = page.cursor
            primaryExhausted = page.exhausted
            return page
        }

        val next = continuation ?: return null
        if (continuationExhausted) return null

        // The continuation starts its own paging, so its first call is initial() and the primary's
        // spent cursor is discarded rather than handed to a loader that cannot read it.
        val page = if (!continuationStarted) {
            continuationStarted = true
            cursor = null
            next.initial()
        } else {
            next.more(cursor)
        }
        cursor = page.cursor
        continuationExhausted = page.exhausted
        return page
    }

    private companion object {
        const val MAX_DEDUPE_ATTEMPTS = 3
    }
}

private fun List<ShortVideo>.distinctById(): List<ShortVideo> = distinctBy { it.id }
