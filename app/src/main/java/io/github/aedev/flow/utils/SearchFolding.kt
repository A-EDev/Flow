package io.github.aedev.flow.utils

import java.text.Normalizer

private val DiacriticsRegex = Regex("\\p{Mn}+")

/** Case- and accent-insensitive form of [this], so "resolucion" finds "Resolución". */
fun String.foldForSearch(): String =
    Normalizer
        .normalize(this, Normalizer.Form.NFD)
        .replace(DiacriticsRegex, "")
        .lowercase()

private val SearchWords = Regex("""\s+""")

/**
 * The items whose [text] holds every word of [query], in their order, ignoring case and accents;
 * a blank query keeps them all. "cafe tram" finds "Café, the 28 tram".
 */
fun <T> List<T>.filterBySearch(
    query: String,
    text: (T) -> String,
): List<T> {
    val words = query.foldForSearch().split(SearchWords).filter(String::isNotEmpty)
    if (words.isEmpty()) return this
    return filter { item ->
        val folded = text(item).foldForSearch()
        words.all { it in folded }
    }
}
