package io.github.aedev.flow.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonParser
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.AppUiModePreferences
import io.github.aedev.flow.data.local.DEEP_FLOW_NEVER_EXPIRES_HOURS
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.discord.DiscordPresenceRuntime
import io.github.aedev.flow.network.AppProxyManager
import io.github.aedev.flow.platform.AppUiMode
import io.github.aedev.flow.player.DeepFlowManager
import io.github.aedev.flow.ui.components.layout.topbar.FlowSearchTopBar
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.extendedColors
import io.github.aedev.flow.utils.AppLanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
    onNavigateToProxySettings: () -> Unit,
    onNavigateToVideoQuality: () -> Unit,
    onNavigateToShortsQuality: () -> Unit,
    onNavigateToContentSettings: () -> Unit,
    onNavigateToDateTimeSettings: () -> Unit,
    onNavigateToBufferSettings: () -> Unit,
    onNavigateToSearchHistory: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToUserPreferences: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToAppIconPicker: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToAutoBackup: () -> Unit,
    onNavigateToSyncDevices: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToSponsorBlockSettings: () -> Unit,
    onNavigateToDiscordSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }
    val appUiModePreferences = remember { AppUiModePreferences(context) }
    val appUiMode by appUiModePreferences.mode.collectAsStateWithLifecycle(initialValue = AppUiMode.AUTOMATIC)
    var showInterfaceModeDialog by remember { mutableStateOf(false) }
    val backupRepo =
        remember {
            io.github.aedev.flow.data.local
                .BackupRepository(context)
        }

    // Brain State
    var userBrain by remember { mutableStateOf<UserBrain?>(null) }
    var refreshBrainTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshBrainTrigger) {
        userBrain = FlowNeuroEngine.getBrainSnapshot()
    }

    var showRegionDialog by remember { mutableStateOf(false) }
    var showAppLanguageDialog by remember { mutableStateOf(false) }
    var showResetBrainDialog by remember { mutableStateOf(false) }
    // Update checker state (github flavor only)
    var isCheckingUpdate by remember { mutableStateOf(false) }
    // null = no dialog; non-null = tag string of the available update
    var updateAvailableTag by remember { mutableStateOf<String?>(null) }

    // Player preferences states
    val currentRegion by playerPreferences.trendingRegion.collectAsState(initial = "US")
    val currentAppLanguage by playerPreferences.appLanguage.collectAsState(initial = AppLanguageManager.SYSTEM_DEFAULT)
    val discordSettingsState by DiscordPresenceRuntime.settingsState.collectAsStateWithLifecycle()
    val discordSettingsSummary = discordSettingsSummaryText(discordSettingsState)

    if (showInterfaceModeDialog) {
        InterfaceModeDialog(
            selected = appUiMode,
            onSelected = { mode ->
                coroutineScope.launch { appUiModePreferences.setMode(mode) }
            },
            onDismiss = { showInterfaceModeDialog = false },
        )
    }

    // Deep Flow state
    val deepFlowActive by playerPreferences.deepFlowActive.collectAsState(initial = false)
    val deepFlowActivatedAt by playerPreferences.deepFlowActivatedAt.collectAsState(initial = 0L)
    val deepFlowExpireHours by playerPreferences.deepFlowExpireHours.collectAsState(initial = 4)
    val deepFlowSaveHistory by playerPreferences.deepFlowSaveToHistory.collectAsState(initial = false)
    var showDeepFlowDurationDialog by remember { mutableStateOf(false) }

    val deepFlowRemainingLabel: String? =
        remember(deepFlowActive, deepFlowActivatedAt, deepFlowExpireHours) {
            if (!deepFlowActive || deepFlowActivatedAt == 0L || deepFlowExpireHours == DEEP_FLOW_NEVER_EXPIRES_HOURS) return@remember null
            val expiresAt = deepFlowActivatedAt + deepFlowExpireHours * 3_600_000L
            val remainingMs = expiresAt - System.currentTimeMillis()
            if (remainingMs <= 0) return@remember null
            val remainingMins = remainingMs / 60_000
            if (remainingMins < 60) {
                context.getString(R.string.duration_minutes_short, remainingMins)
            } else {
                context.getString(
                    R.string.duration_hours_minutes_short,
                    remainingMins / 60,
                    remainingMins % 60,
                )
            }
        }

    // Optimize Region Dialog: compute list only once
    val regionOptions = remember { regionPickerOptions() }
    val appLanguageOptions = remember { AppLanguageManager.getSupportedLanguages() }
    val currentAppLanguageLabel =
        remember(currentAppLanguage, appLanguageOptions) {
            val normalizedLanguage = AppLanguageManager.normalizeLanguageTag(currentAppLanguage)
            if (normalizedLanguage == AppLanguageManager.SYSTEM_DEFAULT) {
                context.getString(R.string.settings_language_system_default)
            } else {
                appLanguageOptions.firstOrNull { it.tag == normalizedLanguage }?.localizedName
                    ?: AppLanguageManager.getLanguageLabel(normalizedLanguage)
            }
        }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    val onCheckForUpdatesClick: () -> Unit = {
        if (BuildConfig.UPDATER_ENABLED && !isCheckingUpdate) {
            isCheckingUpdate = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val client = AppProxyManager.applyTo(OkHttpClient.Builder()).build()
                    val request =
                        Request
                            .Builder()
                            .url("https://api.github.com/repos/A-EDev/Flow/releases/latest")
                            .header("Accept", "application/vnd.github.v3+json")
                            .build()
                    val response = client.newCall(request).execute()
                    withContext(Dispatchers.Main) {
                        isCheckingUpdate = false
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (body != null) {
                                val json = JsonParser.parseString(body).asJsonObject
                                val latestTag = json.get("tag_name").asString
                                val cleanLatest = latestTag.removePrefix("v")
                                val cleanCurrent = BuildConfig.VERSION_NAME.removePrefix("v")
                                val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
                                val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
                                var isNewer = false
                                val size = maxOf(latestParts.size, currentParts.size)
                                for (i in 0 until size) {
                                    val l = latestParts.getOrNull(i) ?: 0
                                    val c = currentParts.getOrNull(i) ?: 0
                                    if (l > c) {
                                        isNewer = true
                                        break
                                    }
                                    if (l < c) break
                                }
                                if (isNewer) {
                                    updateAvailableTag = latestTag
                                } else {
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.flow_is_up_to_date),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }
                        } else {
                            android.widget.Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.update_check_failed),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isCheckingUpdate = false
                        android.widget.Toast
                            .makeText(
                                context,
                                context.getString(R.string.update_check_failed),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            }
        }
    }

    // Section label strings for the search index
    val secFlowEngine = stringResource(R.string.settings_flow_engine_header)
    val secAppearance = stringResource(R.string.settings_header_appearance)
    val secContentPlayback = stringResource(R.string.settings_header_content_playback)
    val secNotifications = stringResource(R.string.settings_header_notifications)
    val secDataManagement = stringResource(R.string.settings_header_data_management)
    val secAbout = stringResource(R.string.settings_header_about)

    val allSettingsEntries =
        listOf(
            SettingSearchEntry(
                Icons.Outlined.Psychology,
                stringResource(R.string.flow_control_center),
                stringResource(R.string.neural_interest_map_subtitle),
                secFlowEngine,
                onNavigateToPersonality,
            ),
            SettingSearchEntry(
                Icons.Outlined.Palette,
                stringResource(R.string.settings_item_theme),
                "",
                secAppearance,
                onNavigateToAppearance,
            ),
            SettingSearchEntry(
                Icons.Outlined.Tv,
                stringResource(R.string.settings_item_interface_mode),
                stringResource(R.string.settings_item_interface_mode_subtitle),
                secAppearance,
            ) { showInterfaceModeDialog = true },
            SettingSearchEntry(
                Icons.Outlined.Language,
                stringResource(R.string.settings_item_app_language),
                currentAppLanguageLabel,
                secAppearance,
            ) { showAppLanguageDialog = true },
            SettingSearchEntry(
                Icons.Outlined.AppShortcut,
                stringResource(R.string.settings_item_app_icon),
                stringResource(R.string.settings_item_app_icon_subtitle),
                secAppearance,
                onNavigateToAppIconPicker,
            ),
            SettingSearchEntry(
                Icons.Outlined.Tune,
                stringResource(R.string.settings_item_player_appearance),
                stringResource(R.string.settings_item_player_appearance_subtitle),
                secAppearance,
                onNavigateToPlayerAppearance,
            ),
            SettingSearchEntry(
                Icons.Outlined.GridView,
                stringResource(R.string.settings_item_content_display),
                stringResource(R.string.settings_item_content_display_subtitle),
                secAppearance,
                onNavigateToContentSettings,
            ),
            SettingSearchEntry(
                Icons.Outlined.Schedule,
                stringResource(R.string.settings_item_datetime),
                stringResource(R.string.settings_item_datetime_subtitle),
                secAppearance,
                onNavigateToDateTimeSettings,
            ),
            SettingSearchEntry(
                Icons.Outlined.FilterAlt,
                stringResource(R.string.settings_item_content_prefs),
                stringResource(R.string.settings_item_content_prefs_subtitle),
                secContentPlayback,
                onNavigateToUserPreferences,
            ),
            SettingSearchEntry(
                Icons.Outlined.PlayCircle,
                stringResource(R.string.settings_item_player),
                stringResource(R.string.settings_item_player_subtitle),
                secContentPlayback,
                onNavigateToPlayerSettings,
            ),
            SettingSearchEntry(
                Icons.Outlined.Share,
                stringResource(R.string.discord_presence_title),
                discordSettingsSummary,
                secContentPlayback,
                onNavigateToDiscordSettings,
            ),
            SettingSearchEntry(
                Icons.Outlined.Public,
                stringResource(R.string.settings_item_proxy),
                stringResource(R.string.settings_item_proxy_subtitle),
                secContentPlayback,
                onNavigateToProxySettings,
            ),
            SettingSearchEntry(
                R.drawable.ic_block,
                stringResource(R.string.sb_settings_title),
                stringResource(R.string.sb_settings_subtitle),
                secContentPlayback,
                onNavigateToSponsorBlockSettings,
            ),
            SettingSearchEntry(
                Icons.Outlined.HighQuality,
                stringResource(R.string.settings_item_quality),
                stringResource(R.string.settings_item_quality_subtitle),
                secContentPlayback,
                onNavigateToVideoQuality,
            ),
            SettingSearchEntry(
                Icons.Outlined.Slideshow,
                stringResource(R.string.shorts_quality_settings_title),
                stringResource(R.string.shorts_quality_settings_subtitle),
                secContentPlayback,
                onNavigateToShortsQuality,
            ),
            SettingSearchEntry(
                Icons.Outlined.Speed,
                stringResource(R.string.settings_item_buffer),
                stringResource(R.string.settings_item_buffer_subtitle),
                secContentPlayback,
                onNavigateToBufferSettings,
            ),
            SettingSearchEntry(
                Icons.Outlined.Download,
                stringResource(R.string.settings_item_downloads),
                stringResource(R.string.settings_item_downloads_subtitle),
                secContentPlayback,
                onNavigateToDownloads,
            ),
            SettingSearchEntry(
                Icons.Outlined.TrendingUp,
                stringResource(R.string.settings_item_region),
                REGION_NAMES[currentRegion] ?: currentRegion,
                secContentPlayback,
            ) { showRegionDialog = true },
            SettingSearchEntry(
                Icons.Outlined.NotificationsNone,
                stringResource(R.string.settings_item_notifications),
                stringResource(R.string.settings_item_notifications_subtitle),
                secNotifications,
                onNavigateToNotifications,
            ),
            SettingSearchEntry(
                Icons.Outlined.History,
                stringResource(R.string.settings_item_search_history),
                stringResource(R.string.settings_item_search_history_subtitle),
                secDataManagement,
                onNavigateToSearchHistory,
            ),
            SettingSearchEntry(
                Icons.Outlined.Schedule,
                stringResource(R.string.settings_item_time_management),
                stringResource(R.string.settings_item_time_management_subtitle),
                secDataManagement,
                onNavigateToTimeManagement,
            ),
            SettingSearchEntry(
                Icons.Outlined.FileUpload,
                stringResource(R.string.settings_item_export_data),
                stringResource(R.string.settings_item_export_data_subtitle),
                secDataManagement,
                onNavigateToExport,
            ),
            SettingSearchEntry(
                Icons.Outlined.FileDownload,
                stringResource(R.string.settings_item_import_data),
                stringResource(R.string.settings_item_import_data_subtitle),
                secDataManagement,
                onNavigateToImport,
            ),
            SettingSearchEntry(
                Icons.Outlined.Schedule,
                stringResource(R.string.auto_backup_title),
                stringResource(R.string.auto_backup_subtitle),
                secDataManagement,
                onNavigateToAutoBackup,
            ),
            SettingSearchEntry(
                Icons.Outlined.Devices,
                stringResource(R.string.sync_devices_title),
                stringResource(R.string.sync_devices_subtitle),
                secDataManagement,
                onNavigateToSyncDevices,
            ),
            SettingSearchEntry(
                Icons.Outlined.Info,
                stringResource(R.string.settings_item_about_flow),
                stringResource(R.string.settings_item_about_flow_subtitle),
                secAbout,
                onNavigateToAbout,
            ),
            SettingSearchEntry(
                Icons.Outlined.BugReport,
                stringResource(R.string.settings_item_diagnostics),
                stringResource(R.string.settings_item_diagnostics_subtitle),
                secAbout,
                onNavigateToDiagnostics,
            ),
            SettingSearchEntry(
                Icons.Outlined.VolunteerActivism,
                stringResource(R.string.settings_item_support),
                stringResource(R.string.settings_item_support_subtitle),
                secAbout,
                onNavigateToDonations,
            ),
        ) +
            if (BuildConfig.UPDATER_ENABLED) {
                listOf(
                    SettingSearchEntry(
                        Icons.Outlined.Update,
                        stringResource(R.string.check_for_updates),
                        stringResource(R.string.check_for_updates_subtitle),
                        secAbout,
                        onCheckForUpdatesClick,
                    ),
                )
            } else {
                emptyList()
            }
    val filteredEntries =
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            allSettingsEntries.filter { entry ->
                entry.title.contains(searchQuery, ignoreCase = true) ||
                    entry.subtitle.contains(searchQuery, ignoreCase = true) ||
                    entry.sectionLabel.contains(searchQuery, ignoreCase = true)
            }
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            if (isSearchActive) {
                FlowSearchTopBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        isSearchActive = false
                        searchQuery = ""
                    },
                    placeholder = stringResource(R.string.ui_search_settings),
                )
            } else {
                FlowTopBar(
                    title = stringResource(R.string.settings_title),
                    onBack = onNavigateBack,
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.ui_search_settings))
                        }
                    },
                )
            }
        },
        modifier = modifier,
    ) { paddingValues ->
        if (isSearchActive && searchQuery.isNotBlank()) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (filteredEntries.isEmpty()) {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_search_no_results, searchQuery),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(filteredEntries.size) { index ->
                        SettingsSearchResultItem(
                            entry = filteredEntries[index],
                            onNavigate = {
                                isSearchActive = false
                                searchQuery = ""
                                filteredEntries[index].onClick()
                            },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // =================================================
// 🧠 MY FLOW PERSONALITY (FLOW EXCLUSIVE FEATURE)
// =================================================
                item {
                    Text(
                        text = stringResource(R.string.settings_flow_engine_header),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 16.dp),
                    )
                }

                item {
                    val persona = if (userBrain != null) FlowNeuroEngine.getPersona(userBrain!!) else null

                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clickable(onClick = onNavigateToPersonality),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp),
                        ) {
                            // 1. Background Layer (Gradient)
                            Box(
                                modifier =
                                    Modifier
                                        .matchParentSize()
                                        .background(
                                            brush =
                                                Brush.linearGradient(
                                                    colors =
                                                        listOf(
                                                            MaterialTheme.colorScheme.primary,
                                                            MaterialTheme.colorScheme.primaryContainer,
                                                        ),
                                                ),
                                        ),
                            )
                            // 2. Background Decor (Abstract Shapes)
                            Canvas(modifier = Modifier.matchParentSize()) {
                                // Top Right Circle
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.1f),
                                    radius = size.width * 0.5f,
                                    center = Offset(size.width, 0f),
                                )
                                // Bottom Left Blob
                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.05f),
                                    radius = size.width * 0.3f,
                                    center = Offset(0f, size.height),
                                )
                            }

                            // 2. Huge Emoji Icon (Watermark style)
                            if (persona != null) {
                                Text(
                                    text = persona.icon, // e.g., 🤿 or 🧭
                                    fontSize = 120.sp,
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 20.dp, y = 20.dp)
                                            .alpha(0.15f),
                                )
                            }

                            // 4. Main Content
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                // Header Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    // Badge
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_active_learning),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }

                                    // Reset Button (Subtle)
                                    IconButton(
                                        onClick = { showResetBrainDialog = true },
                                        modifier =
                                            Modifier
                                                .size(32.dp)
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.2f), CircleShape),
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = stringResource(R.string.settings_reset_everything),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                // Persona Info
                                if (persona != null) {
                                    Column {
                                        Text(
                                            text = stringResource(persona.titleRes),
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(persona.descriptionRes),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                } else {
                                    // Loading State
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(R.string.settings_analyzing_interactions),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }

                                // Bottom CTA
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_view_analytics),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                // DEEP FLOW MODE
                item {
                    Spacer(Modifier.height(12.dp))
                    SettingsGroup {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            DeepFlowManager.toggle(context)
                                        }
                                    }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VisibilityOff,
                                contentDescription = null,
                                tint =
                                    if (deepFlowActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.deep_flow_mode_title),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (deepFlowActive && deepFlowRemainingLabel != null) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp),
                                        ) {
                                            Text(
                                                text =
                                                    stringResource(
                                                        R.string.deep_flow_learning_paused,
                                                    ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text =
                                        when {
                                            deepFlowActive && deepFlowRemainingLabel != null -> {
                                                stringResource(
                                                    R.string.deep_flow_expires_in,
                                                    deepFlowRemainingLabel,
                                                )
                                            }

                                            deepFlowActive && deepFlowExpireHours == DEEP_FLOW_NEVER_EXPIRES_HOURS -> {
                                                stringResource(
                                                    R.string.deep_flow_active_until_disabled,
                                                )
                                            }

                                            else -> {
                                                stringResource(R.string.deep_flow_mode_subtitle)
                                            }
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = deepFlowActive,
                                onCheckedChange = { enabled ->
                                    coroutineScope.launch {
                                        DeepFlowManager.setEnabled(context, enabled)
                                    }
                                },
                            )
                        }

                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showDeepFlowDurationDialog = true }
                                    .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.deep_flow_expire_duration_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text =
                                        stringResource(
                                            R.string.deep_flow_expire_duration_subtitle,
                                            deepFlowExpireHours.let { hours ->
                                                when (hours) {
                                                    DEEP_FLOW_NEVER_EXPIRES_HOURS -> {
                                                        context.getString(R.string.deep_flow_duration_never)
                                                    }

                                                    1 -> {
                                                        context.getString(R.string.deep_flow_duration_1h)
                                                    }

                                                    2 -> {
                                                        context.getString(R.string.deep_flow_duration_2h)
                                                    }

                                                    4 -> {
                                                        context.getString(R.string.deep_flow_duration_4h)
                                                    }

                                                    6 -> {
                                                        context.getString(R.string.deep_flow_duration_6h)
                                                    }

                                                    8 -> {
                                                        context.getString(R.string.deep_flow_duration_8h)
                                                    }

                                                    12 -> {
                                                        context.getString(R.string.deep_flow_duration_12h)
                                                    }

                                                    24 -> {
                                                        context.getString(R.string.deep_flow_duration_24h)
                                                    }

                                                    else -> {
                                                        context.resources.getQuantityString(
                                                            R.plurals.deep_flow_duration_hours,
                                                            hours,
                                                            hours,
                                                        )
                                                    }
                                                }
                                            },
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.deep_flow_save_history_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = stringResource(R.string.deep_flow_save_history_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = deepFlowSaveHistory,
                                onCheckedChange = { enabled ->
                                    coroutineScope.launch {
                                        playerPreferences.setDeepFlowSaveToHistory(enabled)
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // =================================================
                // APPEARANCE
                // =================================================
                item { SectionHeader(text = stringResource(R.string.settings_header_appearance)) }
                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.Palette,
                            title = stringResource(R.string.settings_item_theme),
                            subtitle = stringResource(getThemeNameRes(currentTheme)),
                            onClick = onNavigateToAppearance,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Tv,
                            title = stringResource(R.string.settings_item_interface_mode),
                            subtitle = stringResource(R.string.settings_item_interface_mode_subtitle),
                            onClick = { showInterfaceModeDialog = true },
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Language,
                            title = stringResource(R.string.settings_item_app_language),
                            subtitle = currentAppLanguageLabel,
                            onClick = { showAppLanguageDialog = true },
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.AppShortcut,
                            title = stringResource(R.string.settings_item_app_icon),
                            subtitle = stringResource(R.string.settings_item_app_icon_subtitle),
                            onClick = onNavigateToAppIconPicker,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Tune,
                            title = stringResource(R.string.settings_item_player_appearance),
                            subtitle = stringResource(R.string.settings_item_player_appearance_subtitle),
                            onClick = onNavigateToPlayerAppearance,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.GridView,
                            title = stringResource(R.string.settings_item_content_display),
                            subtitle = stringResource(R.string.settings_item_content_display_subtitle),
                            onClick = onNavigateToContentSettings,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Schedule,
                            title = stringResource(R.string.settings_item_datetime),
                            subtitle = stringResource(R.string.settings_item_datetime_subtitle),
                            onClick = onNavigateToDateTimeSettings,
                        )
                    }
                }

                // =================================================
                // CONTENT & PLAYBACK
                // =================================================
                item { SectionHeader(text = stringResource(R.string.settings_header_content_playback)) }

                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.FilterAlt,
                            title = stringResource(R.string.settings_item_content_prefs),
                            subtitle = stringResource(R.string.settings_item_content_prefs_subtitle),
                            onClick = onNavigateToUserPreferences,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.PlayCircle,
                            title = stringResource(R.string.settings_item_player),
                            subtitle = stringResource(R.string.settings_item_player_subtitle),
                            onClick = onNavigateToPlayerSettings,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Share,
                            title = stringResource(R.string.discord_presence_title),
                            subtitle = discordSettingsSummary,
                            onClick = onNavigateToDiscordSettings,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Public,
                            title = stringResource(R.string.settings_item_proxy),
                            subtitle = stringResource(R.string.settings_item_proxy_subtitle),
                            onClick = onNavigateToProxySettings,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = painterResource(R.drawable.ic_block),
                            title = stringResource(R.string.sb_settings_title),
                            subtitle = stringResource(R.string.sb_settings_subtitle),
                            onClick = onNavigateToSponsorBlockSettings,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.HighQuality,
                            title = stringResource(R.string.settings_item_quality),
                            subtitle = stringResource(R.string.settings_item_quality_subtitle),
                            onClick = onNavigateToVideoQuality,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Slideshow,
                            title = stringResource(R.string.shorts_quality_settings_title),
                            subtitle = stringResource(R.string.shorts_quality_settings_subtitle),
                            onClick = onNavigateToShortsQuality,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Speed,
                            title = stringResource(R.string.settings_item_buffer),
                            subtitle = stringResource(R.string.settings_item_buffer_subtitle),
                            onClick = onNavigateToBufferSettings,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Download,
                            title = stringResource(R.string.settings_item_downloads),
                            subtitle = stringResource(R.string.settings_item_downloads_subtitle),
                            onClick = onNavigateToDownloads,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.TrendingUp,
                            title = stringResource(R.string.settings_item_region),
                            subtitle = REGION_NAMES[currentRegion] ?: currentRegion,
                            onClick = { showRegionDialog = true },
                        )
                    }
                }

                // =================================================
                // NOTIFICATIONS
                // =================================================
                item { SectionHeader(text = stringResource(R.string.settings_header_notifications)) }

                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.NotificationsNone,
                            title = stringResource(R.string.settings_item_notifications),
                            subtitle = stringResource(R.string.settings_item_notifications_subtitle),
                            onClick = onNavigateToNotifications,
                        )
                    }
                }

                // =================================================
                // DATA MANAGEMENT
                // =================================================
                item {
                    SectionHeader(
                        text = stringResource(R.string.settings_header_data_management),
                    )
                }

                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.History,
                            title = stringResource(R.string.settings_item_search_history),
                            subtitle = stringResource(R.string.settings_item_search_history_subtitle),
                            onClick = onNavigateToSearchHistory,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Schedule,
                            title = stringResource(R.string.settings_item_time_management),
                            subtitle = stringResource(R.string.settings_item_time_management_subtitle),
                            onClick = onNavigateToTimeManagement,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.FileUpload,
                            title = stringResource(R.string.settings_item_export_data),
                            subtitle = stringResource(R.string.settings_item_export_data_subtitle),
                            onClick = onNavigateToExport,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.FileDownload,
                            title = stringResource(R.string.settings_item_import_data),
                            subtitle = stringResource(R.string.settings_item_import_data_subtitle),
                            onClick = onNavigateToImport,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Schedule,
                            title = stringResource(R.string.auto_backup_title),
                            subtitle = stringResource(R.string.auto_backup_subtitle),
                            onClick = onNavigateToAutoBackup,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Devices,
                            title = stringResource(R.string.sync_devices_title),
                            subtitle = stringResource(R.string.sync_devices_subtitle),
                            onClick = onNavigateToSyncDevices,
                        )
                    }
                }

                // =================================================
                // ABOUT
                // =================================================
                item { SectionHeader(text = stringResource(R.string.settings_header_about)) }
                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.Info,
                            title = stringResource(R.string.settings_item_about_flow),
                            subtitle = stringResource(R.string.settings_item_about_flow_subtitle),
                            onClick = onNavigateToAbout,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.BugReport,
                            title = stringResource(R.string.settings_item_diagnostics),
                            subtitle = stringResource(R.string.settings_item_diagnostics_subtitle),
                            onClick = onNavigateToDiagnostics,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        if (BuildConfig.UPDATER_ENABLED) {
                            SettingsItem(
                                icon = if (isCheckingUpdate) Icons.Outlined.Sync else Icons.Outlined.Update,
                                title = stringResource(R.string.check_for_updates),
                                subtitle =
                                    if (isCheckingUpdate) {
                                        stringResource(R.string.checking_for_updates)
                                    } else {
                                        stringResource(R.string.check_for_updates_subtitle)
                                    },
                                onClick = onCheckForUpdatesClick,
                            )
                            HorizontalDivider(
                                Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        SettingsItem(
                            icon = Icons.Outlined.VolunteerActivism,
                            title = stringResource(R.string.settings_item_support),
                            subtitle = stringResource(R.string.settings_item_support_subtitle),
                            onClick = onNavigateToDonations,
                        )
                    }
                }
            }
        }
    }

    if (showDeepFlowDurationDialog) {
        val durationOptions =
            listOf(
                DEEP_FLOW_NEVER_EXPIRES_HOURS to stringResource(R.string.deep_flow_duration_never),
                1 to stringResource(R.string.deep_flow_duration_1h),
                2 to stringResource(R.string.deep_flow_duration_2h),
                4 to stringResource(R.string.deep_flow_duration_4h),
                6 to stringResource(R.string.deep_flow_duration_6h),
                8 to stringResource(R.string.deep_flow_duration_8h),
                12 to stringResource(R.string.deep_flow_duration_12h),
                24 to stringResource(R.string.deep_flow_duration_24h),
            )
        AlertDialog(
            onDismissRequest = { showDeepFlowDurationDialog = false },
            icon = { Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.deep_flow_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.deep_flow_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    durationOptions.forEach { (hours, label) ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            playerPreferences.setDeepFlowExpireHours(hours)
                                        }
                                        showDeepFlowDurationDialog = false
                                    }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = deepFlowExpireHours == hours,
                                onClick = {
                                    coroutineScope.launch {
                                        playerPreferences.setDeepFlowExpireHours(hours)
                                    }
                                    showDeepFlowDurationDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDeepFlowDurationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showResetBrainDialog) {
        AlertDialog(
            onDismissRequest = { showResetBrainDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.settings_reset_brain_title)) },
            text = {
                Text(
                    stringResource(R.string.settings_reset_brain_body),
                    style = MaterialTheme.typography.bodyMedium,
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
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_reset_everything)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetBrainDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Update Available Dialog (github flavor only)
    if (BuildConfig.UPDATER_ENABLED) {
        val tag = updateAvailableTag
        if (tag != null) {
            AlertDialog(
                onDismissRequest = { updateAvailableTag = null },
                icon = { Icon(Icons.Outlined.Update, null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(stringResource(R.string.new_update_available), fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        stringResource(R.string.update_available_template, tag),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        updateAvailableTag = null
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/A-EDev/Flow/releases/latest"))
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.download))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updateAvailableTag = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    if (showAppLanguageDialog) {
        val languageOptions =
            remember(appLanguageOptions) {
                listOf(
                    PickerOption(
                        key = AppLanguageManager.SYSTEM_DEFAULT,
                        label = context.getString(R.string.settings_language_system_default),
                        secondaryLabel = context.getString(R.string.settings_item_app_language_subtitle),
                    ),
                ) +
                    appLanguageOptions.map { option ->
                        PickerOption(
                            key = option.tag,
                            label = option.nativeName,
                            secondaryLabel = option.localizedName.takeIf { it != option.nativeName },
                        )
                    }
            }
        SearchablePickerDialog(
            title = stringResource(R.string.settings_language_dialog_title),
            options = languageOptions,
            selectedKey = AppLanguageManager.normalizeLanguageTag(currentAppLanguage),
            onSelect = { tag ->
                coroutineScope.launch {
                    playerPreferences.setAppLanguage(tag)
                    AppLanguageManager.saveLanguageTag(context, tag)
                    showAppLanguageDialog = false
                    AppLanguageManager.activityContext(context)?.recreate()
                }
            },
            onDismiss = { showAppLanguageDialog = false },
        )
    }

    if (showRegionDialog) {
        SearchablePickerDialog(
            title = stringResource(R.string.settings_region_dialog_title),
            options = regionOptions,
            selectedKey = currentRegion,
            onSelect = { code ->
                coroutineScope.launch {
                    playerPreferences.setTrendingRegion(code)
                    showRegionDialog = false
                }
            },
            onDismiss = { showRegionDialog = false },
            listMaxHeight = 260.dp,
        )
    }
}

private fun getThemeNameRes(theme: ThemeMode): Int =
    when (theme) {
        ThemeMode.LIGHT -> R.string.theme_name_pure_light
        ThemeMode.MINT_LIGHT -> R.string.theme_name_mint_fresh
        ThemeMode.ROSE_LIGHT -> R.string.theme_name_rose_petal
        ThemeMode.SKY_LIGHT -> R.string.theme_name_sky_blue
        ThemeMode.CREAM_LIGHT -> R.string.theme_name_cream_paper
        ThemeMode.DARK -> R.string.theme_name_classic_dark
        ThemeMode.OLED -> R.string.theme_name_true_black
        ThemeMode.MIDNIGHT_BLACK -> R.string.theme_name_midnight
        ThemeMode.OCEAN_BLUE -> R.string.theme_name_deep_ocean
        ThemeMode.FOREST_GREEN -> R.string.theme_name_forest
        ThemeMode.LAVENDER_MIST -> R.string.theme_name_lavender
        ThemeMode.SUNSET_ORANGE -> R.string.theme_name_sunset
        ThemeMode.PURPLE_NEBULA -> R.string.theme_name_nebula
        ThemeMode.ROSE_GOLD -> R.string.theme_name_rose_gold
        ThemeMode.ARCTIC_ICE -> R.string.theme_name_arctic
        ThemeMode.MINTY_FRESH -> R.string.theme_name_mint_night
        ThemeMode.CRIMSON_RED -> R.string.theme_name_crimson
        ThemeMode.COSMIC_VOID -> R.string.theme_name_cosmic_void
        ThemeMode.SOLAR_FLARE -> R.string.theme_name_solar_flare
        ThemeMode.CYBERPUNK -> R.string.theme_name_cyberpunk
        ThemeMode.ROYAL_GOLD -> R.string.theme_name_royal_gold
        ThemeMode.NORDIC_HORIZON -> R.string.theme_name_nordic
        ThemeMode.ESPRESSO -> R.string.theme_name_espresso
        ThemeMode.GUNMETAL -> R.string.theme_name_gunmetal
        ThemeMode.SYSTEM -> R.string.theme_name_system_default
        ThemeMode.MONOCHROME -> R.string.theme_name_monochrome
        ThemeMode.CUSTOM -> R.string.theme_name_custom
        ThemeMode.MATERIAL_YOU -> R.string.theme_name_material_you
    }

private data class SettingSearchEntry(
    val icon: Any,
    val title: String,
    val subtitle: String,
    val sectionLabel: String,
    val onClick: () -> Unit,
)

@Composable
private fun SettingsSearchResultItem(
    entry: SettingSearchEntry,
    onNavigate: () -> Unit,
) {
    Column {
        Text(
            text = entry.sectionLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 72.dp, top = 8.dp, bottom = 2.dp),
        )
        when (entry.icon) {
            is ImageVector -> {
                SettingsItem(
                    icon = entry.icon as ImageVector,
                    title = entry.title,
                    subtitle = entry.subtitle,
                    onClick = onNavigate,
                )
            }

            is Int -> {
                SettingsItem(
                    icon = painterResource(entry.icon as Int),
                    title = entry.title,
                    subtitle = entry.subtitle,
                    onClick = onNavigate,
                )
            }
        }
        HorizontalDivider(
            Modifier.padding(start = 56.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
    }
}
