package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination

internal object HistoryIndex {
    private val page = SettingsDestination.HISTORY

    private fun entry(
        key: String,
        title: Int,
        summary: Int? = null,
        revealVia: String? = null,
    ) = SettingEntry(
        key = "history.$key",
        title = title,
        summary = summary,
        section = R.string.search_history_title,
        keywords = R.string.settings_keywords_history,
        revealVia = revealVia,
        destination = page,
    )

    val save = entry("save", R.string.save_search_history_title, R.string.save_searches_subtitle)
    val suggestions = entry("suggestions", R.string.search_suggestions_title, R.string.show_suggestions_subtitle)
    val maxSize = entry("max_size", R.string.max_history_size_title)
    val autoDelete = entry("auto_delete", R.string.auto_delete_history_title)
    val retention = entry("retention", R.string.retention_period_title, revealVia = "history.auto_delete")
    val clear = entry("clear", R.string.clear_history_item_title, R.string.remove_all_queries)

    val all = listOf(save, suggestions, maxSize, autoDelete, retention, clear)
}
