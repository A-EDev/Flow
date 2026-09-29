package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingAvailability
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination

internal object NotificationsIndex {
    private val page = SettingsDestination.NOTIFICATIONS

    private fun entry(
        key: String,
        title: Int,
        section: Int,
        summary: Int? = null,
        availability: SettingAvailability = SettingAvailability.Always,
    ) = SettingEntry(
        key = "notifications.$key",
        title = title,
        summary = summary,
        section = section,
        keywords = R.string.settings_keywords_notifications,
        availability = availability,
        destination = page,
    )

    private val schedule = R.string.notif_check_interval_section_header
    private val types = R.string.settings_section_notification_types

    val enabled = entry("enabled", R.string.notif_master_toggle, schedule, R.string.notif_master_toggle_subtitle)
    val interval = entry("interval", R.string.notif_check_interval, schedule)
    val newVideos = entry("new_videos", R.string.notif_type_new_videos, types, R.string.notif_type_new_videos_subtitle)
    val downloads = entry("downloads", R.string.notif_type_downloads, types, R.string.notif_type_downloads_subtitle)
    val reminders = entry("reminders", R.string.notif_type_reminders, types, R.string.notif_type_reminders_subtitle)
    val updates =
        entry(
            "updates",
            R.string.notif_type_updates,
            types,
            R.string.notif_type_updates_subtitle,
            availability = SettingAvailability.GithubOnly,
        )
    val general = entry("general", R.string.notif_type_general, types, R.string.notif_type_general_subtitle)
    val system =
        entry("system", R.string.notif_system_settings, R.string.settings_section_more, R.string.notif_system_settings_subtitle)

    val all = listOf(enabled, interval, newVideos, downloads, reminders, updates, general, system)
}
