package com.flow.youtube.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import com.flow.youtube.ui.theme.ThemeMode
import com.flow.youtube.ui.theme.extendedColors
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.data.local.VideoQuality
import com.flow.youtube.data.local.BufferProfile
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onNavigateBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToDonations: () -> Unit,
    onNavigateToPersonality: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToTimeManagement: () -> Unit,
    onNavigateToImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val searchHistoryRepo = remember { com.flow.youtube.data.local.SearchHistoryRepository(context) }
    val playerPreferences = remember { PlayerPreferences(context) }
    val viewHistory = remember { com.flow.youtube.data.local.ViewHistory.getInstance(context) }
    val backupRepo = remember { com.flow.youtube.data.local.BackupRepository(context) }
    
    // Brain State
    var userBrain by remember { mutableStateOf<FlowNeuroEngine.UserBrain?>(null) }
    var refreshBrainTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshBrainTrigger) {
        userBrain = FlowNeuroEngine.getBrainSnapshot()
    }
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                coroutineScope.launch {
                    val result = backupRepo.exportData(it)
                    if (result.isSuccess) {
                        android.widget.Toast.makeText(context, "Data exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Export failed: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showRegionDialog by remember { mutableStateOf(false) }
    var showClearSearchDialog by remember { mutableStateOf(false) }
    var showClearWatchHistoryDialog by remember { mutableStateOf(false) }
    var showHistorySizeDialog by remember { mutableStateOf(false) }
    var showRetentionDaysDialog by remember { mutableStateOf(false) }
    var showResetBrainDialog by remember { mutableStateOf(false) }
    var showBufferSettingsDialog by remember { mutableStateOf(false) }
    
    // Player preferences states
    val backgroundPlayEnabled by playerPreferences.backgroundPlayEnabled.collectAsState(initial = false)
    val currentRegion by playerPreferences.trendingRegion.collectAsState(initial = "US")
    val wifiQuality by playerPreferences.defaultQualityWifi.collectAsState(initial = VideoQuality.Q_1080p)
    val cellularQuality by playerPreferences.defaultQualityCellular.collectAsState(initial = VideoQuality.Q_480p)
    val autoplayEnabled by playerPreferences.autoplayEnabled.collectAsState(initial = true)
    val skipSilenceEnabled by playerPreferences.skipSilenceEnabled.collectAsState(initial = false)
    val sponsorBlockEnabled by playerPreferences.sponsorBlockEnabled.collectAsState(initial = false)
    val autoPipEnabled by playerPreferences.autoPipEnabled.collectAsState(initial = false)
    val manualPipButtonEnabled by playerPreferences.manualPipButtonEnabled.collectAsState(initial = true)
    
    // Search settings states
    val searchHistoryEnabled by searchHistoryRepo.isSearchHistoryEnabledFlow().collectAsState(initial = true)
    val searchSuggestionsEnabled by searchHistoryRepo.isSearchSuggestionsEnabledFlow().collectAsState(initial = true)
    val maxHistorySize by searchHistoryRepo.getMaxHistorySizeFlow().collectAsState(initial = 50)
    val autoDeleteHistory by searchHistoryRepo.isAutoDeleteHistoryEnabledFlow().collectAsState(initial = false)
    val historyRetentionDays by searchHistoryRepo.getHistoryRetentionDaysFlow().collectAsState(initial = 90)

    // Buffer Settings
    val minBufferMs by playerPreferences.minBufferMs.collectAsState(initial = 30000)
    val maxBufferMs by playerPreferences.maxBufferMs.collectAsState(initial = 100000)
    val bufferForPlaybackMs by playerPreferences.bufferForPlaybackMs.collectAsState(initial = 1000)
    val bufferForPlaybackAfterRebufferMs by playerPreferences.bufferForPlaybackAfterRebufferMs.collectAsState(initial = 2500)
    val currentBufferProfile by playerPreferences.bufferProfile.collectAsState(initial = BufferProfile.STABLE)
    
    // Region name mapping
    val regionNames = mapOf(
        "US" to "United States", "GB" to "United Kingdom", "CA" to "Canada", "AU" to "Australia",
        "DE" to "Germany", "FR" to "France", "JP" to "Japan", "KR" to "South Korea",
        "IN" to "India", "BR" to "Brazil", "MX" to "Mexico", "ES" to "Spain",
        "IT" to "Italy", "NL" to "Netherlands", "RU" to "Russia"
    )

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
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
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
            // =================================================
// 🧠 MY FLOW PERSONALITY (FLOW EXCLUSIVE FEATURE)
// =================================================
item {
    Text(
        text = "Flow Engine",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 16.dp)
    )
}

item {
    val persona = if (userBrain != null) FlowNeuroEngine.getPersona(userBrain!!) else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(180.dp) // Fixed height for a hero card look
            .clickable(onClick = onNavigateToPersonality),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 1000f)
                    )
                )
        ) {
            // 1. Background Decor (Abstract Shapes)
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Top Right Circle
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = size.width * 0.5f,
                    center = Offset(size.width, 0f)
                )
                // Bottom Left Blob
                drawCircle(
                    color = Color.Black.copy(alpha = 0.05f),
                    radius = size.width * 0.3f,
                    center = Offset(0f, size.height)
                )
            }

            // 2. Huge Emoji Icon (Watermark style)
            if (persona != null) {
                Text(
                    text = persona.icon, // e.g., 🤿 or 🧭
                    fontSize = 120.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 20.dp, y = 20.dp)
                        .alpha(0.15f)
                )
            }

            // 3. Main Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Badge
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "ACTIVE LEARNING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Reset Button (Subtle)
                    IconButton(
                        onClick = { showResetBrainDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Persona Info
                if (persona != null) {
                    Column {
                        Text(
                            text = persona.title, // e.g. "The Deep Diver"
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = persona.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // Loading State
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Analyzing your interactions...",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                // Bottom CTA
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "View Analytics",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ArrowForward, // Use ArrowForward instead of rotated ArrowBack
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

            // =================================================
            // GENERAL
            // =================================================
            item { SectionHeader(text = "General") }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Schedule,
                    title = "Time Management",
                    subtitle = "Usage stats, Bedtime reminders",
                    onClick = onNavigateToTimeManagement
                )
            }

            // =================================================
            // APPEARANCE
            // =================================================
            item { SectionHeader(text = "Appearance") }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Palette,
                    title = "Theme",
                    subtitle = formatThemeName(currentTheme),
                    onClick = onNavigateToAppearance
                )
            }

            // =================================================
            // DOWNLOADS
            // =================================================
            item { SectionHeader(text = "Downloads") }
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.Download,
                        title = "Download Settings",
                        subtitle = "Performance, Storage, Threads",
                        onClick = onNavigateToDownloads
                    )
                }
            }

            // =================================================
            // CONTENT & PLAYBACK
            // =================================================
            item { SectionHeader(text = "Content & Playback") }
            
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.TrendingUp,
                        title = "Trending Region",
                        subtitle = regionNames[currentRegion] ?: currentRegion,
                        onClick = { showRegionDialog = true }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                         icon = Icons.Outlined.HighQuality,
                         title = "Video Quality",
                         subtitle = "Default resolution for Wi-Fi and Mobile",
                         onClick = { showVideoQualityDialog = true }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.PlayCircle,
                        title = "Background Play",
                        subtitle = "Continue playing when app is in background",
                        checked = backgroundPlayEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setBackgroundPlayEnabled(it) } }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.SkipNext,
                        title = "Autoplay",
                        subtitle = "Automatically play the next video",
                        checked = autoplayEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setAutoplayEnabled(it) } }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.GraphicEq,
                        title = "Skip Silence",
                        subtitle = "Skip parts with no audio",
                        checked = skipSilenceEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setSkipSilenceEnabled(it) } }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.ContentCut,
                        title = "SponsorBlock",
                        subtitle = "Skip sponsored segments",
                        checked = sponsorBlockEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setSponsorBlockEnabled(it) } }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.PictureInPictureAlt,
                        title = "Auto Picture-in-Picture",
                        subtitle = "Automatically enter PiP when leaving the app",
                        checked = autoPipEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setAutoPipEnabled(it) } }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.PictureInPicture,
                        title = "Show PiP Button",
                        subtitle = "Show manual PiP button in player controls",
                        checked = manualPipButtonEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setManualPipButtonEnabled(it) } }
                    )
                }
            }

            // =================================================
            // DATA & BUFFERING
            // =================================================
            item { SectionHeader(text = "Data & Buffering") }
            
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.Speed,
                        title = "Custom Buffer Settings",
                        subtitle = "Configure video buffering behavior",
                        onClick = { showBufferSettingsDialog = true }
                    )
                }
            }

            // =================================================
            // SEARCH & HISTORY
            // =================================================
            item { SectionHeader(text = "Search & History") }
            
            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.History,
                        title = "Save Search History",
                        subtitle = "Save your searches for quick access",
                        checked = searchHistoryEnabled,
                        onCheckedChange = { coroutineScope.launch { searchHistoryRepo.setSearchHistoryEnabled(it) } }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.TrendingUp,
                        title = "Search Suggestions",
                        subtitle = "Show suggestions while typing",
                        checked = searchSuggestionsEnabled,
                        onCheckedChange = { coroutineScope.launch { searchHistoryRepo.setSearchSuggestionsEnabled(it) } }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Storage,
                        title = "Max History Size",
                        subtitle = "Currently: $maxHistorySize searches",
                        onClick = { showHistorySizeDialog = true }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.AutoDelete,
                        title = "Auto-Delete History",
                        subtitle = if (autoDeleteHistory) "Delete after $historyRetentionDays days" else "Never delete automatically",
                        checked = autoDeleteHistory,
                        onCheckedChange = { coroutineScope.launch { searchHistoryRepo.setAutoDeleteHistory(it) } }
                    )
                    
                    if (autoDeleteHistory) {
                        Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        SettingsItem(
                            icon = Icons.Outlined.Schedule,
                            title = "Retention Period",
                            subtitle = "Delete searches older than $historyRetentionDays days",
                            onClick = { showRetentionDaysDialog = true }
                        )
                    }
                    
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.ManageSearch,
                        title = "Clear Search History",
                        subtitle = "Remove all search queries",
                        onClick = { showClearSearchDialog = true }
                    )
                }
            }

            // =================================================
            // DATA MANAGEMENT
            // =================================================
            item { SectionHeader(text = "Privacy & Data Management") }
            
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.DeleteSweep,
                        title = "Clear Watch History",
                        subtitle = "Remove all watched videos",
                        onClick = { showClearWatchHistoryDialog = true }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.FileUpload,
                        title = "Export Data",
                        subtitle = "Backup your data",
                        onClick = { exportLauncher.launch("flow_backup_${System.currentTimeMillis()}.json") }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.FileDownload,
                        title = "Import Data",
                        subtitle = "Restore from backup or import from other apps",
                        onClick = onNavigateToImport
                    )
                }
            }
            
            // =================================================
            // ABOUT
            // =================================================
            item { SectionHeader(text = "About") }
            item {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val versionName = packageInfo.versionName ?: "Unknown"
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = "App Version",
                        subtitle = versionName,
                        onClick = { }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Code,
                        title = "By A-EDev",
                        subtitle = "Visit GitHub",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/A-EDev"))
                            context.startActivity(intent)
                        }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Code,
                        title = "Powered by NewPipeExtractor",
                        subtitle = "Open source YouTube extraction library",
                        onClick = { }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Favorite,
                        title = "Support & Donations",
                        subtitle = "Help support the development of Flow",
                        onClick = onNavigateToDonations
                    )
                }
            }
        }
    }

    // Reset Brain Dialog
    if (showResetBrainDialog) {
        AlertDialog(
            onDismissRequest = { showResetBrainDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Reset Flow Personality?") },
            text = { 
                Text(
                    "This will completely wipe your 'Flow Brain' interest profile.\n\n" +
                    "• All learned topics will be forgotten.\n" +
                    "• Personality traits will reset to neutral.\n" +
                    "• Recommendations will return to default.\n\n" +
                    "This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            FlowNeuroEngine.resetBrain(context)
                            refreshBrainTrigger++
                            showResetBrainDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showResetBrainDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Clear Search History Dialog
    if (showClearSearchDialog) {
        SimpleConfirmDialog(
            title = "Clear Search History?",
            text = "Permanently delete all search history.",
            onConfirm = { coroutineScope.launch { searchHistoryRepo.clearSearchHistory(); showClearSearchDialog = false } },
            onDismiss = { showClearSearchDialog = false }
        )
    }

    // Clear Watch History Dialog
    if (showClearWatchHistoryDialog) {
        SimpleConfirmDialog(
            title = "Clear Watch History?",
            text = "Permanently delete watch history.",
            onConfirm = { coroutineScope.launch { viewHistory.clearAllHistory(); showClearWatchHistoryDialog = false } },
            onDismiss = { showClearWatchHistoryDialog = false }
        )
    }
    
    // Region Selection Dialog
    if (showRegionDialog) {
        AlertDialog(
            onDismissRequest = { showRegionDialog = false },
            title = { Text("Select Region") },
            text = {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(regionNames.toList().size) { index ->
                        val (code, name) = regionNames.toList()[index]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    coroutineScope.launch { playerPreferences.setTrendingRegion(code); showRegionDialog = false }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currentRegion == code, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showRegionDialog = false }) { Text("Cancel") } }
        )
    }
    
    // Max History Size Dialog
    if (showHistorySizeDialog) {
        var selectedSize by remember { mutableStateOf(maxHistorySize) }
        
        AlertDialog(
            onDismissRequest = { showHistorySizeDialog = false },
            icon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
            title = { Text("Max History Size") },
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
                            RadioButton(selected = selectedSize == size, onClick = { selectedSize = size })
                            Spacer(Modifier.width(8.dp))
                            Text("$size searches")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch { searchHistoryRepo.setMaxHistorySize(selectedSize); showHistorySizeDialog = false }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showHistorySizeDialog = false }) { Text("Cancel") } }
        )
    }
    
    // Retention Days Dialog
    if (showRetentionDaysDialog) {
        var selectedDays by remember { mutableStateOf(historyRetentionDays) }
        
        AlertDialog(
            onDismissRequest = { showRetentionDaysDialog = false },
            icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            title = { Text("Retention Period") },
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
                            RadioButton(selected = selectedDays == days, onClick = { selectedDays = days })
                            Spacer(Modifier.width(8.dp))
                            Text(when (days) {
                                7 -> "1 week"
                                30 -> "1 month"
                                90 -> "3 months"
                                180 -> "6 months"
                                365 -> "1 year"
                                else -> "$days days"
                            })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch { searchHistoryRepo.setHistoryRetentionDays(selectedDays); showRetentionDaysDialog = false }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRetentionDaysDialog = false }) { Text("Cancel") } }
        )
    }

    // Video Quality Dialog
    if (showVideoQualityDialog) {
        var selectedWifiQuality by remember { mutableStateOf(wifiQuality) }
        var selectedCellularQuality by remember { mutableStateOf(cellularQuality) }
        
        val qualities = listOf(
            VideoQuality.Q_144p, VideoQuality.Q_240p, VideoQuality.Q_360p,
            VideoQuality.Q_480p, VideoQuality.Q_720p, VideoQuality.Q_1080p,
            VideoQuality.Q_1440p, VideoQuality.Q_2160p, VideoQuality.AUTO
        )
        
        AlertDialog(
            onDismissRequest = { showVideoQualityDialog = false },
            icon = { Icon(Icons.Outlined.HighQuality, contentDescription = null) },
            title = { Text("Video Quality") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Wi-Fi Quality", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                        items(qualities.size) { index ->
                            val quality = qualities[index]
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { selectedWifiQuality = quality }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedWifiQuality == quality, onClick = { selectedWifiQuality = quality })
                                Spacer(Modifier.width(8.dp))
                                Text(quality.label)
                            }
                        }
                    }
                    
                    Text("Cellular Quality", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                        items(qualities.size) { index ->
                            val quality = qualities[index]
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { selectedCellularQuality = quality }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedCellularQuality == quality, onClick = { selectedCellularQuality = quality })
                                Spacer(Modifier.width(8.dp))
                                Text(quality.label)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        playerPreferences.setDefaultQualityWifi(selectedWifiQuality)
                        playerPreferences.setDefaultQualityCellular(selectedCellularQuality)
                        showVideoQualityDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showVideoQualityDialog = false }) { Text("Cancel") } }
        )
    }

    // Buffer Settings Dialog
    if (showBufferSettingsDialog) {
        var selectedProfile by remember { mutableStateOf(currentBufferProfile) }
        var tempMinBuffer by remember { mutableStateOf(minBufferMs.toFloat()) }
        var tempMaxBuffer by remember { mutableStateOf(maxBufferMs.toFloat()) }
        var tempPlaybackBuffer by remember { mutableStateOf(bufferForPlaybackMs.toFloat()) }
        var tempRebuffer by remember { mutableStateOf(bufferForPlaybackAfterRebufferMs.toFloat()) }
        
        // Update sliders when profile changes
        LaunchedEffect(selectedProfile) {
            if (selectedProfile != BufferProfile.CUSTOM) {
                tempMinBuffer = selectedProfile.minBuffer.toFloat()
                tempMaxBuffer = selectedProfile.maxBuffer.toFloat()
                tempPlaybackBuffer = selectedProfile.playbackBuffer.toFloat()
                tempRebuffer = selectedProfile.rebufferBuffer.toFloat()
            }
        }

        AlertDialog(
            onDismissRequest = { showBufferSettingsDialog = false },
            icon = { Icon(Icons.Outlined.Speed, contentDescription = null) },
            title = { Text("Buffer Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Profile Selector
                    Text("Performance Profile", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BufferProfile.values().filter { it != BufferProfile.CUSTOM }.forEach { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedProfile = profile }
                                    .background(if (selectedProfile == profile) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedProfile == profile,
                                    onClick = { selectedProfile = profile }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(profile.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    val desc = when(profile) {
                                        BufferProfile.AGGRESSIVE -> "Fastest start, good for stable Wi-Fi"
                                        BufferProfile.STABLE -> "Balanced, prevents stalling (Recommended)"
                                        BufferProfile.DATASAVER -> "Minimizes data usage and memory"
                                        else -> ""
                                    }
                                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    
                    Divider()
                    
                    Text("Manual Configuration (${if (selectedProfile == BufferProfile.CUSTOM) "Custom" else "Locked"})", 
                        style = MaterialTheme.typography.labelLarge, 
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (selectedProfile != BufferProfile.CUSTOM) {
                        Text(
                            "Select 'Custom' to edit values manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("Higher values prevent stalling but delay start.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    // Min Buffer
                    Column {
                        Text("Min Buffer: ${tempMinBuffer.toInt()/1000}s", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = tempMinBuffer,
                            onValueChange = { tempMinBuffer = it; selectedProfile = BufferProfile.CUSTOM },
                            valueRange = 5000f..60000f,
                            enabled = true // Always enabled to allow switching to custom implicitly? 
                                           // Actually let's make it so touching them switches to Custom.
                        )
                    }
                    
                    // Max Buffer
                    Column {
                        Text("Max Buffer: ${tempMaxBuffer.toInt()/1000}s", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = tempMaxBuffer,
                            onValueChange = { tempMaxBuffer = it; selectedProfile = BufferProfile.CUSTOM },
                            valueRange = 10000f..120000f,
                            enabled = true
                        )
                    }
                    
                    // Playback Buffer
                    Column {
                        Text("Start Playback: ${tempPlaybackBuffer.toInt()/1000}s", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = tempPlaybackBuffer,
                            onValueChange = { tempPlaybackBuffer = it; selectedProfile = BufferProfile.CUSTOM },
                            valueRange = 500f..5000f,
                            enabled = true
                        )
                    }
                    
                    // Rebuffer
                    Column {
                        Text("Resume after Rebuffer: ${tempRebuffer.toInt()/1000}s", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = tempRebuffer,
                            onValueChange = { tempRebuffer = it; selectedProfile = BufferProfile.CUSTOM },
                            valueRange = 1000f..10000f,
                            enabled = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        // Save profile first
                        playerPreferences.setBufferProfile(selectedProfile)
                        
                        // If custom, we must manually save the values because setBufferProfile only saves presets
                        if (selectedProfile == BufferProfile.CUSTOM) {
                            playerPreferences.setMinBufferMs(tempMinBuffer.toInt())
                            playerPreferences.setMaxBufferMs(tempMaxBuffer.toInt())
                            playerPreferences.setBufferForPlaybackMs(tempPlaybackBuffer.toInt())
                            playerPreferences.setBufferForPlaybackAfterRebufferMs(tempRebuffer.toInt())
                        }
                        
                        showBufferSettingsDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showBufferSettingsDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
fun BrainTraitRow(label: String, value: Double, leftLabel: String, rightLabel: String) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = value.toFloat(), // Fixed: No lambda
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(leftLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rightLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp)
    )
}

private fun formatThemeName(theme: ThemeMode): String {
    return theme.name.split("_")
        .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SimpleConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
