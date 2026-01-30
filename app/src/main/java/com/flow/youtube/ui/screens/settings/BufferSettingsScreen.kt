package com.flow.youtube.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flow.youtube.data.local.BufferProfile
import com.flow.youtube.data.local.PlayerPreferences
import kotlinx.coroutines.launch

@Composable
fun BufferSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }
    
    // Buffer Settings
    val minBufferMs by playerPreferences.minBufferMs.collectAsState(initial = 30000)
    val maxBufferMs by playerPreferences.maxBufferMs.collectAsState(initial = 100000)
    val bufferForPlaybackMs by playerPreferences.bufferForPlaybackMs.collectAsState(initial = 1000)
    val bufferForPlaybackAfterRebufferMs by playerPreferences.bufferForPlaybackAfterRebufferMs.collectAsState(initial = 2500)
    val currentBufferProfile by playerPreferences.bufferProfile.collectAsState(initial = BufferProfile.STABLE)
    
    // Local state for sliders when in custom mode
    var tempMinBuffer by remember { mutableFloatStateOf(minBufferMs.toFloat()) }
    var tempMaxBuffer by remember { mutableFloatStateOf(maxBufferMs.toFloat()) }
    var tempPlaybackBuffer by remember { mutableFloatStateOf(bufferForPlaybackMs.toFloat()) }
    var tempRebuffer by remember { mutableFloatStateOf(bufferForPlaybackAfterRebufferMs.toFloat()) }
    
    // Sync local state when preferences change from external source or profile switch
    LaunchedEffect(currentBufferProfile, minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs) {
         if (currentBufferProfile != BufferProfile.CUSTOM) {
            tempMinBuffer = currentBufferProfile.minBuffer.toFloat()
            tempMaxBuffer = currentBufferProfile.maxBuffer.toFloat()
            tempPlaybackBuffer = currentBufferProfile.playbackBuffer.toFloat()
            tempRebuffer = currentBufferProfile.rebufferBuffer.toFloat()
        } else {
            // Only update from prefs if we are already in custom logic, usually to sync initial state
            // But we want to avoid overwriting user dragging, so assume prefs source of truth on load
            if (minBufferMs.toFloat() != tempMinBuffer) tempMinBuffer = minBufferMs.toFloat()
            if (maxBufferMs.toFloat() != tempMaxBuffer) tempMaxBuffer = maxBufferMs.toFloat()
            if (bufferForPlaybackMs.toFloat() != tempPlaybackBuffer) tempPlaybackBuffer = bufferForPlaybackMs.toFloat()
            if (bufferForPlaybackAfterRebufferMs.toFloat() != tempRebuffer) tempRebuffer = bufferForPlaybackAfterRebufferMs.toFloat()
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
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                    Text(
                        text = "Buffer Settings",
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
                Text(
                    text = "Configure video buffering behavior to optimize for your network connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Profile Selection
            item { SectionHeader(text = "Performance Profile") }
            
            item {
                SettingsGroup {
                    BufferProfile.values().filter { it != BufferProfile.CUSTOM }.forEachIndexed { index, profile ->
                        val isSelected = currentBufferProfile == profile
                        ProfileSelectionItem(
                            title = formatProfileName(profile),
                            subtitle = getProfileDescription(profile),
                            isSelected = isSelected,
                            onClick = { 
                                coroutineScope.launch { 
                                    playerPreferences.setBufferProfile(profile)
                                    // Also explicitly set values to ensure player picks them up immediately
                                    playerPreferences.setMinBufferMs(profile.minBuffer)
                                    playerPreferences.setMaxBufferMs(profile.maxBuffer)
                                    playerPreferences.setBufferForPlaybackMs(profile.playbackBuffer)
                                    playerPreferences.setBufferForPlaybackAfterRebufferMs(profile.rebufferBuffer)
                                } 
                            }
                        )
                         if (index < BufferProfile.values().filter { it != BufferProfile.CUSTOM }.lastIndex) {
                            Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
            
            // Custom Profile
             item { SectionHeader(text = "Manual Configuration") }
             
             item {
                 SettingsGroup {
                     ProfileSelectionItem(
                        title = "Custom",
                        subtitle = "Manually configure buffer sizes",
                        isSelected = currentBufferProfile == BufferProfile.CUSTOM,
                        onClick = { coroutineScope.launch { playerPreferences.setBufferProfile(BufferProfile.CUSTOM) } }
                    )
                 }
             }

            if (currentBufferProfile == BufferProfile.CUSTOM) {
                item {
                    SettingsGroup {
                        Column(Modifier.padding(16.dp)) {
                            Text("Manually adjust buffer sizes. Higher values prevent stalling but may delay playback start.", 
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            Spacer(Modifier.height(16.dp))
                            
                            // Min Buffer
                            Text("Min Buffer: ${tempMinBuffer.toInt()/1000}s", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = tempMinBuffer,
                                onValueChange = { tempMinBuffer = it },
                                onValueChangeFinished = {
                                    coroutineScope.launch { playerPreferences.setMinBufferMs(tempMinBuffer.toInt()) }
                                },
                                valueRange = 1000f..60000f,
                                steps = 59
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            
                            // Max Buffer
                            Text("Max Buffer: ${tempMaxBuffer.toInt()/1000}s", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = tempMaxBuffer,
                                onValueChange = { tempMaxBuffer = it },
                                onValueChangeFinished = {
                                    coroutineScope.launch { playerPreferences.setMaxBufferMs(tempMaxBuffer.toInt()) }
                                },
                                valueRange = 30000f..180000f,
                                steps = 30
                            )
                            
                            Spacer(Modifier.height(8.dp))

                            // Playback Buffer
                            Text("Start Playback: ${tempPlaybackBuffer.toInt()/1000}s", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = tempPlaybackBuffer,
                                onValueChange = { tempPlaybackBuffer = it },
                                onValueChangeFinished = {
                                    coroutineScope.launch { playerPreferences.setBufferForPlaybackMs(tempPlaybackBuffer.toInt()) }
                                },
                                valueRange = 500f..5000f,
                                steps = 9
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            
                            // Rebuffer
                            Text("Resume after Rebuffer: ${tempRebuffer.toInt()/1000}s", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = tempRebuffer,
                                onValueChange = { tempRebuffer = it },
                                onValueChangeFinished = {
                                    coroutineScope.launch { playerPreferences.setBufferForPlaybackAfterRebufferMs(tempRebuffer.toInt()) }
                                },
                                valueRange = 1000f..10000f,
                                steps = 9
                            )
                        }
                    }
                }
            } else {
                 item {
                     Text(
                        text = "Switch to 'Custom' to enable sliders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp)
                     )
                 }
            }
        }
    }
}

@Composable
fun ProfileSelectionItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
         RadioButton(
            selected = isSelected,
            onClick = null 
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


private fun formatProfileName(profile: BufferProfile): String {
    return when (profile) {
        BufferProfile.STABLE -> "Stable (Default)"
        BufferProfile.AGGRESSIVE -> "Fast Start / Aggressive"
        BufferProfile.DATASAVER -> "Data Saver / Low Latency"
        BufferProfile.CUSTOM -> "Custom"
        else -> profile.label
    }
}

private fun getProfileDescription(profile: BufferProfile): String {
    return when (profile) {
        BufferProfile.STABLE -> "Balanced start time and buffering (15s min, 50s max)"
        BufferProfile.AGGRESSIVE -> "Prioritize quick playback start (10s min, 30s max)"
        BufferProfile.DATASAVER -> "Minimize data usage with smaller buffers (12s min)"
        else -> ""
    }
}
