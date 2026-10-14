package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.utils.foldForSearch

/** A [SettingEntry] with its strings resolved for the current locale, ready to be searched. */
internal data class SearchableSetting(
    val entry: SettingEntry,
    val title: String,
    val summary: String?,
    val keywords: List<String>,
    val breadcrumb: String,
) {
    internal val foldedTitle = title.foldForSearch()
    internal val foldedSummary = summary?.foldForSearch()
    internal val foldedKeywords = keywords.map { it.foldForSearch() }
    internal val foldedBreadcrumb = breadcrumb.foldForSearch()
}

/**
 * Ranks settings against a query. Every word of the query has to match somewhere; a match in the
 * title outranks one in a keyword, which outranks the summary, which outranks the page name.
 */
internal object SettingsSearch {
    private const val TITLE_PREFIX = 100
    private const val TITLE_WORD_PREFIX = 80
    private const val TITLE_CONTAINS = 60
    private const val KEYWORD_PREFIX = 55
    private const val KEYWORD_CONTAINS = 40
    private const val SUMMARY_CONTAINS = 25
    private const val BREADCRUMB_CONTAINS = 15

    private val WordSplit = Regex("[\\s\\p{Punct}]+")

    fun search(
        query: String,
        settings: List<SearchableSetting>,
    ): List<SearchableSetting> {
        val words = query.foldForSearch().split(WordSplit).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return settings
            .mapIndexedNotNull { index, setting ->
                val score = words.sumOf { word -> wordScore(word, setting) ?: return@mapIndexedNotNull null }
                Triple(setting, score, index)
            }.sortedWith(compareByDescending<Triple<SearchableSetting, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    private fun wordScore(
        word: String,
        setting: SearchableSetting,
    ): Int? {
        val title = setting.foldedTitle
        return when {
            title.startsWith(word) -> TITLE_PREFIX
            title.split(WordSplit).any { it.startsWith(word) } -> TITLE_WORD_PREFIX
            title.contains(word) -> TITLE_CONTAINS
            setting.foldedKeywords.any { it.startsWith(word) } -> KEYWORD_PREFIX
            setting.foldedKeywords.any { it.contains(word) } -> KEYWORD_CONTAINS
            setting.foldedSummary?.contains(word) == true -> SUMMARY_CONTAINS
            setting.foldedBreadcrumb.contains(word) -> BREADCRUMB_CONTAINS
            else -> null
        }
    }
}
