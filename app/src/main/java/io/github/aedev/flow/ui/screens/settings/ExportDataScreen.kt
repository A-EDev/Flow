package io.github.aedev.flow.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.BackupRepository
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDataScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupRepo = remember { BackupRepository(context) }

    val exportAppDataLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                val result = backupRepo.exportData(it)
                android.widget.Toast.makeText(
                    context,
                    context.getString(
                        if (result.isSuccess) R.string.settings_export_success
                        else R.string.settings_export_failed
                    ),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val exportNewPipeSubscriptionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                val result = backupRepo.exportSubscriptionsAsNewPipe(it)
                android.widget.Toast.makeText(
                    context,
                    context.getString(
                        if (result.isSuccess) R.string.export_newpipe_subs_success
                        else R.string.export_newpipe_subs_failed
                    ),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val exportWatchHistoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                val result = backupRepo.exportWatchHistory(it)
                android.widget.Toast.makeText(
                    context,
                    context.getString(
                        if (result.isSuccess) R.string.settings_export_success
                        else R.string.history_export_failed
                    ),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val exportEngineLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                val success = context.contentResolver.openOutputStream(it)?.use { out ->
                    FlowNeuroEngine.exportBrainToStream(out)
                } ?: false
                android.widget.Toast.makeText(
                    context,
                    context.getString(
                        if (success) R.string.export_engine_success else R.string.export_engine_failed
                    ),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val exportMasterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            scope.launch {
                val result = backupRepo.exportMasterBackup(it)
                android.widget.Toast.makeText(
                    context,
                    context.getString(
                        if (result.isSuccess) R.string.master_backup_export_success
                        else R.string.master_backup_export_failed
                    ),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
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
                        text = stringResource(R.string.export_data_screen_title),
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.export_data_section_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                Text(
                    text = stringResource(R.string.export_data_section_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.export_app_data_title),
                    description = stringResource(R.string.export_app_data_desc),
                    icon = Icons.Outlined.SaveAlt,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = {
                        exportAppDataLauncher.launch("flow_backup_${System.currentTimeMillis()}.json")
                    }
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.export_newpipe_subs_title),
                    description = stringResource(R.string.export_newpipe_subs_desc),
                    painter = painterResource(id = R.drawable.ic_newpipe),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = {
                        exportNewPipeSubscriptionsLauncher.launch("newpipe_subscriptions_${System.currentTimeMillis()}.json")
                    }
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.export_watch_history_title),
                    description = stringResource(R.string.export_watch_history_desc),
                    icon = Icons.Outlined.History,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = {
                        exportWatchHistoryLauncher.launch("flow-watch-history.json")
                    }
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.export_engine_data),
                    description = stringResource(R.string.export_engine_data_subtitle),
                    icon = Icons.Outlined.Psychology,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = {
                        exportEngineLauncher.launch("flow_engine_${System.currentTimeMillis()}.json")
                    }
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.master_backup_title),
                    description = stringResource(R.string.master_backup_subtitle),
                    icon = Icons.Outlined.Backup,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    onClick = {
                        exportMasterLauncher.launch("flow_master_backup_${System.currentTimeMillis()}.zip")
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
