package com.flow.youtube.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Psychology
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Flow Engine / Neural profile import
    val importEngineLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val success = context.contentResolver.openInputStream(it)?.use { inp ->
                        FlowNeuroEngine.importBrainFromStream(context, inp)
                    } ?: false
                    snackbarHostState.showSnackbar(
                        context.getString(
                            if (success) R.string.import_engine_success
                            else R.string.import_engine_failed
                        )
                    )
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.import_data_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                InfoCard(
                    icon = Icons.Outlined.FileDownload,
                    title = stringResource(R.string.import_data_title),
                    description = "Migrate your subscriptions and history from other platforms to Flow seamlessly.",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            item {
                PreferencesSectionHeader(
                    title = "Backup & Restore",
                    subtitle = "Manage your Flow backups"
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_flow_backup_item_title),
                    description = stringResource(R.string.import_flow_backup_desc),
                    icon = Icons.Default.Restore,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = { flowImportLauncher.launch(arrayOf("application/json")) }
                )
            }
            
            item {
                PreferencesSectionHeader(
                    title = "External Services",
                    subtitle = "Import from other YouTube clients"
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_from_youtube),
                    description = stringResource(R.string.import_from_youtube_desc),
                    painter = painterResource(id = R.drawable.ic_youtube),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { youtubeImportLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "text/plain")) }
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_from_newpipe),
                    description = stringResource(R.string.import_from_newpipe_desc),
                    painter = painterResource(id = R.drawable.ic_newpipe),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { newPipeImportLauncher.launch(arrayOf("application/json")) }
                )
            }
            
            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_from_libretube),
                    description = stringResource(R.string.import_from_libretube_desc),
                    painter = painterResource(id = R.drawable.ic_libretube),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = true,
                    onClick = { /* TODO */ }
                )
            }

            item {
                PreferencesSectionHeader(
                    title = stringResource(R.string.import_engine_section_title),
                    subtitle = stringResource(R.string.import_engine_section_subtitle)
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_engine_data),
                    description = stringResource(R.string.import_engine_data_desc),
                    icon = Icons.Outlined.Psychology,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = { importEngineLauncher.launch(arrayOf("application/json")) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    description: String,
    containerColor: Color,
    iconTint: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, containerColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = containerColor.copy(alpha = 0.2f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun PreferencesSectionHeader(
    title: String,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ImportOptionCard(
    title: String,
    description: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (enabled) 1.0f else 0.5f),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = containerColor.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else if (painter != null) {
                        Icon(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.Unspecified
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}