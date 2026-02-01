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
    onNavigateToPlayerSettings: () -> Unit,
    onNavigateToVideoQuality: () -> Unit,
    onNavigateToBufferSettings: () -> Unit,
    onNavigateToSearchHistory: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToUserPreferences: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }
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

    var showRegionDialog by remember { mutableStateOf(false) }
    var showResetBrainDialog by remember { mutableStateOf(false) }
    
    // Player preferences states
    val currentRegion by playerPreferences.trendingRegion.collectAsState(initial = "US")


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
            .height(180.dp) 
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
                        Icons.Default.ArrowForward, 
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
            // APPEARANCE
            // =================================================
            item { SectionHeader(text = "Appearance") }
            item {
                SettingsGroup { 
                    SettingsItem(
                        icon = Icons.Outlined.Palette,
                        title = "Theme",
                        subtitle = formatThemeName(currentTheme),
                        onClick = onNavigateToAppearance
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
                        icon = Icons.Outlined.FilterAlt,
                        title = "Content Preferences",
                        subtitle = "Block topics you don't want to see",
                        onClick = onNavigateToUserPreferences
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                         icon = Icons.Outlined.PlayCircle,
                         title = "Player Settings",
                         subtitle = "Autoplay, Background Play, SponsorBlock",
                         onClick = onNavigateToPlayerSettings
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                         icon = Icons.Outlined.HighQuality,
                         title = "Video Quality",
                         subtitle = "Resolution for Wi-Fi and Mobile",
                         onClick = onNavigateToVideoQuality
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Speed,
                        title = "Buffer Settings",
                        subtitle = "Configure video buffering behavior",
                        onClick = onNavigateToBufferSettings
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Download,
                        title = "Download Settings",
                        subtitle = "Performance, Storage, Threads",
                        onClick = onNavigateToDownloads
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.TrendingUp,
                        title = "Trending Region",
                        subtitle = regionNames[currentRegion] ?: currentRegion,
                        onClick = { showRegionDialog = true }
                    )
                }
            }
            
            // =================================================
            // DATA MANAGEMENT
            // =================================================
            item { SectionHeader(text = "Data Management") }
            
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.History,
                        title = "Search & History",
                        subtitle = "Clear history, manage suggestions",
                        onClick = onNavigateToSearchHistory
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Schedule,
                        title = "Time Management",
                        subtitle = "Usage stats, Bedtime reminders",
                        onClick = onNavigateToTimeManagement
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
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = "About Flow",
                        subtitle = "Version, License, Contributors",
                        onClick = onNavigateToAbout
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

private fun formatThemeName(theme: ThemeMode): String {
    return theme.name.split("_")
        .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
}
