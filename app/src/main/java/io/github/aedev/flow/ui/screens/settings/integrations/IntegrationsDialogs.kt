package io.github.aedev.flow.ui.screens.settings.integrations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog
import io.github.aedev.flow.ui.theme.SponsorBlockSegmentPresets
import io.github.aedev.flow.utils.sponsorCategoryLabelRes

private val PresetSize = 32.dp
private val PresetBorder = 1.dp
private val PresetSpacing = 4.dp
private const val LIGHT_SWATCH_LUMINANCE = 0.5f

internal enum class IntegrationsDialog { USER_ID, DISCORD_RISK }

@Composable
internal fun IntegrationsDialogs(
    dialog: IntegrationsDialog?,
    userId: String?,
    viewModel: IntegrationsViewModel,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        IntegrationsDialog.USER_ID -> {
            var input by rememberSaveable { mutableStateOf(userId.orEmpty()) }
            FlowAlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.sb_user_id_dialog_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(PresetSpacing)) {
                        Text(stringResource(R.string.sb_user_id_dialog_body), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            label = { Text(stringResource(R.string.sb_user_id_hint)) },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setUserId(input)
                        onDismiss()
                    }) { Text(stringResource(R.string.btn_save)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } },
            )
        }

        IntegrationsDialog.DISCORD_RISK -> {
            FlowAlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.discord_presence_risk_dialog_title)) },
                text = { Text(stringResource(R.string.discord_presence_risk_dialog_body)) },
                confirmButton = {
                    Button(onClick = {
                        viewModel.connectDiscord()
                        onDismiss()
                    }) { Text(stringResource(R.string.discord_presence_risk_accept)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }

        null -> {
            Unit
        }
    }
}

/** Picks a segment colour from the presets, or goes back to the category's default. */
@Composable
internal fun SegmentColourDialog(
    category: String,
    current: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val label = sponsorCategoryLabelRes(category)?.let { stringResource(it) } ?: category
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sb_color_picker_title)) },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PresetSpacing),
                verticalArrangement = Arrangement.spacedBy(PresetSpacing),
            ) {
                SponsorBlockSegmentPresets.forEach { preset ->
                    val selected = preset.toArgb() == current
                    IconButton(
                        onClick = {
                            onSelect(preset.toArgb())
                            onDismiss()
                        },
                        modifier =
                            Modifier.semantics {
                                role = Role.RadioButton
                                this.selected = selected
                            },
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(PresetSize)
                                    .clip(CircleShape)
                                    .background(preset)
                                    .border(PresetBorder, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.settings_segment_colour, label),
                                    tint = if (preset.luminance() > LIGHT_SWATCH_LUMINANCE) Color.Black else Color.White,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelect(null)
                onDismiss()
            }) { Text(stringResource(R.string.sb_color_reset)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
