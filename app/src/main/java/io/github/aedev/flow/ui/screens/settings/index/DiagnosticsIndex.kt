package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination

internal object DiagnosticsIndex {
    private val page = SettingsDestination.DIAGNOSTICS

    private fun entry(
        key: String,
        title: Int,
        section: Int,
        summary: Int? = null,
    ) = SettingEntry(
        key = "diagnostics.$key",
        title = title,
        summary = summary,
        section = section,
        keywords = R.string.settings_keywords_diagnostics,
        destination = page,
    )

    val model = entry("model", R.string.settings_diagnostics_model, R.string.settings_section_this_device)
    val android = entry("android", R.string.settings_diagnostics_android, R.string.settings_section_this_device)
    val appVersion = entry("app_version", R.string.settings_diagnostics_app_version, R.string.settings_section_this_device)
    val copy = entry("copy", R.string.diagnostics_copy, R.string.settings_section_report, R.string.settings_diagnostics_copy_summary)
    val share = entry("share", R.string.diagnostics_share, R.string.settings_section_report, R.string.settings_diagnostics_share_summary)
    val engineInteractions = entry("engine.interactions", R.string.metric_interactions, R.string.diagnostics_engine_header)
    val engineTopics = entry("engine.topics", R.string.metric_topics, R.string.diagnostics_engine_header)
    val engineChannels = entry("engine.channels", R.string.metric_channels, R.string.diagnostics_engine_header)
    val engineHistory = entry("engine.history", R.string.metric_history, R.string.diagnostics_engine_header)
    val engineFeedMemory = entry("engine.feed_memory", R.string.metric_feed_memory, R.string.diagnostics_engine_header)
    val engineSuppressed = entry("engine.suppressed", R.string.metric_suppressed, R.string.diagnostics_engine_header)
    val engineShortsSeen = entry("engine.shorts_seen", R.string.discovery_shorts_seen, R.string.diagnostics_engine_header)
    val engineQueries = entry("engine.queries", R.string.discovery_query_memory, R.string.diagnostics_engine_header)
    val resetSession =
        entry(
            "reset_session",
            R.string.diagnostics_reset_session,
            R.string.settings_section_report,
            R.string.settings_diagnostics_reset_summary,
        )
    val clearCrashes = entry("clear_crashes", R.string.diagnostics_clear_crashes, R.string.diagnostics_tab_crashes)

    val all =
        listOf(
            model,
            android,
            appVersion,
            copy,
            share,
            resetSession,
            engineInteractions,
            engineTopics,
            engineChannels,
            engineHistory,
            engineFeedMemory,
            engineSuppressed,
            engineShortsSeen,
            engineQueries,
        )
}
