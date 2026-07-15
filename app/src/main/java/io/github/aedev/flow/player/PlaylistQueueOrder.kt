package io.github.aedev.flow.player

import kotlin.random.Random

internal data class ReorderedQueue<T>(
    val items: List<T>,
    val currentIndex: Int,
)

internal object PlaylistQueueOrder {
    fun nextIndex(itemCount: Int, currentIndex: Int, loopEnabled: Boolean): Int? = when {
        currentIndex < itemCount - 1 -> currentIndex + 1
        loopEnabled && itemCount > 0 -> 0
        else -> null
    }

    fun <T> shuffleFromCurrent(
        items: List<T>,
        currentIndex: Int,
        random: Random = Random.Default,
    ): ReorderedQueue<T> {
        val currentItem = items.getOrNull(currentIndex) ?: return ReorderedQueue(items, currentIndex)
        val remainingItems = items.filterIndexed { index, _ -> index != currentIndex }.shuffled(random)
        return ReorderedQueue(listOf(currentItem) + remainingItems, currentIndex = 0)
    }

    fun <T, K> restoreOriginal(
        original: List<T>,
        currentItem: T?,
        keySelector: (T) -> K,
    ): ReorderedQueue<T> {
        if (currentItem == null) return ReorderedQueue(original, currentIndex = -1)
        val currentKey = keySelector(currentItem)
        val restoredIndex = original.indexOfFirst { keySelector(it) == currentKey }.coerceAtLeast(0)
        return ReorderedQueue(original, restoredIndex)
    }
}
