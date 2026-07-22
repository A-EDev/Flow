package io.github.aedev.flow.ui.screens.music.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.components.audio.EqualizerEditor
import io.github.aedev.flow.ui.components.rememberFlowSheetState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsSheet(
    onDismiss: () -> Unit
) {
    val speed by EnhancedMusicPlayerManager.playbackSpeed.collectAsState()
    val pitch by EnhancedMusicPlayerManager.playbackPitch.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                stringResource(R.string.audio_settings_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // --- Speed & Pitch ---
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.label_tempo_pitch), style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = {
                                    EnhancedMusicPlayerManager.setPlaybackSpeed(1f)
                                    EnhancedMusicPlayerManager.setPlaybackPitch(0f)
                                }) {
                                    Icon(Icons.Default.Refresh, stringResource(R.string.action_reset), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_reset))
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Speed
                            Text(stringResource(R.string.template_speed, (speed * 100).roundToInt()), style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = speed,
                                onValueChange = { EnhancedMusicPlayerManager.setPlaybackSpeed(it) },
                                valueRange = 0.25f..2.0f,
                                steps = 34
                            )

                            // Pitch
                            Text(stringResource(R.string.template_pitch, pitch.roundToInt()), style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = pitch,
                                onValueChange = { EnhancedMusicPlayerManager.setPlaybackPitch(it) },
                                valueRange = -12f..12f,
                                steps = 23
                            )
                        }
                    }
                }

                // --- Equalizer (shared with the video player) ---
                item {
                    EqualizerEditor()
                }
            }
        }
    }
}
