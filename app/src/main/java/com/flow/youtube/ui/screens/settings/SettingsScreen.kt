package com.flow.youtube.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flow.youtube.ui.theme.ThemeMode
import com.flow.youtube.ui.theme.extendedColors
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.data.local.VideoQuality
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val searchHistoryRepo = remember { com.flow.youtube.data.local.SearchHistoryRepository(context) }
    val playerPreferences = remember { PlayerPreferences(context) }
    
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showRegionDialog by remember { mutableStateOf(false) }
    var showClearSearchDialog by remember { mutableStateOf(false) }
    var showHistorySizeDialog by remember { mutableStateOf(false) }
    var showRetentionDaysDialog by remember { mutableStateOf(false) }
    
    // Player preferences states
    val backgroundPlayEnabled by playerPreferences.backgroundPlayEnabled.collectAsState(initial = false)
    val currentRegion by playerPreferences.trendingRegion.collectAsState(initial = "US")
    val wifiQuality by playerPreferences.defaultQualityWifi.collectAsState(initial = VideoQuality.Q_1080p)
    val cellularQuality by playerPreferences.defaultQualityCellular.collectAsState(initial = VideoQuality.Q_480p)
    
    // Search settings states
    val searchHistoryEnabled by searchHistoryRepo.isSearchHistoryEnabledFlow().collectAsState(initial = true)
    val searchSuggestionsEnabled by searchHistoryRepo.isSearchSuggestionsEnabledFlow().collectAsState(initial = true)
    val maxHistorySize by searchHistoryRepo.getMaxHistorySizeFlow().collectAsState(initial = 50)
    val autoDeleteHistory by searchHistoryRepo.isAutoDeleteHistoryEnabledFlow().collectAsState(initial = false)
    val historyRetentionDays by searchHistoryRepo.getHistoryRetentionDaysFlow().collectAsState(initial = 90)
    
    // Region name mapping
    val regionNames = mapOf(
        "US" to "United States",
        "GB" to "United Kingdom",
        "CA" to "Canada",
        "AU" to "Australia",
        "DE" to "Germany",
        "FR" to "France",
        "JP" to "Japan",
        "KR" to "South Korea",
        "IN" to "India",
        "BR" to "Brazil",
        "MX" to "Mexico",
        "ES" to "Spain",
        "IT" to "Italy",
        "NL" to "Netherlands",
        "RU" to "Russia"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance Section
            item {
                SectionHeader(text = "Appearance")
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Palette,
                    title = "Theme",
                    subtitle = formatThemeName(currentTheme),
                    onClick = onNavigateToAppearance
                )
            }

            // Content & Playback Section
            item {
                SectionHeader(text = "Content & Playback")
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.TrendingUp,
                    title = "Trending Region",
                    subtitle = regionNames[currentRegion] ?: currentRegion,
                    onClick = { showRegionDialog = true }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.PlayCircle,
                    title = "Background Play",
                    subtitle = "Continue playing when app is in background",
                    checked = backgroundPlayEnabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            playerPreferences.setBackgroundPlayEnabled(enabled)
                        }
                    }
                )
            }

            // Search Settings Section
            item {
                SectionHeader(text = "Search Settings")
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.History,
                    title = "Save Search History",
                    subtitle = "Save your searches for quick access",
                    checked = searchHistoryEnabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            searchHistoryRepo.setSearchHistoryEnabled(enabled)
                        }
                    }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.TrendingUp,
                    title = "Search Suggestions",
                    subtitle = "Show suggestions while typing",
                    checked = searchSuggestionsEnabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            searchHistoryRepo.setSearchSuggestionsEnabled(enabled)
                        }
                    }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Storage,
                    title = "Max History Size",
                    subtitle = "Currently: $maxHistorySize searches",
                    onClick = { showHistorySizeDialog = true }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.AutoDelete,
                    title = "Auto-Delete History",
                    subtitle = if (autoDeleteHistory) "Delete after $historyRetentionDays days" else "Never delete automatically",
                    checked = autoDeleteHistory,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            searchHistoryRepo.setAutoDeleteHistory(enabled)
                        }
                    }
                )
            }

            if (autoDeleteHistory) {
                item {
                    SettingsItem(
                        icon = Icons.Outlined.Schedule,
                        title = "Retention Period",
                        subtitle = "Delete searches older than $historyRetentionDays days",
                        onClick = { showRetentionDaysDialog = true }
                    )
                }
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.ManageSearch,
                    title = "Clear Search History",
                    subtitle = "Remove all search queries",
                    onClick = { showClearSearchDialog = true }
                )
            }

            // Privacy & Data Section
            item {
                SectionHeader(text = "Privacy & Data Management")
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.DeleteSweep,
                    title = "Clear Watch History",
                    subtitle = "Remove all watched videos",
                    onClick = { /* Show confirmation */ }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.FileUpload,
                    title = "Export Data",
                    subtitle = "Backup your data to a file",
                    onClick = { /* Export */ }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.FileDownload,
                    title = "Import Data",
                    subtitle = "Restore from backup file",
                    onClick = { /* Import */ }
                )
            }

            // About Section
            item {
                SectionHeader(text = "About")
            }

            item {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val versionName = packageInfo.versionName ?: "Unknown"
                
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "App Version",
                    subtitle = versionName,
                    onClick = { }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Person,
                    title = "Made By A-EDev",
                    subtitle = "Tap to visit GitHub profile",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/A-EDev"))
                        context.startActivity(intent)
                    }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Code,
                    title = "Powered by NewPipeExtractor",
                    subtitle = "Open source YouTube extraction library",
                    onClick = { }
                )
            }
        }
    }

    // Clear Search History Dialog
    if (showClearSearchDialog) {
        AlertDialog(
            onDismissRequest = { showClearSearchDialog = false },
            icon = {
                Icon(Icons.Outlined.ManageSearch, contentDescription = null)
            },
            title = {
                Text("Clear Search History?")
            },
            text = {
                Text("This will permanently delete all your search history.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            searchHistoryRepo.clearSearchHistory()
                            showClearSearchDialog = false
                        }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSearchDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Max History Size Dialog
    if (showHistorySizeDialog) {
        var selectedSize by remember { mutableStateOf(maxHistorySize) }
        
        AlertDialog(
            onDismissRequest = { showHistorySizeDialog = false },
            icon = {
                Icon(Icons.Outlined.Storage, contentDescription = null)
            },
            title = {
                Text("Max History Size")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose how many searches to keep:")
                    
                    listOf(25, 50, 100, 200, 500).forEach { size ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedSize = size }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSize == size,
                                onClick = { selectedSize = size }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("$size searches")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            searchHistoryRepo.setMaxHistorySize(selectedSize)
                            showHistorySizeDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHistorySizeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Retention Days Dialog
    if (showRetentionDaysDialog) {
        var selectedDays by remember { mutableStateOf(historyRetentionDays) }
        
        AlertDialog(
            onDismissRequest = { showRetentionDaysDialog = false },
            icon = {
                Icon(Icons.Outlined.Schedule, contentDescription = null)
            },
            title = {
                Text("Retention Period")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delete searches older than:")
                    
                    listOf(7, 30, 90, 180, 365).forEach { days ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedDays = days }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDays == days,
                                onClick = { selectedDays = days }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (days) {
                                    7 -> "1 week"
                                    30 -> "1 month"
                                    90 -> "3 months"
                                    180 -> "6 months"
                                    365 -> "1 year"
                                    else -> "$days days"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            searchHistoryRepo.setHistoryRetentionDays(selectedDays)
                            showRetentionDaysDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRetentionDaysDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Region Selection Dialog
    if (showRegionDialog) {
        var selectedRegion by remember { mutableStateOf(currentRegion) }
        
        AlertDialog(
            onDismissRequest = { showRegionDialog = false },
            icon = {
                Icon(Icons.Outlined.TrendingUp, contentDescription = null)
            },
            title = {
                Text("Select Region")
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(regionNames.entries.toList().size) { index ->
                        val (code, name) = regionNames.entries.toList()[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedRegion = code }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedRegion == code,
                                onClick = { selectedRegion = code }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            playerPreferences.setTrendingRegion(selectedRegion)
                            showRegionDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Video Quality Dialog
    if (showVideoQualityDialog) {
        var selectedWifiQuality by remember { mutableStateOf(wifiQuality) }
        var selectedCellularQuality by remember { mutableStateOf(cellularQuality) }
        
        val qualities = listOf(
            VideoQuality.Q_144p,
            VideoQuality.Q_240p,
            VideoQuality.Q_360p,
            VideoQuality.Q_480p,
            VideoQuality.Q_720p,
            VideoQuality.Q_1080p,
            VideoQuality.Q_1440p,
            VideoQuality.Q_2160p,
            VideoQuality.AUTO
        )
        
        AlertDialog(
            onDismissRequest = { showVideoQualityDialog = false },
            icon = {
                Icon(Icons.Outlined.HighQuality, contentDescription = null)
            },
            title = {
                Text("Video Quality")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Wi-Fi Quality
                    Text(
                        text = "Wi-Fi Quality",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 150.dp)
                    ) {
                        items(qualities.size) { index ->
                            val quality = qualities[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedWifiQuality = quality }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedWifiQuality == quality,
                                    onClick = { selectedWifiQuality = quality }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(quality.label)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Cellular Quality
                    Text(
                        text = "Cellular Quality",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 150.dp)
                    ) {
                        items(qualities.size) { index ->
                            val quality = qualities[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedCellularQuality = quality }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedCellularQuality == quality,
                                    onClick = { selectedCellularQuality = quality }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(quality.label)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            playerPreferences.setDefaultQualityWifi(selectedWifiQuality)
                            playerPreferences.setDefaultQualityCellular(selectedCellularQuality)
                            showVideoQualityDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVideoQualityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

private fun formatThemeName(theme: ThemeMode): String {
    return theme.name.split("_")
        .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}


