package io.github.aedev.flow.data.model

internal fun <T> Iterable<T>.distinctByNonBlankKey(
    keySelector: (T) -> String
): List<T> {
    val seenKeys = HashSet<String>()
    return filter { item ->
        val key = keySelector(item)
        key.isNotBlank() && seenKeys.add(key)
    }
}

internal fun <T> Iterable<T>.mergeDistinctByNonBlankKey(
    incoming: Iterable<T>,
    keySelector: (T) -> String
): List<T> = (this + incoming).distinctByNonBlankKey(keySelector)

internal fun <T> List<T>.distinctByNonBlankKeyOrSelf(
    keySelector: (T) -> String
): List<T> {
    val distinctItems = distinctByNonBlankKey(keySelector)
    return if (distinctItems.size == size) this else distinctItems
}

internal class DistinctKeyTracker {
    private val seenKeys = HashSet<String>()

    fun <T> filter(
        items: Iterable<T>,
        keySelector: (T) -> String
    ): List<T> = synchronized(seenKeys) {
        items.filter { item ->
            val key = keySelector(item)
            key.isNotBlank() && seenKeys.add(key)
        }
    }
}
