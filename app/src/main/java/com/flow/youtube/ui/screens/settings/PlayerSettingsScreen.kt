package com.flow.youtube.ui.screens.settings

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flow.youtube.data.local.PlayerPreferences
import kotlinx.coroutines.launch

@Composable
fun PlayerSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }
    
    val autoplayEnabled by playerPreferences.autoplayEnabled.collectAsState(initial = true)
    val skipSilenceEnabled by playerPreferences.skipSilenceEnabled.collectAsState(initial = false)
    val sponsorBlockEnabled by playerPreferences.sponsorBlockEnabled.collectAsState(initial = false)
    val manualPipButtonEnabled by playerPreferences.manualPipButtonEnabled.collectAsState(initial = true)
    val backgroundPlayEnabled by playerPreferences.backgroundPlayEnabled.collectAsState(initial = false)

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
                        text = "Player Settings",
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
                        icon = Icons.Outlined.PictureInPicture,
                        title = "Show PiP Button",
                        subtitle = "Show manual PiP button in player controls",
                        checked = manualPipButtonEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setManualPipButtonEnabled(it) } }
                    )
                }
            }
        }
    }
}
