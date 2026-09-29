package io.github.aedev.flow.ui.screens.settings.backup

import io.github.aedev.flow.data.local.LocalDataManager.AutoBackupFrequency

/** Days between automatic backups, or null when they are off. */
internal fun AutoBackupFrequency.periodDays(): Long? =
    when (this) {
        AutoBackupFrequency.NONE -> null
        AutoBackupFrequency.DAILY -> 1L
        AutoBackupFrequency.WEEKLY -> 7L
        AutoBackupFrequency.MONTHLY -> 30L
    }

/**
 * A readable name for a folder picked through the system picker. Tree document ids look like
 * `primary:Documents/Backups`; the part after the volume is what the user chose.
 */
internal fun folderDisplayName(treeDocumentId: String?): String? =
    treeDocumentId
        ?.substringAfter(':', treeDocumentId)
        ?.trim('/')
        ?.takeIf { it.isNotBlank() }
