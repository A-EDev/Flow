package com.flow.youtube.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
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
    
    // Dialog states
    var showThreadDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    
    // Storage Info
    var freeSpace by remember { mutableStateOf("Calculating...") }
    var totalSpace by remember { mutableStateOf("") }
    var usedSpacePercentage by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        try {
            val file = File(context.filesDir, "downloads")
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
            freeSpace = "Unknown"
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
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                    Text(
                        text = "Download Settings",
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
                    "Storage",
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
                            Text("Internal Storage", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("$freeSpace free", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
                            "Total: $totalSpace", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Divider(Modifier.padding(horizontal = 16.dp))
                    
                    SettingsItem(
                        icon = Icons.Outlined.Download,
                        title = "Location",
                        subtitle = "Internal App Storage",
                        onClick = { }
                    )
                }
            }

            // ==================== PREFERENCES ====================
            item {
                Text(
                    "Preferences",
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
                        title = "Default Video Quality",
                        subtitle = defaultQuality.label,
                        onClick = { showQualityDialog = true }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Wifi,
                        title = "Download over Wi-Fi only",
                        subtitle = "Reduce data usage",
                        checked = wifiOnly,
                        onCheckedChange = { coroutineScope.launch { preferences.setDownloadOverWifiOnly(it) } }
                    )
                }
            }

            // ==================== PERFORMANCE ====================
            item {
                Text(
                    "Performance",
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
                        title = "Parallel Downloading",
                        subtitle = "Download files in multiple chunks for max speed.",
                        checked = parallelEnabled,
                        onCheckedChange = { coroutineScope.launch { preferences.setParallelDownloadEnabled(it) } }
                    )
                    
                    if (parallelEnabled) {
                        Divider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Speed,
                            title = "Concurrent Threads",
                            subtitle = "$threadCount threads per download",
                            onClick = { showThreadDialog = true }
                        )
                    }
                }
            }
            
            item {
                Text(
                    "Using more threads can increase speed but may use more battery and heat up the device.",
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
            title = { Text("Concurrent Threads") },
            text = {
                Column {
                    Text("Select threads count (1-8):", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = threadCount.toFloat(),
                        onValueChange = { coroutineScope.launch { preferences.setDownloadThreads(it.toInt()) } },
                        valueRange = 1f..8f,
                        steps = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "$threadCount threads", 
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showThreadDialog = false }) { Text("Done") } }
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
            title = { Text("Default Quality") },
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
            confirmButton = { TextButton(onClick = { showQualityDialog = false }) { Text("Cancel") } }
        )
    }
}
