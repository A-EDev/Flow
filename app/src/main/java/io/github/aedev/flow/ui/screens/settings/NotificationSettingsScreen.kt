package io.github.aedev.flow.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
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
}
