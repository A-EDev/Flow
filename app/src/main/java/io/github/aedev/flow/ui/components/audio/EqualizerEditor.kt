package io.github.aedev.flow.ui.components.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.FilterType
import io.github.aedev.flow.data.model.ParametricEQ
import io.github.aedev.flow.data.model.ParametricEQBand
import io.github.aedev.flow.player.audio.AudioEffectsController
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Parametric equalizer editor shared by the music audio-settings sheet and the video player
 * settings menu. All edits go through [AudioEffectsController], so they apply to both playback
 * pipelines immediately and persist across tracks and app restarts.
 */
@Composable
fun EqualizerEditor(modifier: Modifier = Modifier) {
    val selectedPreset by AudioEffectsController.eqProfileName.collectAsState()
    val bassBoost by AudioEffectsController.bassBoost.collectAsState()
    val customPresets by AudioEffectsController.customPresets.collectAsState()

    var preamp by remember { mutableFloatStateOf(0f) }
    var bands by remember { mutableStateOf(defaultEditingBands()) }
    var showSaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPreset) {
        val profile = AudioEffectsController.editableProfile(selectedPreset)
        bands = profile.bands.ifEmpty { defaultEditingBands() }
        preamp = profile.preamp.toFloat()
    }

    // Any manual edit becomes the live "Custom" curve — applied to the players and persisted.
    fun pushCustom(newBands: List<ParametricEQBand> = bands, newPreamp: Float = preamp) {
        bands = newBands
        preamp = newPreamp
        AudioEffectsController.setCustomEq(ParametricEQ(newPreamp.toDouble(), newBands))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_preset), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))

                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(selectedPreset)
                            Icon(Icons.Default.ExpandMore, null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            AudioEffectsController.availablePresetNames().forEach { presetName ->
                                DropdownMenuItem(
                                    text = { Text(presetName) },
                                    trailingIcon = if (AudioEffectsController.isCustomPreset(presetName)) {
                                        {
                                            IconButton(
                                                onClick = { AudioEffectsController.deleteCustomPreset(presetName) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, stringResource(R.string.delete), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    } else null,
                                    onClick = {
                                        AudioEffectsController.setEqProfile(presetName)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.template_bass_boost, bassBoost.roundToInt()), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = bassBoost,
                    onValueChange = { AudioEffectsController.setBassBoost(it) },
                    valueRange = 0f..15f,
                    steps = 15
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.label_parametric_equalizer), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showSaveDialog = true }) {
                Icon(Icons.Default.Save, stringResource(R.string.action_save_preset))
            }
            IconButton(onClick = {
                pushCustom(newBands = bands + ParametricEQBand(1000.0, 0.0, 1.41, FilterType.PK, true))
            }) {
                Icon(Icons.Default.Add, stringResource(R.string.action_add_band))
            }
        }

        Column {
            Text(stringResource(R.string.template_preamp, preamp), style = MaterialTheme.typography.labelSmall)
            Slider(
                value = preamp,
                onValueChange = { pushCustom(newPreamp = it) },
                valueRange = -20f..20f
            )
        }

        bands.forEachIndexed { index, band ->
            BandControl(
                band = band,
                onUpdate = { newBand ->
                    pushCustom(newBands = bands.toMutableList().also { it[index] = newBand })
                },
                onRemove = {
                    pushCustom(newBands = bands.toMutableList().also { it.removeAt(index) })
                }
            )
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(
            onSave = { name ->
                if (AudioEffectsController.saveCustomPreset(name)) showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

@Composable
private fun SavePresetDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_save_preset)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.label_preset_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.trim().isNotEmpty()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun BandControl(
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
                    Icon(Icons.Default.Close, stringResource(R.string.remove), modifier = Modifier.size(16.dp))
                }
            }

            if (band.enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("F", fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp), fontSize = 12.sp)
                    Slider(
                        value = freqToLog(band.frequency),
                        onValueChange = { onUpdate(band.copy(frequency = logToFreq(it))) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(stringResource(R.string.template_freq, band.frequency.roundToInt()), modifier = Modifier.width(50.dp), fontSize = 12.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("G", fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp), fontSize = 12.sp)
                    Slider(
                        value = band.gain.toFloat(),
                        onValueChange = { onUpdate(band.copy(gain = it.toDouble())) },
                        valueRange = -15f..15f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(stringResource(R.string.template_gain, band.gain), modifier = Modifier.width(50.dp), fontSize = 12.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Q", fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp), fontSize = 12.sp)
                    Slider(
                        value = band.q.toFloat(),
                        onValueChange = { onUpdate(band.copy(q = it.toDouble())) },
                        valueRange = 0.1f..10f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(stringResource(R.string.template_q, band.q), modifier = Modifier.width(50.dp), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun FilterTypeButton(current: FilterType, onChange: (FilterType) -> Unit) {
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

private fun freqToLog(freq: Double): Float {
    val minLog = kotlin.math.log10(20.0)
    val maxLog = kotlin.math.log10(20000.0)
    val freqLog = kotlin.math.log10(freq.coerceIn(20.0, 20000.0))
    return ((freqLog - minLog) / (maxLog - minLog)).toFloat()
}

private fun logToFreq(norm: Float): Double {
    val minLog = kotlin.math.log10(20.0)
    val maxLog = kotlin.math.log10(20000.0)
    val logFreq = minLog + (norm * (maxLog - minLog))
    return 10.0.pow(logFreq.toDouble())
}

// Editing scaffold shown when the active profile has no bands (e.g. Flat).
private fun defaultEditingBands(): List<ParametricEQBand> = listOf(
    ParametricEQBand(60.0, 0.0, 1.41, FilterType.LSC),
    ParametricEQBand(230.0, 0.0, 1.41, FilterType.PK),
    ParametricEQBand(910.0, 0.0, 1.41, FilterType.PK),
    ParametricEQBand(3600.0, 0.0, 1.41, FilterType.PK),
    ParametricEQBand(14000.0, 0.0, 1.41, FilterType.HSC)
)
