package io.github.aedev.flow.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.BackupRepository
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.notification.AutoBackupWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBackupSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ldm = remember { LocalDataManager(context) }
    val playerPrefs = remember { PlayerPreferences(context) }
    val backupRepo = remember { BackupRepository(context) }

    val frequency by playerPrefs.autoBackupFrequency.collectAsState(initial = LocalDataManager.AutoBackupFrequency.NONE)
    val folderUriStr by playerPrefs.autoBackupFolderUri.collectAsState(initial = null)
    val backupType by playerPrefs.autoBackupType.collectAsState(initial = LocalDataManager.AutoBackupType.APP_DATA)
    val lastRun by ldm.autoBackupLastRun.collectAsState(initial = 0L)

    var isRunningNow by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch { playerPrefs.setAutoBackupFolderUri(it.toString()) }
        }
    }

    val lastRunText = if (lastRun == 0L) {
        stringResource(R.string.auto_backup_never_run)
    } else {
        val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        stringResource(R.string.auto_backup_last_run, sdf.format(Date(lastRun)))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.auto_backup_screen_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item {
                Text(
                    text = stringResource(R.string.auto_backup_schedule_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            item {
                SettingsGroup {
                    val frequencies = listOf(
                        LocalDataManager.AutoBackupFrequency.NONE to stringResource(R.string.auto_backup_frequency_none),
                        LocalDataManager.AutoBackupFrequency.DAILY to stringResource(R.string.auto_backup_frequency_daily),
                        LocalDataManager.AutoBackupFrequency.WEEKLY to stringResource(R.string.auto_backup_frequency_weekly),
                        LocalDataManager.AutoBackupFrequency.MONTHLY to stringResource(R.string.auto_backup_frequency_monthly),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            frequencies.take(2).forEach { (freq, label) ->
                                FilterChip(
                                    selected = frequency == freq,
                                    onClick = {
                                        scope.launch {
                                            playerPrefs.setAutoBackupFrequency(freq)
                                            when (freq) {
                                                LocalDataManager.AutoBackupFrequency.NONE ->
                                                    AutoBackupWorker.cancelBackup(context)
                                                LocalDataManager.AutoBackupFrequency.DAILY ->
                                                    AutoBackupWorker.scheduleBackup(context, 1L)
                                                LocalDataManager.AutoBackupFrequency.WEEKLY ->
                                                    AutoBackupWorker.scheduleBackup(context, 7L)
                                                LocalDataManager.AutoBackupFrequency.MONTHLY ->
                                                    AutoBackupWorker.scheduleBackup(context, 30L)
                                            }
                                        }
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            frequencies.drop(2).forEach { (freq, label) ->
                                FilterChip(
                                    selected = frequency == freq,
                                    onClick = {
                                        scope.launch {
                                            playerPrefs.setAutoBackupFrequency(freq)
                                            when (freq) {
                                                LocalDataManager.AutoBackupFrequency.NONE ->
                                                    AutoBackupWorker.cancelBackup(context)
                                                LocalDataManager.AutoBackupFrequency.DAILY ->
                                                    AutoBackupWorker.scheduleBackup(context, 1L)
                                                LocalDataManager.AutoBackupFrequency.WEEKLY ->
                                                    AutoBackupWorker.scheduleBackup(context, 7L)
                                                LocalDataManager.AutoBackupFrequency.MONTHLY ->
                                                    AutoBackupWorker.scheduleBackup(context, 30L)
                                            }
                                        }
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.auto_backup_type_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            item {
                SettingsGroup {
                    val types = listOf(
                        LocalDataManager.AutoBackupType.APP_DATA to stringResource(R.string.auto_backup_type_app_data),
                        LocalDataManager.AutoBackupType.BRAIN to stringResource(R.string.auto_backup_type_brain),
                        LocalDataManager.AutoBackupType.MASTER to stringResource(R.string.auto_backup_type_master),
                    )
                    Column(modifier = Modifier.selectableGroup()) {
                        types.forEachIndexed { index, (type, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = backupType == type,
                                        onClick = { scope.launch { playerPrefs.setAutoBackupType(type) } },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                RadioButton(
                                    selected = backupType == type,
                                    onClick = null
                                )
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (index < types.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.auto_backup_folder_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.Folder,
                        title = stringResource(R.string.auto_backup_folder_label),
                        subtitle = folderUriStr?.let { uri ->
                            try { Uri.parse(uri).lastPathSegment ?: uri } catch (_: Exception) { uri }
                        } ?: stringResource(R.string.auto_backup_no_folder),
                        onClick = { folderPickerLauncher.launch(null) }
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.auto_backup_status_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.auto_backup_last_run_label),
                        subtitle = lastRunText,
                        onClick = {}
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = if (isRunningNow) Icons.Outlined.HourglassEmpty else Icons.Outlined.Backup,
                        title = stringResource(R.string.auto_backup_run_now),
                        subtitle = if (isRunningNow) "…" else "",
                        onClick = {
                            if (!isRunningNow && folderUriStr != null) {
                                isRunningNow = true
                                scope.launch {
                                    val folderUri = Uri.parse(folderUriStr)
                                    val result = when (backupType) {
                                        LocalDataManager.AutoBackupType.APP_DATA ->
                                            backupRepo.exportDataToFolder(folderUri)
                                        LocalDataManager.AutoBackupType.BRAIN ->
                                            backupRepo.exportBrainToFolder(folderUri)
                                        LocalDataManager.AutoBackupType.MASTER ->
                                            backupRepo.exportMasterToFolder(folderUri)
                                    }
                                    if (result.isSuccess) {
                                        ldm.setAutoBackupLastRun(System.currentTimeMillis())
                                    }
                                    isRunningNow = false
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(
                                            if (result.isSuccess) R.string.auto_backup_success
                                            else R.string.auto_backup_failed
                                        ),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
