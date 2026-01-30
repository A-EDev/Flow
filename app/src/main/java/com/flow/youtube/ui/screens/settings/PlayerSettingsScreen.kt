package com.flow.youtube.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flow.youtube.data.local.PlayerPreferences
import kotlinx.coroutines.launch

private val audioLanguageOptions = listOf(
    "original" to "Original (Native)",
    "en" to "English",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "pt" to "Portuguese",
    "it" to "Italian",
    "ru" to "Russian",
    "ja" to "Japanese",
    "ko" to "Korean",
    "zh" to "Chinese",
    "ar" to "Arabic",
    "hi" to "Hindi",
    "nl" to "Dutch",
    "pl" to "Polish",
    "tr" to "Turkish",
    "vi" to "Vietnamese",
    "th" to "Thai",
    "id" to "Indonesian"
)

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
    val preferredAudioLanguage by playerPreferences.preferredAudioLanguage.collectAsState(initial = "original")
    
    var showAudioLanguageDialog by remember { mutableStateOf(false) }

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
            
            // Audio Settings Section
            item {
                Text(
                    text = "Audio",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SettingsGroup {
                    SettingsClickItem(
                        icon = Icons.Outlined.VolumeUp,
                        title = "Preferred Audio Language",
                        subtitle = audioLanguageOptions.find { it.first == preferredAudioLanguage }?.second 
                            ?: "Original (Native)",
                        onClick = { showAudioLanguageDialog = true }
                    )
                }
            }
        }
    }
    
    // Audio Language Selection Dialog
    if (showAudioLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showAudioLanguageDialog = false },
            title = { 
                Text(
                    "Audio Language",
                    style = MaterialTheme.typography.titleLarge
                ) 
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        "Select your preferred audio track language. If not available, the original language will be used.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    audioLanguageOptions.forEach { (code, displayName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        playerPreferences.setPreferredAudioLanguage(code)
                                    }
                                    showAudioLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferredAudioLanguage == code,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (code == "original") {
                                    Text(
                                        text = "Uses the video's native audio track",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioLanguageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsClickItem(
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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
