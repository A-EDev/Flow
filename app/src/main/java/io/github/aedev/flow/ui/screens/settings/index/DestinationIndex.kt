package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination

/** One entry per settings page, so a page is found by its own name as well as by its options. */
internal object DestinationIndex {
    private const val KEY_PREFIX = "dest."

    fun entry(destination: SettingsDestination): SettingEntry =
        SettingEntry(
            key = KEY_PREFIX + destination.id,
            title = destination.titleRes,
            destination = destination.parent ?: SettingsDestination.HOME,
            summary = summaryOf(destination),
        )

    /** The page a destination entry stands for, or null for an option entry. */
    fun destinationOf(entry: SettingEntry): SettingsDestination? =
        entry.key
            .takeIf { it.startsWith(KEY_PREFIX) }
            ?.removePrefix(KEY_PREFIX)
            ?.let(SettingsDestination::fromId)

    /** The editor is left out: it opens one theme, so it has no page of its own to find. */
    val all: List<SettingEntry> =
        SettingsDestination.entries
            .filter { it != SettingsDestination.HOME && it != SettingsDestination.CUSTOM_THEME_EDIT }
            .map(::entry)

    private fun summaryOf(destination: SettingsDestination): Int? =
        when (destination) {
            SettingsDestination.HOME -> null
            SettingsDestination.TASTE -> R.string.taste_summary
            SettingsDestination.HIDDEN_CONTENT -> R.string.taste_hidden_summary
            SettingsDestination.APPEARANCE -> R.string.settings_appearance_summary
            SettingsDestination.THEME -> R.string.settings_theme_summary
            SettingsDestination.CUSTOM_THEME -> R.string.settings_custom_theme_summary
            SettingsDestination.CUSTOM_THEME_EDIT -> null
            SettingsDestination.NAVIGATION_BAR -> R.string.settings_navigation_bar_summary
            SettingsDestination.DATE_TIME -> R.string.settings_item_datetime_subtitle
            SettingsDestination.PLAYER_APPEARANCE -> R.string.settings_player_appearance_summary
            SettingsDestination.LANGUAGE_REGION -> R.string.settings_language_region_summary
            SettingsDestination.PLAYBACK -> R.string.settings_playback_summary
            SettingsDestination.BUFFER -> R.string.settings_item_buffer_subtitle
            SettingsDestination.QUALITY -> R.string.settings_quality_summary
            SettingsDestination.CONTENT -> R.string.settings_content_summary
            SettingsDestination.TOPICS -> R.string.settings_topics_summary
            SettingsDestination.INTEGRATIONS -> R.string.settings_integrations_summary
            SettingsDestination.BACKUP -> R.string.settings_backup_summary
            SettingsDestination.SYNC -> R.string.sync_devices_subtitle
            SettingsDestination.HISTORY -> R.string.settings_history_summary
            SettingsDestination.DOWNLOADS -> R.string.settings_downloads_summary
            SettingsDestination.NOTIFICATIONS -> R.string.settings_item_notifications_subtitle
            SettingsDestination.NETWORK -> R.string.settings_network_summary
            SettingsDestination.WELLBEING -> R.string.settings_wellbeing_summary
            SettingsDestination.ABOUT -> R.string.settings_about_summary
            SettingsDestination.DIAGNOSTICS -> R.string.settings_item_diagnostics_subtitle
        }
}
