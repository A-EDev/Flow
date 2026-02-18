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
    onNavigateToPlayerAppearance: () -> Unit,
    onNavigateToDonations: () -> Unit,
    onNavigateToPersonality: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToTimeManagement: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToPlayerSettings: () -> Unit,
    onNavigateToVideoQuality: () -> Unit,
    onNavigateToShortsQuality: () -> Unit,
    onNavigateToContentSettings: () -> Unit,
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
                        android.widget.Toast.makeText(context, context.getString(com.flow.youtube.R.string.settings_export_success), android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, context.getString(com.flow.youtube.R.string.settings_export_failed, result.exceptionOrNull()?.message), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    var showRegionDialog by remember { mutableStateOf(false) }
    var showResetBrainDialog by remember { mutableStateOf(false) }
    
    // Player preferences states
    val currentRegion by playerPreferences.trendingRegion.collectAsState(initial = "US")

    // Optimize Region Dialog: compute list only once
    val regionList = remember { REGION_NAMES.toList() }

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
                        Icon(Icons.Default.ArrowBack, androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.btn_back))
                    }
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_title),
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
        text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_flow_engine_header),
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
            .clickable(onClick = onNavigateToPersonality),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp)
        ) {
            // 1. Background Layer (Gradient)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
            )
            // 2. Background Decor (Abstract Shapes)
            Canvas(modifier = Modifier.matchParentSize()) {
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

            // 4. Main Content
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
                            text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_active_learning),
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
                            contentDescription = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_reset_everything),
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
                            text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_analyzing_interactions),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                // Bottom CTA
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_view_analytics),
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
            item { SectionHeader(text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_header_appearance)) }
            item {
                SettingsGroup { 
                    SettingsItem(
                        icon = Icons.Outlined.Palette,
                        title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_theme),
                        subtitle = androidx.compose.ui.res.stringResource(getThemeNameRes(currentTheme)),
                        onClick = onNavigateToAppearance
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Tune,
                        title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_player_appearance),
                        subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_player_appearance_subtitle),
                        onClick = onNavigateToPlayerAppearance
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                         icon = Icons.Outlined.GridView,
                         title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_content_display),
                         subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_content_display_subtitle),
                         onClick = onNavigateToContentSettings
                    )
                }
            }

            // =================================================
            // CONTENT & PLAYBACK
            // =================================================
            item { SectionHeader(text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_header_content_playback)) }
            
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.FilterAlt,
                        title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_content_prefs),
                        subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_content_prefs_subtitle),
                        onClick = onNavigateToUserPreferences
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                         icon = Icons.Outlined.PlayCircle,
                         title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_player),
                         subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_player_subtitle),
                         onClick = onNavigateToPlayerSettings
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                         icon = Icons.Outlined.HighQuality,
                         title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_quality),
                         subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_quality_subtitle),
                         onClick = onNavigateToVideoQuality
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                         icon = Icons.Outlined.Slideshow,
                         title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.shorts_quality_settings_title),
                         subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.shorts_quality_settings_subtitle),
                         onClick = onNavigateToShortsQuality
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Speed,
                        title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_buffer),
                        subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_buffer_subtitle),
                        onClick = onNavigateToBufferSettings
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Download,
                        title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_downloads),
                        subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_downloads_subtitle),
                        onClick = onNavigateToDownloads
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.TrendingUp,
                        title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_region),
                        subtitle = REGION_NAMES[currentRegion] ?: currentRegion,
                        onClick = { showRegionDialog = true }
                    )
                }
            }
            
            // =================================================
            // DATA MANAGEMENT
            // =================================================
            item { SectionHeader(text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_header_data_management)) }
            
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.History,
                        title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_search_history),
                        subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_search_history_subtitle),
                        onClick = onNavigateToSearchHistory
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Schedule,
                        title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_time_management),
                        subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_time_management_subtitle),
                        onClick = onNavigateToTimeManagement
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.FileUpload,
                        title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_export_data),
                        subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_export_data_subtitle),
                        onClick = { exportLauncher.launch("flow_backup_${System.currentTimeMillis()}.json") }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.FileDownload,
                        title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_import_data),
                        subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_import_data_subtitle),
                        onClick = onNavigateToImport
                    )
                }
            }
            
            // =================================================
            // ABOUT
            // =================================================
            item { SectionHeader(text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_header_about)) }
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_about_flow),
                        subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_about_flow_subtitle),
                        onClick = onNavigateToAbout
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Favorite,
                        title = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_support),
                        subtitle = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_item_support_subtitle),
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
            title = { Text(androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_reset_brain_title)) },
            text = { 
                Text(
                    androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_reset_brain_body),
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
                ) { Text(androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_reset_everything)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetBrainDialog = false }) { Text(androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.cancel)) }
            }
        )
    }

    // Region Selection Dialog
    if (showRegionDialog) {
        AlertDialog(
            onDismissRequest = { showRegionDialog = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.settings_region_dialog_title)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(regionList.size) { index ->
                        val (code, name) = regionList[index]
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
            dismissButton = { TextButton(onClick = { showRegionDialog = false }) { Text(androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.cancel)) } }
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

private val REGION_NAMES = mapOf(
    "DZ" to "Algeria", "AS" to "American Samoa", "AI" to "Anguilla", "AR" to "Argentina",
    "AW" to "Aruba", "AU" to "Australia", "AT" to "Austria", "AZ" to "Azerbaijan",
    "BH" to "Bahrain", "BD" to "Bangladesh", "BY" to "Belarus", "BE" to "Belgium",
    "BM" to "Bermuda", "BO" to "Bolivia", "BA" to "Bosnia and Herzegovina", "BR" to "Brazil",
    "IO" to "British Indian Ocean Territory", "VG" to "British Virgin Islands", "BG" to "Bulgaria", "KH" to "Cambodia",
    "CA" to "Canada", "KY" to "Cayman Islands", "CL" to "Chile", "CO" to "Colombia",
    "CR" to "Costa Rica", "HR" to "Croatia", "CY" to "Cyprus", "CZ" to "Czech Republic",
    "DK" to "Denmark", "DO" to "Dominican Republic", "EC" to "Ecuador", "EG" to "Egypt",
    "SV" to "El Salvador", "EE" to "Estonia", "FK" to "Falkland Islands", "FO" to "Faroe Islands",
    "FI" to "Finland", "FR" to "France", "GF" to "French Guiana", "PF" to "French Polynesia",
    "GE" to "Georgia", "DE" to "Germany", "GH" to "Ghana", "GI" to "Gibraltar",
    "GR" to "Greece", "GL" to "Greenland", "GP" to "Guadeloupe", "GU" to "Guam",
    "GT" to "Guatemala", "HN" to "Honduras", "HK" to "Hong Kong", "HU" to "Hungary",
    "IS" to "Iceland", "IN" to "India", "ID" to "Indonesia", "IQ" to "Iraq",
    "IE" to "Ireland", "IL" to "Israel", "IT" to "Italy", "JM" to "Jamaica",
    "JP" to "Japan", "JO" to "Jordan", "KZ" to "Kazakhstan", "KE" to "Kenya",
    "KW" to "Kuwait", "LA" to "Laos", "LV" to "Latvia", "LB" to "Lebanon",
    "LY" to "Libya", "LI" to "Liechtenstein", "LT" to "Lithuania", "LU" to "Luxembourg",
    "MY" to "Malaysia", "MT" to "Malta", "MQ" to "Martinique", "YT" to "Mayotte",
    "MX" to "Mexico", "MD" to "Moldova", "ME" to "Montenegro", "MS" to "Montserrat",
    "MA" to "Morocco", "NP" to "Nepal", "NL" to "Netherlands", "NC" to "New Caledonia",
    "NZ" to "New Zealand", "NI" to "Nicaragua", "NG" to "Nigeria", "NF" to "Norfolk Island",
    "MP" to "Northern Mariana Islands", "NO" to "Norway", "OM" to "Oman", "PK" to "Pakistan",
    "PA" to "Panama", "PG" to "Papua New Guinea", "PY" to "Paraguay", "PE" to "Peru",
    "PH" to "Philippines", "PL" to "Poland", "PT" to "Portugal", "PR" to "Puerto Rico",
    "QA" to "Qatar", "RE" to "Reunion", "RO" to "Romania", "RU" to "Russia",
    "SH" to "Saint Helena", "PM" to "Saint Pierre and Miquelon", "SA" to "Saudi Arabia", "SN" to "Senegal",
    "RS" to "Serbia", "SG" to "Singapore", "SK" to "Slovakia", "SI" to "Slovenia",
    "ZA" to "South Africa", "KR" to "South Korea", "ES" to "Spain", "LK" to "Sri Lanka",
    "SJ" to "Svalbard and Jan Mayen", "SE" to "Sweden", "CH" to "Switzerland", "TW" to "Taiwan",
    "TZ" to "Tanzania", "TH" to "Thailand", "TN" to "Tunisia", "TR" to "Turkey",
    "TC" to "Turks and Caicos Islands", "UG" to "Uganda", "UA" to "Ukraine", "AE" to "United Arab Emirates",
    "GB" to "United Kingdom", "US" to "United States", "VI" to "U.S. Virgin Islands", "UY" to "Uruguay",
    "VE" to "Venezuela", "VN" to "Vietnam"
).toList().sortedBy { it.second }.toMap()

private fun getThemeNameRes(theme: ThemeMode): Int {
    return when (theme) {
        ThemeMode.LIGHT -> com.flow.youtube.R.string.theme_name_pure_light
        ThemeMode.MINT_LIGHT -> com.flow.youtube.R.string.theme_name_mint_fresh
        ThemeMode.ROSE_LIGHT -> com.flow.youtube.R.string.theme_name_rose_petal
        ThemeMode.SKY_LIGHT -> com.flow.youtube.R.string.theme_name_sky_blue
        ThemeMode.CREAM_LIGHT -> com.flow.youtube.R.string.theme_name_cream_paper
        ThemeMode.DARK -> com.flow.youtube.R.string.theme_name_classic_dark
        ThemeMode.OLED -> com.flow.youtube.R.string.theme_name_true_black
        ThemeMode.MIDNIGHT_BLACK -> com.flow.youtube.R.string.theme_name_midnight
        ThemeMode.OCEAN_BLUE -> com.flow.youtube.R.string.theme_name_deep_ocean
        ThemeMode.FOREST_GREEN -> com.flow.youtube.R.string.theme_name_forest
        ThemeMode.LAVENDER_MIST -> com.flow.youtube.R.string.theme_name_lavender
        ThemeMode.SUNSET_ORANGE -> com.flow.youtube.R.string.theme_name_sunset
        ThemeMode.PURPLE_NEBULA -> com.flow.youtube.R.string.theme_name_nebula
        ThemeMode.ROSE_GOLD -> com.flow.youtube.R.string.theme_name_rose_gold
        ThemeMode.ARCTIC_ICE -> com.flow.youtube.R.string.theme_name_arctic
        ThemeMode.MINTY_FRESH -> com.flow.youtube.R.string.theme_name_mint_night
        ThemeMode.CRIMSON_RED -> com.flow.youtube.R.string.theme_name_crimson
        ThemeMode.COSMIC_VOID -> com.flow.youtube.R.string.theme_name_cosmic_void
        ThemeMode.SOLAR_FLARE -> com.flow.youtube.R.string.theme_name_solar_flare
        ThemeMode.CYBERPUNK -> com.flow.youtube.R.string.theme_name_cyberpunk
        ThemeMode.ROYAL_GOLD -> com.flow.youtube.R.string.theme_name_royal_gold
        ThemeMode.NORDIC_HORIZON -> com.flow.youtube.R.string.theme_name_nordic
        ThemeMode.ESPRESSO -> com.flow.youtube.R.string.theme_name_espresso
        ThemeMode.GUNMETAL -> com.flow.youtube.R.string.theme_name_gunmetal
    }
}
