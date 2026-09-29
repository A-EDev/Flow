package io.github.aedev.flow.ui.screens.settings.appearance.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog
import io.github.aedev.flow.ui.components.shared.FlowChoice
import io.github.aedev.flow.ui.components.shared.FlowChoiceDialog
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.theme.CustomTheme
import io.github.aedev.flow.ui.theme.ThemeCatalog
import io.github.aedev.flow.ui.theme.ThemeCatalogEntry
import io.github.aedev.flow.ui.theme.ThemeMode

private val FieldSpacing = 8.dp

/**
 * Names a theme: a new one, with the palette it starts from, or an existing one being renamed or
 * copied. [palettes] is null when there is no palette to pick.
 */
@Composable
internal fun ThemeNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (name: String, base: ThemeMode) -> Unit,
    onDismiss: () -> Unit,
    palettes: List<ThemeCatalogEntry>? = null,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var base by rememberSaveable { mutableStateOf(ThemeMode.DARK) }
    var pickingBase by rememberSaveable { mutableStateOf(false) }
    val trimmed = name.trim()

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(CustomTheme.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.settings_custom_theme_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (palettes != null) {
                    FlowNavRow(
                        title = stringResource(R.string.settings_custom_theme_start_from),
                        supportingText = stringResource(ThemeCatalog.nameRes(base)),
                        leadingIcon = Icons.Outlined.Palette,
                        showChevron = false,
                        onClick = { pickingBase = true },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = trimmed.isNotEmpty(), onClick = { onConfirm(trimmed, base) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (pickingBase && palettes != null) {
        FlowChoiceDialog(
            title = stringResource(R.string.settings_custom_theme_start_from),
            options = palettes.map { FlowChoice(it.mode, stringResource(it.nameRes), stringResource(it.descriptionRes)) },
            selected = base,
            onSelect = { base = it },
            onDismiss = { pickingBase = false },
        )
    }
}

@Composable
internal fun DeleteThemeDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_custom_theme_delete_title, name)) },
        text = { Text(stringResource(R.string.settings_custom_theme_delete_body)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(stringResource(R.string.settings_custom_theme_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
