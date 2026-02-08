package com.flow.youtube.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
import com.flow.youtube.data.local.BackupRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDataScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupRepo = remember { BackupRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val flowImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val result = backupRepo.importData(it)
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar(context.getString(R.string.import_flow_backup_success))
                    } else {
                        snackbarHostState.showSnackbar(context.getString(R.string.import_flow_backup_failed_template, result.exceptionOrNull()?.message))
                    }
                }
            }
        }
    )

    val newPipeImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val result = backupRepo.importNewPipe(it)
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar(context.getString(R.string.import_newpipe_success_template, result.getOrNull()))
                    } else {
                        snackbarHostState.showSnackbar(context.getString(R.string.import_newpipe_failed_template, result.exceptionOrNull()?.message))
                    }
                }
            }
        }
    )

    val youtubeImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val result = backupRepo.importYouTube(it)
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar(context.getString(R.string.import_youtube_success_template, result.getOrNull()))
                    } else {
                        snackbarHostState.showSnackbar(context.getString(R.string.import_youtube_failed_template, result.exceptionOrNull()?.message))
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_data_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_flow_backup_item_title),
                    description = stringResource(R.string.import_flow_backup_desc),
                    icon = Icons.Default.Restore,
                    onClick = { flowImportLauncher.launch(arrayOf("application/json")) }
                )
            }
            
            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_from_newpipe),
                    description = stringResource(R.string.import_from_newpipe_desc),
                    icon = Icons.Default.CloudDownload,
                    onClick = { newPipeImportLauncher.launch(arrayOf("application/json")) }
                )
            }
            
            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_from_libretube),
                    description = stringResource(R.string.import_from_libretube_desc),
                    icon = Icons.Default.VideoLibrary,
                    enabled = false,
                    onClick = { /* TODO */ }
                )
            }
            
            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_from_youtube),
                    description = stringResource(R.string.import_from_youtube_desc),
                    icon = Icons.Default.PlayArrow,
                    enabled = true,
                    onClick = { youtubeImportLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "text/plain")) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}