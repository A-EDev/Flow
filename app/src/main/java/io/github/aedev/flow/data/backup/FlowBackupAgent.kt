package io.github.aedev.flow.data.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * Auto Backup with one extra step: after a restore, install-only files that an older backup still
 * carries are deleted before the app first runs. With fullBackupOnly the key-value calls never run.
 */
class FlowBackupAgent : BackupAgent() {
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestoreFinished() {
        val deleted = RestoredDataCleanup.deleteInstallOnlyFiles(filesDir)
        if (deleted.isNotEmpty()) Log.i(TAG, "Removed restored install-only files: $deleted")
    }

    private companion object {
        const val TAG = "FlowBackupAgent"
    }
}
