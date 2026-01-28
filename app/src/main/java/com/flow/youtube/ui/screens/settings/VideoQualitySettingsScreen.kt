package com.flow.youtube.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.data.local.VideoQuality
import kotlinx.coroutines.launch

@Composable
fun VideoQualitySettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }
    
    val wifiQuality by playerPreferences.defaultQualityWifi.collectAsState(initial = VideoQuality.Q_1080p)
    val cellularQuality by playerPreferences.defaultQualityCellular.collectAsState(initial = VideoQuality.Q_480p)
    
    val qualities = listOf(
        VideoQuality.AUTO,
        VideoQuality.Q_2160p,
        VideoQuality.Q_1440p,
        VideoQuality.Q_1080p,
        VideoQuality.Q_720p,
        VideoQuality.Q_480p,
        VideoQuality.Q_360p,
        VideoQuality.Q_240p,
        VideoQuality.Q_144p
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
                        text = "Video Quality",
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = "High definition uses more data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Wi-Fi Section
            item {
                SectionHeader(text = "Wi-Fi Quality")
            }
            
            item {
                SettingsGroup {
                    qualities.forEachIndexed { index, quality ->
                        QualitySelectionItem(
                            quality = quality,
                            isSelected = wifiQuality == quality,
                            onClick = { coroutineScope.launch { playerPreferences.setDefaultQualityWifi(quality) } }
                        )
                        if (index < qualities.size - 1) {
                            Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
            
            // Cellular Section
            item {
                SectionHeader(text = "Cellular Quality")
            }
            
            item {
                SettingsGroup {
                    qualities.forEachIndexed { index, quality ->
                        QualitySelectionItem(
                            quality = quality,
                            isSelected = cellularQuality == quality,
                            onClick = { coroutineScope.launch { playerPreferences.setDefaultQualityCellular(quality) } }
                        )
                         if (index < qualities.size - 1) {
                            Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QualitySelectionItem(
    quality: VideoQuality,
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
        // Radio button look
        RadioButton(
            selected = isSelected,
            onClick = null // Handled by row click
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = formatQualityName(quality),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatQualityName(quality: VideoQuality): String {
    return when (quality) {
        VideoQuality.AUTO -> "Auto"
        VideoQuality.Q_144p -> "144p"
        VideoQuality.Q_240p -> "240p"
        VideoQuality.Q_360p -> "360p"
        VideoQuality.Q_480p -> "480p"
        VideoQuality.Q_720p -> "720p HD"
        VideoQuality.Q_1080p -> "1080p Full HD"
        VideoQuality.Q_1440p -> "1440p QHD"
        VideoQuality.Q_2160p -> "2160p 4K"
    }
}
