package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.data.backup.ExportKind
import io.github.aedev.flow.data.backup.ImportKind
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsTabs

internal object BackupIndex {
    private val page = SettingsDestination.BACKUP

    fun exportEntry(kind: ExportKind) =
        SettingEntry(
            key = "backup.export.${kind.name.lowercase()}",
            title = kind.titleRes,
            summary = kind.descriptionRes,
            keywords = R.string.settings_keywords_backup,
            section = R.string.settings_backup_tab_export,
            tab = SettingsTabs.BACKUP_EXPORT,
            destination = page,
        )

    fun importEntry(kind: ImportKind) =
        SettingEntry(
            key = "backup.import.${kind.name.lowercase()}",
            title = kind.titleRes,
            summary = kind.descriptionRes,
            keywords = R.string.settings_keywords_backup,
            section = R.string.settings_backup_tab_import,
            tab = SettingsTabs.BACKUP_IMPORT,
            destination = page,
        )

    private fun auto(
        key: String,
        title: Int,
        summary: Int? = null,
    ) = SettingEntry(
        key = "backup.auto.$key",
        title = title,
        summary = summary,
        section = R.string.settings_backup_tab_auto,
        tab = SettingsTabs.BACKUP_AUTO,
        destination = page,
    )

    val schedule = auto("schedule", R.string.auto_backup_schedule_section, R.string.auto_backup_subtitle)
    val type = auto("type", R.string.auto_backup_type_section)
    val folder = auto("folder", R.string.auto_backup_folder_label)
    val lastRun = auto("last_run", R.string.auto_backup_last_run_label)
    val backUpNow = auto("now", R.string.auto_backup_run_now)

    val all: List<SettingEntry> =
        ExportKind.entries.map(::exportEntry) + ImportKind.entries.map(::importEntry) + listOf(schedule, type, folder, lastRun, backUpNow)
}
