package io.github.aedev.flow.ui.screens.settings

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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QueueMusic
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.activity.ComponentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.BackupRepository
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

    // Activity-scoped ViewModel: survives screen navigation so imports keep running
    val activity = context as ComponentActivity
    val importViewModel: ImportViewModel = hiltViewModel(activity)
    val importState by importViewModel.state.collectAsStateWithLifecycle()

    // Show snackbar when an import finishes (success or error), then reset state
    LaunchedEffect(importState) {
        when (val s = importState) {
            is ImportViewModel.State.Success -> {
                snackbarHostState.showSnackbar(
                    s.message ?: if ((s.count ?: 0) > 0) "Imported ${s.count} ${s.label.lowercase()}"
                    else "${s.label} imported successfully"
                )
                importViewModel.dismiss()
            }
            is ImportViewModel.State.Error -> {
                val msg = when (s.message) {
                    "no_entries"     -> "No watch-history entries found in file"
                    "no_videos"      -> "No videos found in playlist file"
                    "no_content"     -> "No importable content found in this backup"
                    "invalid_format" -> "Unrecognised backup format — make sure you selected the correct file"
                    else             -> "Import failed: ${s.message}"
                }
                snackbarHostState.showSnackbar(msg)
                importViewModel.dismiss()
            }
            else -> {}
        }
    }

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
        onResult = { uri -> uri?.let { importViewModel.importNewPipe(it) } }
    )

    val youtubeImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { importViewModel.importYouTube(it) } }
    )

    val youtubeHistoryImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { importViewModel.importYouTubeWatchHistory(it) } }
    )

    val freeTubeHistoryImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { importViewModel.importFreeTubeWatchHistory(it) } }
    )

    val newPipeHistoryImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { importViewModel.importNewPipeWatchHistory(it) } }
    )

    val youtubePlaylistImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val result = backupRepo.importYouTubePlaylist(it)
                    if (result.isSuccess) {
                        val (name, count) = result.getOrNull()!!
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.import_yt_playlist_success_template, name, count)
                        )
                    } else {
                        val errMsg = result.exceptionOrNull()?.message
                        val display = when (errMsg) {
                            "no_videos" -> context.getString(R.string.import_yt_playlist_empty_error)
                            else -> context.getString(R.string.import_yt_playlist_failed_template, errMsg)
                        }
                        snackbarHostState.showSnackbar(display)
                    }
                }
            }
        }
    )

    val youtubeMusicPlaylistImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val result = backupRepo.importYouTubePlaylist(it, isMusic = true)
                    if (result.isSuccess) {
                        val (name, count) = result.getOrNull()!!
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.import_yt_playlist_success_template, name, count)
                        )
                    } else {
                        val errMsg = result.exceptionOrNull()?.message
                        val display = when (errMsg) {
                            "no_videos" -> context.getString(R.string.import_yt_playlist_empty_error)
                            else -> context.getString(R.string.import_yt_playlist_failed_template, errMsg)
                        }
                        snackbarHostState.showSnackbar(display)
                    }
                }
            }
        }
    )

    val libreTubeImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { importViewModel.importLibreTube(it) } }
    )

    val youtubeTakeoutImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { importViewModel.importYouTubeTakeout(it) } }
    )

    val newPipePlaylistImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { importViewModel.importNewPipePlaylists(it) } }
    )

    val libreTubePlaylistImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { importViewModel.importLibreTubePlaylists(it) } }
    )

    val metrolistImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { importViewModel.importMetrolist(it) } }
    )

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

    val importMasterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { importViewModel.importMasterBackup(it) } }
    )

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
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.import_data_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
            // Live progress banner — visible whenever the ViewModel is running an import
            item {
                ImportProgressBanner(importState)
            }

            item {
                InfoCard(
                    icon = Icons.Outlined.FileDownload,
                    title = stringResource(R.string.import_data_title),
                    description = stringResource(R.string.import_migrate_description),
                    containerColor = MaterialTheme.colorScheme.primary,
                    iconTint = MaterialTheme.colorScheme.onPrimary
                )
            }

            item {
                PreferencesSectionHeader(
                    title = stringResource(R.string.import_backup_restore),
                    subtitle = stringResource(R.string.import_manage_backups)
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
                ImportOptionCard(
                    title = stringResource(R.string.import_master_backup_title),
                    description = stringResource(R.string.import_master_backup_desc),
                    icon = Icons.Outlined.Archive,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = { importMasterLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
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
                PreferencesSectionHeader(
                    title = stringResource(R.string.import_external_services),
                    subtitle = stringResource(R.string.import_other_youtube_clients)
                )
            }

            item {
                ImportSubsectionHeader(title = stringResource(R.string.import_subscriptions_section_title))
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_from_youtube),
                    description = stringResource(R.string.import_from_youtube_desc),
                    painter = painterResource(id = R.drawable.ic_youtube),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = importState !is ImportViewModel.State.Running,
                    onClick = { youtubeImportLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "text/plain")) }
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_from_newpipe),
                    description = stringResource(R.string.import_from_newpipe_desc),
                    painter = painterResource(id = R.drawable.ic_newpipe),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = importState !is ImportViewModel.State.Running,
                    onClick = { newPipeImportLauncher.launch(arrayOf("application/json")) }
                )
            }
            
            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_from_libretube),
                    description = stringResource(R.string.import_from_libretube_desc),
                    painter = painterResource(id = R.drawable.ic_libretube),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = importState !is ImportViewModel.State.Running,
                    onClick = { libreTubeImportLauncher.launch(arrayOf("application/json")) }
                )
            }

            item {
                ImportSubsectionHeader(title = stringResource(R.string.import_history_section_title))
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_yt_takeout_all),
                    description = stringResource(R.string.import_yt_takeout_all_desc),
                    icon = Icons.Outlined.Archive,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    enabled = importState !is ImportViewModel.State.Running,
                    onClick = { youtubeTakeoutImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_yt_watch_history),
                    description = stringResource(R.string.import_yt_watch_history_desc),
                    icon = Icons.Outlined.History,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = importState !is ImportViewModel.State.Running,
                    onClick = { youtubeHistoryImportLauncher.launch(arrayOf("text/html", "text/plain", "*/*")) }
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_freetube_history),
                    description = stringResource(R.string.import_freetube_history_desc),
                    icon = Icons.Outlined.History,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = importState !is ImportViewModel.State.Running,
                    onClick = { freeTubeHistoryImportLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_newpipe_history),
                    description = stringResource(R.string.import_newpipe_history_desc),
                    painter = painterResource(id = R.drawable.ic_newpipe),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = importState !is ImportViewModel.State.Running,
                    onClick = { newPipeHistoryImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-sqlite3", "*/*")) }
                )
            }

            item {
                ImportSubsectionHeader(title = stringResource(R.string.import_playlists_section_title))
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_newpipe_playlists),
                    description = stringResource(R.string.import_newpipe_playlists_desc),
                    painter = painterResource(id = R.drawable.ic_newpipe),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = importState !is ImportViewModel.State.Running,
                    onClick = { newPipePlaylistImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_libretube_playlists),
                    description = stringResource(R.string.import_libretube_playlists_desc),
                    painter = painterResource(id = R.drawable.ic_libretube),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = importState !is ImportViewModel.State.Running,
                    onClick = { libreTubePlaylistImportLauncher.launch(arrayOf("application/json")) }
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_yt_playlist),
                    description = stringResource(R.string.import_yt_playlist_desc),
                    icon = Icons.Outlined.PlaylistPlay,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = importState !is ImportViewModel.State.Running,
                    onClick = { youtubePlaylistImportLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "text/plain")) }
                )
            }

            item {
                ImportSubsectionHeader(title = stringResource(R.string.import_music_apps_section_title))
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_from_metrolist),
                    description = stringResource(R.string.import_from_metrolist_desc),
                    painter = painterResource(id = R.drawable.ic_metrolist),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = importState !is ImportViewModel.State.Running,
                    onClick = { metrolistImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                )
            }

            item {
                ImportOptionCard(
                    title = stringResource(R.string.import_yt_music_playlist),
                    description = stringResource(R.string.import_yt_music_playlist_desc),
                    icon = Icons.Outlined.QueueMusic,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = importState !is ImportViewModel.State.Running,
                    onClick = { youtubeMusicPlaylistImportLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "text/plain")) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ImportSubsectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
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
    containerColor: Color = MaterialTheme.colorScheme.primary,
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

/**
 * Animated banner that shows import progress.
 * Appears when state is [ImportViewModel.State.Running] and fades out otherwise.
 */
@Composable
internal fun ImportProgressBanner(state: ImportViewModel.State) {
    AnimatedVisibility(
        visible = state is ImportViewModel.State.Running,
        enter = expandVertically() + fadeIn(),
        exit  = shrinkVertically() + fadeOut()
    ) {
        val running = state as? ImportViewModel.State.Running ?: return@AnimatedVisibility
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier  = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color     = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text     = "Importing ${running.label}\u2026",
                        style    = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        color    = MaterialTheme.colorScheme.onSurface
                    )
                    if (running.total > 0) {
                        Text(
                            text  = "${running.current} / ${running.total}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (running.total > 0) {
                    LinearProgressIndicator(
                        progress   = { running.current.toFloat() / running.total.toFloat() },
                        modifier   = Modifier.fillMaxWidth().height(3.dp),
                        color      = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier   = Modifier.fillMaxWidth().height(3.dp),
                        color      = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = "You can navigate away \u2014 the import continues in the background.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
