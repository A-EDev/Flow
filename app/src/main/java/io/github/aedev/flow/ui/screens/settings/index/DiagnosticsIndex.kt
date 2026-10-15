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
    val resetSession =
        entry(
            "reset_session",
            R.string.diagnostics_reset_session,
            R.string.settings_section_report,
            R.string.settings_diagnostics_reset_summary,
        )
    val clearCrashes = entry("clear_crashes", R.string.diagnostics_clear_crashes, R.string.diagnostics_tab_crashes)
    val recordSnapshots =
        entry(
            "record_snapshots",
            R.string.diagnostics_record_home_snapshots,
            R.string.settings_section_home_snapshots,
            R.string.settings_diagnostics_record_snapshots_summary,
        )
    val exportSnapshot =
        entry(
            "export_snapshot",
            R.string.diagnostics_export_home_snapshot,
            R.string.settings_section_home_snapshots,
            R.string.settings_diagnostics_export_snapshot_summary,
        )

    val all =
        listOf(
            model,
            android,
            appVersion,
            copy,
            share,
            resetSession,
            recordSnapshots,
            exportSnapshot,
        )
}
