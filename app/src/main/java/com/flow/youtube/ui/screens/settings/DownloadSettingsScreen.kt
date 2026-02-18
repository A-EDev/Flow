package com.flow.youtube.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SignalWifi4Bar
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.flow.youtube.R
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.data.local.VideoQuality
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

    // Runtime permission launcher (needed for API < 29 to write to external storage)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        Log.d("DownloadSettings", "Storage permissions granted=$granted")
    }

    // Request storage permission on first composition for pre-Q devices
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

    Scaffold(
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
                        text = stringResource(R.string.download_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
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
                            Text(stringResource(R.string.internal_storage_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.free_space_template, freeSpace), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = usedSpacePercentage,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
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
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
    
    // Thread Dialog
    if (showThreadDialog) {
        AlertDialog(
            onDismissRequest = { showThreadDialog = false },
            icon = { Icon(Icons.Outlined.Speed, null) },
            title = { Text(stringResource(R.string.concurrent_threads_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.select_threads_count_desc), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = threadCount.toFloat(),
                        onValueChange = { coroutineScope.launch { preferences.setDownloadThreads(it.toInt()) } },
                        valueRange = 1f..8f,
                        steps = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.threads_count_label, threadCount), 
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showThreadDialog = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    // Quality Dialog
    if (showQualityDialog) {
        val qualities = listOf(
            VideoQuality.Q_360p, VideoQuality.Q_480p, VideoQuality.Q_720p, 
            VideoQuality.Q_1080p, VideoQuality.Q_1440p, VideoQuality.Q_2160p
        )
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            icon = { Icon(Icons.Outlined.HighQuality, null) },
            title = { Text(stringResource(R.string.quality)) },
            text = {
                LazyColumn {
                    items(qualities.size) { index ->
                        val quality = qualities[index]
                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    preferences.setDefaultDownloadQuality(quality)
                                    showQualityDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.Transparent
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = defaultQuality == quality, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(quality.label)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQualityDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Location Picker Dialog
    if (showLocationDialog) {
        val downloadsPath = remember {
            try {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Flow").absolutePath
            } catch (_: Exception) { null }
        }
        val moviesPath = remember {
            try {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Flow").absolutePath
            } catch (_: Exception) { null }
        }
        val internalPath = remember {
            File(context.filesDir, "downloads").absolutePath
        }

        // Custom path option sentinel – null path means "use Downloads (default)"
        data class LocationOption(val label: String, val path: String?, val description: String, val isCustom: Boolean = false)

        val options = remember {
            listOfNotNull(
                downloadsPath?.let { LocationOption("Downloads/Flow", it, it) }, // default / recommended
                moviesPath?.let { LocationOption("Movies/Flow", it, it) },
                LocationOption("Internal App Storage", internalPath, internalPath),
                LocationOption("Custom Path…", null, "Enter a folder path manually", isCustom = true)
            )
        }

        // Determine which preset is currently selected (if any)
        val presetPaths = options.filter { !it.isCustom }.mapNotNull { it.path }
        val isCustomSelected = downloadLocation != null && downloadLocation !in presetPaths

        var showCustomPathDialog by remember { mutableStateOf(false) }
        var customPathInput by remember {
            mutableStateOf(if (isCustomSelected) downloadLocation ?: "" else "")
        }

        if (!showCustomPathDialog) {
            AlertDialog(
                onDismissRequest = { showLocationDialog = false },
                icon = { Icon(Icons.Outlined.FolderOpen, null) },
                title = { Text(stringResource(R.string.location_label)) },
                text = {
                    Column {
                        Text(
                            "Choose where to save downloaded files.\nDownloads/Flow is recommended — it works on all devices without extra permissions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        options.forEach { option ->
                            Surface(
                                onClick = {
                                    if (option.isCustom) {
                                        showCustomPathDialog = true
                                    } else {
                                        coroutineScope.launch {
                                            // Ensure the directory exists before saving the preference
                                            option.path?.let { p ->
                                                try { File(p).mkdirs() } catch (_: Exception) {}
                                            }
                                            preferences.setDownloadLocation(option.path)
                                            showLocationDialog = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Transparent
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isSelected = if (option.isCustom) {
                                        isCustomSelected
                                    } else {
                                        option.path != null && option.path == downloadLocation
                                    }
                                    RadioButton(selected = isSelected, onClick = null)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                            if (option.path == downloadsPath) {
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = MaterialTheme.shapes.small
                                                ) {
                                                    Text(
                                                        "Recommended",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                        val desc = if (option.isCustom && isCustomSelected) downloadLocation ?: option.description
                                                   else option.description
                                        Text(
                                            desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (option.isCustom) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLocationDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        } else {
            // Custom path entry dialog
            AlertDialog(
                onDismissRequest = { showCustomPathDialog = false },
                icon = { Icon(Icons.Outlined.Edit, null) },
                title = { Text("Custom Download Path") },
                text = {
                    Column {
                        Text(
                            "Enter the absolute path of the folder where downloads should be saved. The folder will be created if it doesn't exist.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customPathInput,
                            onValueChange = { customPathInput = it },
                            label = { Text("Folder path") },
                            placeholder = { Text("/storage/emulated/0/MyDownloads") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val trimmed = customPathInput.trim()
                            if (trimmed.isNotEmpty()) {
                                coroutineScope.launch {
                                    try { File(trimmed).mkdirs() } catch (_: Exception) {}
                                    preferences.setDownloadLocation(trimmed)
                                }
                            }
                            showCustomPathDialog = false
                            showLocationDialog = false
                        },
                        enabled = customPathInput.trim().isNotEmpty()
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomPathDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}
