package io.github.aedev.flow.data.local

/** The words a history entry is matched on, so "World news" and "world news " are one entry. */
internal fun String.searchHistoryKey(): String = trim().lowercase()

/** This search at the top, replacing any earlier entry for the same words: the latest filters win. */
internal fun List<SearchHistoryItem>.withSearch(
    query: String,
    type: SearchType,
    filters: SearchFilter?,
    maxSize: Int,
    now: Long,
): List<SearchHistoryItem> {
    val key = query.searchHistoryKey()
    val entry = SearchHistoryItem(query = query, timestamp = now, type = type, filters = filters)
    return (listOf(entry) + filterNot { it.query.searchHistoryKey() == key }).take(maxSize)
}

/**
 * Gson reads an enum constant this build does not know, from a newer backup, as null even into a
 * non-null field. Those fall back to their defaults instead of failing the first `when` that reads them.
 */
internal fun SearchHistoryItem.sanitized(): SearchHistoryItem = copy(type = type.orDefault(SearchType.TEXT), filters = filters?.sanitized())

internal fun SearchFilter.sanitized(): SearchFilter =
    SearchFilter(
        contentType = contentType.orDefault(ContentType.ALL),
        duration = duration.orDefault(Duration.ANY),
        uploadDate = uploadDate.orDefault(UploadDate.ANY),
        sortType = sortType.orDefault(SortType.RELEVANCE),
        features = features.orDefault(emptySet()).filterNotNull().toSet(),
    )

/** The stored filters this device can still run: a Shorts search falls back to everything once Shorts are hidden. */
fun SearchFilter.availableWith(shortsEnabled: Boolean): SearchFilter =
    if (!shortsEnabled && contentType == ContentType.SHORTS) copy(contentType = ContentType.ALL) else this

private fun <T : Any> T?.orDefault(default: T): T = this ?: default
