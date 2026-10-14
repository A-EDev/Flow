package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination

internal object LanguageRegionIndex {
    private val page = SettingsDestination.LANGUAGE_REGION

    val appLanguage =
        SettingEntry(
            key = "language_region.app_language",
            title = R.string.settings_item_app_language,
            keywords = R.string.settings_keywords_language,
            destination = page,
        )
    val contentLanguage =
        SettingEntry(
            key = "language_region.content_language",
            title = R.string.music_content_language_title,
            summary = R.string.settings_content_language_summary,
            keywords = R.string.settings_keywords_language,
            destination = page,
        )
    val contentCountry =
        SettingEntry(
            key = "language_region.content_country",
            title = R.string.music_content_country_title,
            summary = R.string.settings_content_country_summary,
            keywords = R.string.settings_keywords_region,
            destination = page,
        )

    val all = listOf(appLanguage, contentLanguage, contentCountry)
}
