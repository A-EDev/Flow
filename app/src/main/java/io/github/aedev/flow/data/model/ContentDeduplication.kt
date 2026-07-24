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
