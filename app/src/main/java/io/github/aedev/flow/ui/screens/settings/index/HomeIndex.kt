package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingAvailability
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination

/** Options that live directly on the settings list rather than on a page of their own. */
internal object HomeIndex {
    private val home = SettingsDestination.HOME

    val persona =
        SettingEntry(
            key = "home.persona",
            title = R.string.flow_control_center,
            summary = R.string.neural_interest_map_subtitle,
            section = R.string.settings_flow_engine_header,
            destination = home,
        )
    val deepFlow =
        SettingEntry(
            key = "home.deep_flow",
            title = R.string.deep_flow_mode_title,
            summary = R.string.deep_flow_mode_subtitle,
            section = R.string.settings_flow_engine_header,
            destination = home,
        )
    val deepFlowDuration =
        SettingEntry(
            key = "home.deep_flow_duration",
            title = R.string.deep_flow_expire_duration_title,
            section = R.string.settings_flow_engine_header,
            destination = home,
        )
    val deepFlowHistory =
        SettingEntry(
            key = "home.deep_flow_history",
            title = R.string.deep_flow_save_history_title,
            summary = R.string.deep_flow_save_history_subtitle,
            section = R.string.settings_flow_engine_header,
            destination = home,
        )
    val checkForUpdates =
        SettingEntry(
            key = "home.updates",
            title = R.string.check_for_updates,
            summary = R.string.check_for_updates_subtitle,
            section = R.string.settings_header_about,
            destination = home,
            availability = SettingAvailability.GithubOnly,
        )
    val support =
        SettingEntry(
            key = "home.support",
            title = R.string.settings_item_support,
            summary = R.string.settings_item_support_subtitle,
            section = R.string.settings_header_about,
            destination = home,
        )

    val all = listOf(persona, deepFlow, deepFlowDuration, deepFlowHistory, checkForUpdates, support)
}
