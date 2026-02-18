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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flow.youtube.R
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.data.local.VideoQuality
import kotlinx.coroutines.launch

@Composable
fun ShortsVideoQualitySettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }

    val wifiQuality by playerPreferences.shortsQualityWifi.collectAsState(initial = VideoQuality.Q_720p)
    val cellularQuality by playerPreferences.shortsQualityCellular.collectAsState(initial = VideoQuality.Q_480p)

    val qualities = listOf(
        VideoQuality.AUTO,
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
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.shorts_quality_settings_title),
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
                    text = stringResource(R.string.shorts_quality_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Wi-Fi Section
            item {
                SectionHeader(text = stringResource(R.string.shorts_quality_wifi_header))
            }

            item {
                SettingsGroup {
                    qualities.forEachIndexed { index, quality ->
                        ShortsQualitySelectionItem(
                            quality = quality,
                            isSelected = wifiQuality == quality,
                            onClick = {
                                coroutineScope.launch {
                                    playerPreferences.setShortsQualityWifi(quality)
                                }
                            }
                        )
                        if (index < qualities.size - 1) {
                            HorizontalDivider(
                                Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // Cellular Section
            item {
                SectionHeader(text = stringResource(R.string.shorts_quality_cellular_header))
            }

            item {
                SettingsGroup {
                    qualities.forEachIndexed { index, quality ->
                        ShortsQualitySelectionItem(
                            quality = quality,
                            isSelected = cellularQuality == quality,
                            onClick = {
                                coroutineScope.launch {
                                    playerPreferences.setShortsQualityCellular(quality)
                                }
                            }
                        )
                        if (index < qualities.size - 1) {
                            HorizontalDivider(
                                Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortsQualitySelectionItem(
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
        RadioButton(
            selected = isSelected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(getShortsQualityNameRes(quality)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun getShortsQualityNameRes(quality: VideoQuality): Int {
    return when (quality) {
        VideoQuality.AUTO -> R.string.quality_auto
        VideoQuality.Q_144p -> R.string.quality_144p
        VideoQuality.Q_240p -> R.string.quality_240p
        VideoQuality.Q_360p -> R.string.quality_360p
        VideoQuality.Q_480p -> R.string.quality_480p
        VideoQuality.Q_720p -> R.string.quality_720p_hd
        VideoQuality.Q_1080p -> R.string.quality_1080p_full_hd
        VideoQuality.Q_1440p -> R.string.quality_1440p_qhd
        VideoQuality.Q_2160p -> R.string.quality_2160p_4k
    }
}
