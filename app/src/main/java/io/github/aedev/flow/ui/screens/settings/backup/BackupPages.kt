package io.github.aedev.flow.ui.screens.settings.backup

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.backup.ExportKind
import io.github.aedev.flow.data.backup.ImportKind
import io.github.aedev.flow.data.local.LocalDataManager.AutoBackupFrequency
import io.github.aedev.flow.data.local.LocalDataManager.AutoBackupType
import io.github.aedev.flow.ui.components.settings.SettingsListScope
import io.github.aedev.flow.ui.components.settings.info
import io.github.aedev.flow.ui.components.settings.nav
import io.github.aedev.flow.ui.components.settings.notice
import io.github.aedev.flow.ui.components.settings.option
import io.github.aedev.flow.ui.components.settings.toggleGroup
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import io.github.aedev.flow.ui.screens.settings.index.BackupIndex

internal fun SettingsListScope.exportPage(onExport: (ExportKind) -> Unit) {
    group(key = "backup.export.full", header = R.string.settings_backup_full) {
        nav(BackupIndex.exportEntry(ExportKind.MASTER), onClick = {
            onExport(ExportKind.MASTER)
        }, icon = Icons.Outlined.CloudUpload, showChevron = false)
    }
    group(key = "backup.export.individual", header = R.string.settings_backup_individual) {
        ExportKind.entries.filter { it != ExportKind.MASTER }.forEach { kind ->
            nav(BackupIndex.exportEntry(kind), onClick = { onExport(kind) }, showChevron = false)
        }
    }
}

internal fun SettingsListScope.importPage(onImport: (ImportKind) -> Unit) {
    ImportSections.forEachIndexed { index, (header, kinds) ->
        group(key = "backup.import.section.$index", header = header) {
            kinds.forEach { kind -> nav(BackupIndex.importEntry(kind), onClick = { onImport(kind) }, showChevron = false) }
        }
    }
}

internal class AutoBackupState(
    val frequency: AutoBackupFrequency,
    val type: AutoBackupType,
    val folderName: String?,
    val noFolderLabel: String,
    val lastRun: String?,
    val running: Boolean,
    val frequencyOptions: List<FlowToggleOption<AutoBackupFrequency>>,
    val typeLabels: Map<AutoBackupType, String>,
)

internal fun SettingsListScope.autoBackupPage(
    state: AutoBackupState,
    onFrequency: (AutoBackupFrequency) -> Unit,
    onType: (AutoBackupType) -> Unit,
    onPickFolder: () -> Unit,
    onBackUpNow: () -> Unit,
) {
    group(key = "backup.auto.schedule.group", header = R.string.auto_backup_schedule_section) {
        toggleGroup(BackupIndex.schedule, state.frequencyOptions, state.frequency, onFrequency)
    }
    group(key = BackupIndex.type.key, header = R.string.auto_backup_type_section) {
        AutoBackupType.entries.forEach { type ->
            option(
                key = "backup.auto.type.${type.name}",
                label = state.typeLabels.getValue(type),
                selected = state.type == type,
                onClick = { onType(type) },
            )
        }
    }
    group(key = "backup.auto.folder.group", header = R.string.auto_backup_folder_section) {
        nav(BackupIndex.folder, value = state.folderName ?: state.noFolderLabel, icon = Icons.Outlined.Folder, onClick = onPickFolder)
    }
    group(key = "backup.auto.status", header = R.string.auto_backup_status_section) {
        info(BackupIndex.lastRun, value = state.lastRun, icon = Icons.Outlined.History)
    }
    item(BackupIndex.backUpNow.key) {
        FilledTonalButton(
            onClick = onBackUpNow,
            enabled = state.folderName != null && !state.running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text =
                    if (state.folderName == null) {
                        stringResource(R.string.settings_backup_needs_folder)
                    } else {
                        stringResource(R.string.auto_backup_run_now)
                    },
            )
        }
    }
    notice("backup.auto.notice", text = { stringResource(R.string.settings_backup_one_copy) }, icon = Icons.Outlined.Info)
}
