package io.github.aedev.flow.ui.screens.settings.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R

private val EditorPadding = 16.dp
private val EditorSpacing = 12.dp
private val ChipSpacing = 8.dp

/** The custom speed presets: the saved ones as removable chips, and a field to add another. */
@Composable
internal fun SpeedPresetEditor(
    viewModel: PlaybackSettingsViewModel,
    shape: Shape,
) {
    val presets by viewModel.customSpeedPresets.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val removeLabel = stringResource(R.string.player_settings_custom_speeds_remove)

    fun add() {
        val speed = parseSpeedInput(input)
        if (speed == null) {
            invalid = true
        } else {
            viewModel.addSpeedPreset(speed)
            input = ""
            invalid = false
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(EditorPadding),
        verticalArrangement = Arrangement.spacedBy(EditorSpacing),
    ) {
        Text(text = stringResource(R.string.player_settings_custom_speeds_header), style = MaterialTheme.typography.titleSmall)
        if (presets.isEmpty()) {
            Text(
                text = stringResource(R.string.player_settings_custom_speeds_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
                presets.forEach { speed ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.removeSpeedPreset(speed) },
                        label = { Text(stringResource(R.string.playback_speed_multiplier, speed.toString())) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = removeLabel,
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EditorSpacing)) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    invalid = false
                },
                placeholder = { Text(stringResource(R.string.settings_speed_preset_hint)) },
                isError = invalid,
                supportingText = if (invalid) ({ Text(stringResource(R.string.player_settings_custom_speeds_input_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(onClick = ::add, enabled = input.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_speed_preset_add))
            }
        }
    }
}
