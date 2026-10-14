package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsTabs

internal object TopicsIndex {
    private val page = SettingsDestination.TOPICS

    val addInterest =
        SettingEntry(
            key = "topics.add_interest",
            title = R.string.ui_custom_interest_title,
            summary = R.string.ui_custom_interest_hint,
            keywords = R.string.settings_keywords_topics,
            tab = SettingsTabs.TOPICS_INTERESTS,
            destination = page,
        )
    val block =
        SettingEntry(
            key = "topics.block",
            title = R.string.block_topic_title,
            summary = R.string.hidden_content_desc,
            keywords = R.string.settings_keywords_topics,
            tab = SettingsTabs.TOPICS_BLOCKED,
            destination = page,
        )
    val quickAdd =
        SettingEntry(
            key = "topics.quick_add",
            title = R.string.quick_add,
            tab = SettingsTabs.TOPICS_BLOCKED,
            section = R.string.settings_topics_tab_blocked,
            destination = page,
        )

    val all = listOf(addInterest, block, quickAdd)
}
