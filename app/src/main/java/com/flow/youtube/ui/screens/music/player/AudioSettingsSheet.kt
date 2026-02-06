package com.flow.youtube.ui.screens.music.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.youtube.data.model.EqPresets
import com.flow.youtube.data.model.FilterType
import com.flow.youtube.data.model.ParametricEQ
import com.flow.youtube.data.model.ParametricEQBand
import com.flow.youtube.player.EnhancedMusicPlayerManager
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsSheet(
    onDismiss: () -> Unit
) {
    val speed by EnhancedMusicPlayerManager.playbackSpeed.collectAsState()
    val pitch by EnhancedMusicPlayerManager.playbackPitch.collectAsState()
    val selectedPreset by EnhancedMusicPlayerManager.currentEqProfile.collectAsState()
    val bassBoost by EnhancedMusicPlayerManager.bassBoostLevel.collectAsState()

    var preamp by remember { mutableFloatStateOf(0.0f) }
    var bands by remember { mutableStateOf(getAllInitialBands()) }
    
    LaunchedEffect(selectedPreset) {
        val preset = EqPresets.presets[selectedPreset] ?: ParametricEQ.createFlat()
        bands = preset.bands
        preamp = preset.preamp.toFloat()
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                "Audio Settings",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                 item {
                     Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.4f))) {
                         Column(Modifier.padding(16.dp)) {
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                  Text("Preset", style = MaterialTheme.typography.titleMedium)
                                  Spacer(Modifier.weight(1f))
                                  
                                  var expanded by remember { mutableStateOf(false) }
                                  Box {
                                      TextButton(onClick = { expanded = true }) {
                                          Text(selectedPreset)
                                          Icon(Icons.Default.ExpandMore, null)
                                      }
                                      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                          EqPresets.presets.keys.sorted().forEach { presetName ->
                                              DropdownMenuItem(
                                                  text = { Text(presetName) },
                                                  onClick = {
                                                      EnhancedMusicPlayerManager.setEqProfile(presetName)
                                                      expanded = false
                                                  }
                                              )
                                          }
                                      }
                                  }
                             }
                             
                             Spacer(Modifier.height(8.dp))
                             Text("Bass Boost: ${bassBoost.roundToInt()}dB", style = MaterialTheme.typography.labelMedium)
                             Slider(
                                 value = bassBoost,
                                 onValueChange = { EnhancedMusicPlayerManager.setBassBoost(it) },
                                 valueRange = 0f..15f,
                                 steps = 15
                             )
                         }
                     }
                 }

                // --- Speed & Pitch ---
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Tempo & Pitch", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { 
                                    EnhancedMusicPlayerManager.setPlaybackSpeed(1f)
                                    EnhancedMusicPlayerManager.setPlaybackPitch(0f)
                                }) {
                                    Icon(Icons.Default.Refresh, "Reset", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reset")
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            
                            // Speed
                            Text("Speed: ${(speed * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = speed,
                                onValueChange = { EnhancedMusicPlayerManager.setPlaybackSpeed(it) },
                                valueRange = 0.25f..2.0f,
                                steps = 34 
                            )

                            // Pitch
                            Text("Pitch: ${pitch.roundToInt()} Semitones", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = pitch,
                                onValueChange = { EnhancedMusicPlayerManager.setPlaybackPitch(it) },
                                valueRange = -12f..12f,
                                steps = 23
                            )
                        }
                    }
                }

                // --- Equalizer ---
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Parametric Equalizer", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { 
                            bands = bands + ParametricEQBand(1000.0, 0.0, 1.41, FilterType.PK, true) 
                        }) {
                            Icon(Icons.Default.Add, "Add Band")
                        }
                    }
                }
                
                item {
                     Column {
                        Text("Preamp: ${preamp}dB", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = preamp,
                            onValueChange = { preamp = it },
                            valueRange = -20f..20f
                        )
                     }
                }

                itemsIndexed(bands) { index, band ->
                    BandControl(
                        band = band,
                        onUpdate = { newBand ->
                            val list = bands.toMutableList()
                            list[index] = newBand
                            bands = list
                        },
                        onRemove = {
                            val list = bands.toMutableList()
                            list.removeAt(index)
                            bands = list
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BandControl(
    band: ParametricEQBand,
    onUpdate: (ParametricEQBand) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterTypeButton(band.filterType) { newType -> onUpdate(band.copy(filterType = newType)) }
                
                Spacer(Modifier.weight(1f))
                
                Switch(checked = band.enabled, onCheckedChange = { onUpdate(band.copy(enabled = it)) }, modifier = Modifier.scale(0.8f))
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp))
                }
            }
            
            // Sliders
            if (band.enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                   Text("F", fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp), fontSize = 12.sp)
                   Slider(
                       value = freqToLog(band.frequency),
                       onValueChange = { onUpdate(band.copy(frequency = logToFreq(it))) },
                       valueRange = 0f..1f, 
                       modifier = Modifier.weight(1f)
                   )
                   Text("${band.frequency.roundToInt()}Hz", modifier = Modifier.width(50.dp), fontSize = 12.sp)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                   Text("G", fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp), fontSize = 12.sp)
                   Slider(
                       value = band.gain.toFloat(),
                       onValueChange = { onUpdate(band.copy(gain = it.toDouble())) },
                       valueRange = -15f..15f,
                       modifier = Modifier.weight(1f)
                   )
                   Text("${"%.1f".format(band.gain)}dB", modifier = Modifier.width(50.dp), fontSize = 12.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                   Text("Q", fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp), fontSize = 12.sp)
                   Slider(
                       value = band.q.toFloat(),
                       onValueChange = { onUpdate(band.copy(q = it.toDouble())) },
                       valueRange = 0.1f..10f,
                       modifier = Modifier.weight(1f)
                   )
                   Text("${"%.1f".format(band.q)}", modifier = Modifier.width(50.dp), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun FilterTypeButton(current: FilterType, onChange: (FilterType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp)) {
            Text(current.name)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FilterType.values().forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name) },
                    onClick = { onChange(type); expanded = false }
                )
            }
        }
    }
}

fun freqToLog(freq: Double): Float {
    val minLog = kotlin.math.log10(20.0)
    val maxLog = kotlin.math.log10(20000.0)
    val freqLog = kotlin.math.log10(freq.coerceIn(20.0, 20000.0))
    return ((freqLog - minLog) / (maxLog - minLog)).toFloat()
}

fun logToFreq(norm: Float): Double {
    val minLog = kotlin.math.log10(20.0)
    val maxLog = kotlin.math.log10(20000.0)
    val logFreq = minLog + (norm * (maxLog - minLog))
    return 10.0.pow(logFreq.toDouble())
}

// Initial 5-band EQ
fun getAllInitialBands(): List<ParametricEQBand> {
    return listOf(
        ParametricEQBand(60.0, 0.0, 1.41, FilterType.LSC),
        ParametricEQBand(230.0, 0.0, 1.41, FilterType.PK),
        ParametricEQBand(910.0, 0.0, 1.41, FilterType.PK),
        ParametricEQBand(3600.0, 0.0, 1.41, FilterType.PK),
        ParametricEQBand(14000.0, 0.0, 1.41, FilterType.HSC)
    )
}
