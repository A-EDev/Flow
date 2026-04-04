package io.github.aedev.flow.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.notification.SubscriptionCheckWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { PlayerPreferences(context) }

    val notifNewVideos by prefs.notifNewVideosEnabled.collectAsState(initial = true)
    val notifDownloads by prefs.notifDownloadsEnabled.collectAsState(initial = true)
    val notifReminders by prefs.notifRemindersEnabled.collectAsState(initial = true)
    val notifUpdates by prefs.notifUpdatesEnabled.collectAsState(initial = true)
    val notifGeneral by prefs.notifGeneralEnabled.collectAsState(initial = true)
    val subCheckInterval by prefs.subscriptionCheckIntervalMinutes.collectAsState(initial = 360)
    var showIntervalDialog by remember { mutableStateOf(false) }

    val intervalOptions = listOf(
        15 to stringResource(R.string.notif_interval_15m),
        30 to stringResource(R.string.notif_interval_30m),
        60 to stringResource(R.string.notif_interval_1h),
        120 to stringResource(R.string.notif_interval_2h),
        180 to stringResource(R.string.notif_interval_3h),
        360 to stringResource(R.string.notif_interval_6h),
        720 to stringResource(R.string.notif_interval_12h),
        1440 to stringResource(R.string.notif_interval_24h),
    )
    val currentIntervalLabel = intervalOptions.firstOrNull { it.first == subCheckInterval }?.second
        ?: "${subCheckInterval}min"

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
                        text = stringResource(R.string.notif_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader(text = stringResource(R.string.notif_check_interval_section_header))
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.Schedule,
                        title = stringResource(R.string.notif_check_interval),
                        subtitle = stringResource(R.string.notif_check_interval_subtitle_template, currentIntervalLabel),
                        onClick = { showIntervalDialog = true }
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Subscriptions,
                        title = stringResource(R.string.notif_type_new_videos),
                        subtitle = stringResource(R.string.notif_type_new_videos_subtitle),
                        checked = notifNewVideos,
                        onCheckedChange = { coroutineScope.launch { prefs.setNotifNewVideosEnabled(it) } }
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Download,
                        title = stringResource(R.string.notif_type_downloads),
                        subtitle = stringResource(R.string.notif_type_downloads_subtitle),
                        checked = notifDownloads,
                        onCheckedChange = { coroutineScope.launch { prefs.setNotifDownloadsEnabled(it) } }
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Bedtime,
                        title = stringResource(R.string.notif_type_reminders),
                        subtitle = stringResource(R.string.notif_type_reminders_subtitle),
                        checked = notifReminders,
                        onCheckedChange = { coroutineScope.launch { prefs.setNotifRemindersEnabled(it) } }
                    )
                    if (BuildConfig.UPDATER_ENABLED) {
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        SettingsSwitchItem(
                            icon = Icons.Outlined.Update,
                            title = stringResource(R.string.notif_type_updates),
                            subtitle = stringResource(R.string.notif_type_updates_subtitle),
                            checked = notifUpdates,
                            onCheckedChange = { coroutineScope.launch { prefs.setNotifUpdatesEnabled(it) } }
                        )
                    }
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.notif_type_general),
                        subtitle = stringResource(R.string.notif_type_general_subtitle),
                        checked = notifGeneral,
                        onCheckedChange = { coroutineScope.launch { prefs.setNotifGeneralEnabled(it) } }
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.OpenInNew,
                        title = stringResource(R.string.notif_system_settings),
                        subtitle = stringResource(R.string.notif_system_settings_subtitle),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    if (showIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = {
                Text(
                    stringResource(R.string.notif_check_interval_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.notif_check_interval_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    intervalOptions.forEach { (minutes, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        prefs.setSubscriptionCheckIntervalMinutes(minutes)
                                        SubscriptionCheckWorker.schedulePeriodicCheck(context, intervalMinutes = minutes.toLong())
                                    }
                                    showIntervalDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = subCheckInterval == minutes,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }
}
