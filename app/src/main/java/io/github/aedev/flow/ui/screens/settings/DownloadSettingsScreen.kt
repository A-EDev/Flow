package io.github.aedev.flow.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoQuality
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember { PlayerPreferences(context) }
    
    val parallelEnabled by preferences.parallelDownloadEnabled.collectAsState(initial = true)
    val threadCount by preferences.downloadThreads.collectAsState(initial = 3)
    val wifiOnly by preferences.downloadOverWifiOnly.collectAsState(initial = false)
    val defaultQuality by preferences.defaultDownloadQuality.collectAsState(initial = VideoQuality.Q_720p)
    val downloadLocation by preferences.downloadLocation.collectAsState(initial = null)
    
    // Dialog states
    var showThreadDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }

    // Runtime permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        Log.d("DownloadSettings", "Storage permissions granted=$granted")
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try { context.contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
            val path: String? = runCatching {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val parts = docId.split(":")
                if (parts.size == 2) {
                    val volume = parts[0]
                    val relativePath = parts[1]
                    if (volume.equals("primary", ignoreCase = true)) {
                        "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
                    } else {
                        "/storage/$volume/$relativePath"
                    }
                } else null
            }.getOrNull() ?: uri.path
            coroutineScope.launch {
                if (!path.isNullOrBlank()) {
                    try { File(path).mkdirs() } catch (_: Exception) {}
                    preferences.setDownloadLocation(path)
                }
                showLocationDialog = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val writeGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!writeGranted) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }
    
    val defaultVideoPath = remember {
        try {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "Flow"
            ).absolutePath
        } catch (e: Exception) {
            "Internal App Storage"
        }
    }
    val displayPath = downloadLocation ?: defaultVideoPath
    
    // Storage Info
    var freeSpace by remember { mutableStateOf(context.getString(R.string.loading_ellipsis)) }
    var totalSpace by remember { mutableStateOf("") }
    var usedSpacePercentage by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(downloadLocation) {
        try {
            val statsPath = downloadLocation 
                ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).path
            val file = File(statsPath)
            if (!file.exists()) file.mkdirs()
            
            val stat = android.os.StatFs(file.path)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            val total = stat.blockCountLong * stat.blockSizeLong
            
            val availableGB = available / (1024f * 1024f * 1024f)
            val totalGB = total / (1024f * 1024f * 1024f)
            
            freeSpace = String.format("%.1f GB", availableGB)
            totalSpace = String.format("%.1f GB", totalGB)
            
            if (total > 0) {
                usedSpacePercentage = (total - available).toFloat() / total.toFloat()
            }
        } catch (e: Exception) {
            freeSpace = context.getString(R.string.unknown)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.download_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            ),
            windowInsets = WindowInsets(0.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ==================== STORAGE ====================
            item {
                Text(
                    stringResource(R.string.storage_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            item {
                SettingsGroup {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.internal_storage_label),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.free_space_template, freeSpace),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { usedSpacePercentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.total_space_template, totalSpace),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    SettingsItem(
                        icon = Icons.Outlined.Download,
                        title = stringResource(R.string.location_label),
                        subtitle = displayPath,
                        onClick = { showLocationDialog = true }
                    )
                }
            }

            // ==================== PREFERENCES ====================
            item {
                Text(
                    stringResource(R.string.preferences_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.HighQuality,
                        title = stringResource(R.string.default_video_quality_label),
                        subtitle = defaultQuality.label,
                        onClick = { showQualityDialog = true }
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Wifi,
                        title = stringResource(R.string.download_over_wifi_only),
                        subtitle = stringResource(R.string.reduce_data_usage_subtitle),
                        checked = wifiOnly,
                        onCheckedChange = { coroutineScope.launch { preferences.setDownloadOverWifiOnly(it) } }
                    )
                }
            }

            // ==================== PERFORMANCE ====================
            item {
                Text(
                    stringResource(R.string.performance_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.RocketLaunch,
                        title = stringResource(R.string.parallel_downloading_title),
                        subtitle = stringResource(R.string.parallel_downloading_subtitle),
                        checked = parallelEnabled,
                        onCheckedChange = { coroutineScope.launch { preferences.setParallelDownloadEnabled(it) } }
                    )
                    if (parallelEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Speed,
                            title = stringResource(R.string.concurrent_threads_title),
                            subtitle = stringResource(R.string.threads_per_download_template, threadCount),
                            onClick = { showThreadDialog = true }
                        )
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.performance_optimization_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

    // ==================== DIALOGS ====================

    if (showThreadDialog) {
        AlertDialog(
            onDismissRequest = { showThreadDialog = false },
            icon = { Icon(Icons.Outlined.Speed, null) },
            title = { Text(stringResource(R.string.concurrent_threads_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.select_threads_count_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Slider(
                        value = threadCount.toFloat(),
                        onValueChange = { coroutineScope.launch { preferences.setDownloadThreads(it.toInt()) } },
                        valueRange = 1f..8f,
                        steps = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.threads_count_label, threadCount), 
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = { 
                TextButton(onClick = { showThreadDialog = false }) { 
                    Text(stringResource(R.string.close)) 
                } 
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    if (showQualityDialog) {
        val qualities = listOf(
            VideoQuality.Q_144p, VideoQuality.Q_240p, VideoQuality.Q_360p,
            VideoQuality.Q_480p, VideoQuality.Q_720p,
            VideoQuality.Q_1080p, VideoQuality.Q_1440p, VideoQuality.Q_2160p
        )
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            icon = { Icon(Icons.Outlined.HighQuality, null) },
            title = { Text(stringResource(R.string.quality)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    qualities.forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        preferences.setDefaultDownloadQuality(quality)
                                        showQualityDialog = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultQuality == quality,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = quality.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = { 
                TextButton(onClick = { showQualityDialog = false }) { 
                    Text(stringResource(R.string.cancel)) 
                } 
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showLocationDialog) {
        val downloadsPath = remember {
            try { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Flow").absolutePath } 
            catch (_: Exception) { null }
        }
        val moviesPath = remember {
            try { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Flow").absolutePath } 
            catch (_: Exception) { null }
        }
        val internalPath = remember { File(context.filesDir, "downloads").absolutePath }

        val presetPaths = listOfNotNull(downloadsPath, moviesPath, internalPath)
        val isSafCustomSelected = downloadLocation != null && downloadLocation !in presetPaths

        var showManualDialog by remember { mutableStateOf(false) }
        var manualPathInput by remember { mutableStateOf(if (isSafCustomSelected) downloadLocation ?: "" else "") }

        BasicAlertDialog(onDismissRequest = { showLocationDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    
                    // HEADER
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            stringResource(R.string.location_label),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.location_dialog_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))

                    Text(
                        stringResource(R.string.location_preset_header).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                    )

                    // HELPER COMPOSABLE FOR ROWS
                    @Composable
                    fun PresetRow(label: String, path: String?, isRecommended: Boolean = false) {
                        val isSelected = path != null && path == downloadLocation || (path == null && downloadLocation == null)
                        
                        val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent
                        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    path?.let { p -> try { File(p).mkdirs() } catch (_: Exception) {} }
                                    preferences.setDownloadLocation(path)
                                    showLocationDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = bgColor,
                            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected, 
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = label, 
                                            style = MaterialTheme.typography.bodyLarge, 
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false) 
                                        )
                                        
                                        if (isRecommended) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.location_badge_recommended),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    if (path != null) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    downloadsPath?.let { PresetRow(stringResource(R.string.location_downloads_label), it, isRecommended = true) }
                    moviesPath?.let { PresetRow(stringResource(R.string.location_movies_label), it) }
                    PresetRow(stringResource(R.string.location_internal_app_label), internalPath)

                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.location_custom_header).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                    )

                    // SAF PICKER ROW
                    val safBg = if (isSafCustomSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent
                    val safBorder = if (isSafCustomSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                    Surface(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = safBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, safBorder)
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = if (isSafCustomSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.location_custom_saf_label),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (isSafCustomSelected) downloadLocation ?: stringResource(R.string.location_custom_saf_desc)
                                           else stringResource(R.string.location_custom_saf_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // MANUAL PICKER ROW
                    Surface(
                        onClick = { showManualDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.location_custom_manual_label),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.location_custom_manual_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showLocationDialog = false }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }

        if (showManualDialog) {
            AlertDialog(
                onDismissRequest = { showManualDialog = false },
                icon = { Icon(Icons.Outlined.Edit, null) },
                title = { Text(stringResource(R.string.location_manual_dialog_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.location_custom_manual_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = manualPathInput,
                            onValueChange = { manualPathInput = it },
                            label = { Text(stringResource(R.string.location_manual_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = manualPathInput.trim()
                            if (trimmed.isNotBlank()) {
                                coroutineScope.launch {
                                    try { File(trimmed).mkdirs() } catch (_: Exception) {}
                                    preferences.setDownloadLocation(trimmed)
                                    showManualDialog = false
                                    showLocationDialog = false
                                }
                            }
                        },
                        enabled = manualPathInput.trim().isNotBlank()
                    ) {
                        Text(stringResource(R.string.location_manual_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

