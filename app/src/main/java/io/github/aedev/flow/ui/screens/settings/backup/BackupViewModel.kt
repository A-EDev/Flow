package io.github.aedev.flow.ui.screens.settings.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.backup.BackupCoordinator
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.data.local.LocalDataManager.AutoBackupFrequency
import io.github.aedev.flow.data.local.LocalDataManager.AutoBackupType
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.notification.AutoBackupWorker
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import javax.inject.Inject

/** Backup & restore: manual exports and imports through the coordinator, and the automatic schedule. */
@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        val coordinator: BackupCoordinator,
        private val preferences: PlayerPreferences,
        localDataManager: LocalDataManager,
    ) : SettingsViewModel() {
        val operation = coordinator.operation
        val frequency = preferences.autoBackupFrequency.asState(AutoBackupFrequency.NONE)
        val type = preferences.autoBackupType.asState(AutoBackupType.APP_DATA)
        val folder = preferences.autoBackupFolderUri.asState(null)
        val lastRun = localDataManager.autoBackupLastRun.asState(0L)

        fun dismiss() = coordinator.dismiss()

        /** Re-picking the current schedule would restart its period, so it is a no-op. */
        fun setFrequency(value: AutoBackupFrequency) {
            if (value == frequency.value) return
            write {
                preferences.setAutoBackupFrequency(value)
                val days = value.periodDays()
                if (days == null) AutoBackupWorker.cancelBackup(context) else AutoBackupWorker.scheduleBackup(context, days)
            }
        }

        fun setType(value: AutoBackupType) = write { preferences.setAutoBackupType(value) }

        /** Keeps access to the new folder across restarts and gives up the old folder's grant. */
        fun setFolder(uri: Uri) =
            write {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                val previous = folder.value?.let(Uri::parse)
                context.contentResolver.takePersistableUriPermission(uri, flags)
                if (previous != null && previous != uri) {
                    runCatching { context.contentResolver.releasePersistableUriPermission(previous, flags) }
                }
                preferences.setAutoBackupFolderUri(uri.toString())
            }

        fun folderName(folder: String): String? =
            folderDisplayName(
                runCatching {
                    DocumentsContract.getTreeDocumentId(Uri.parse(folder))
                }.getOrNull(),
            )

        fun backUpNow() {
            val target = folder.value?.let(Uri::parse) ?: return
            coordinator.backUpNow(type.value, target)
        }
    }
