package io.github.aedev.flow.ui.screens.equalizer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.audio.eq.EqFilterMath
import io.github.aedev.flow.data.audio.eq.EqLimits
import io.github.aedev.flow.ui.components.equalizer.EqResponseGraph
import io.github.aedev.flow.ui.components.equalizer.formatGain
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog

private val PreviewHeight = 96.dp
private val PasteFieldMinHeight = 160.dp
private const val PASTE_MAX_LINES = 10

internal sealed interface EqDialog {
    data object SaveAs : EqDialog

    data object Paste : EqDialog

    data class Rename(
        val id: String,
        val current: String,
    ) : EqDialog

    data class Duplicate(
        val id: String,
        val sourceName: String,
    ) : EqDialog
}

@Composable
internal fun EqualizerDialogs(
    dialog: EqDialog?,
    importPreview: EqImportPreview?,
    viewModel: EqualizerViewModel,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        EqDialog.SaveAs -> {
            NameDialog(
                title = R.string.eq_save_as,
                initial = "",
                isAvailable = { viewModel.isNameAvailable(it) },
                onConfirm = {
                    viewModel.saveAs(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        is EqDialog.Rename -> {
            NameDialog(
                title = R.string.eq_rename,
                initial = dialog.current,
                isAvailable = { viewModel.isNameAvailable(it, exceptId = dialog.id) },
                onConfirm = {
                    viewModel.rename(dialog.id, it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        is EqDialog.Duplicate -> {
            NameDialog(
                title = R.string.eq_duplicate,
                initial = stringResource(R.string.eq_copy_name, dialog.sourceName),
                isAvailable = { viewModel.isNameAvailable(it) },
                onConfirm = {
                    viewModel.duplicate(dialog.id, it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        EqDialog.Paste -> {
            PasteDialog(
                onImport = {
                    viewModel.importFromText(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        null -> {
            Unit
        }
    }
    importPreview?.let { ImportPreviewDialog(preview = it, viewModel = viewModel) }
}

@Composable
private fun NameDialog(
    @StringRes title: Int,
    initial: String,
    isAvailable: (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val blank = name.isBlank()
    val taken = !blank && !isAvailable(name)
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { NameField(name = name, onNameChange = { name = it }, taken = taken) },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = !blank && !taken) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NameField(
    name: String,
    onNameChange: (String) -> Unit,
    taken: Boolean,
) {
    OutlinedTextField(
        value = name,
        onValueChange = { onNameChange(it.take(EqLimits.MAX_NAME_LENGTH)) },
        label = { Text(stringResource(R.string.eq_name)) },
        singleLine = true,
        isError = taken,
        supportingText = if (taken) ({ Text(stringResource(R.string.eq_name_taken)) }) else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasteDialog(
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.eq_paste_text)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.eq_paste_hint)) },
                maxLines = PASTE_MAX_LINES,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().heightIn(min = PasteFieldMinHeight),
            )
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.eq_import_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** What a file or paste holds, and what auto preamp will do with it, before anything is saved. */
@Composable
private fun ImportPreviewDialog(
    preview: EqImportPreview,
    viewModel: EqualizerViewModel,
) {
    var name by rememberSaveable(preview) { mutableStateOf(preview.suggestedName) }
    val result = preview.result
    val bands = result.curve.bands
    val peak = remember(bands) { EqFilterMath.peakDb(bands) }
    val taken = name.isNotBlank() && !viewModel.isNameAvailable(name)
    FlowAlertDialog(
        onDismissRequest = viewModel::dismissImport,
        title = { Text(stringResource(R.string.eq_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EqResponseGraph(
                    bands = bands,
                    showPoints = false,
                    showLabels = false,
                    modifier = Modifier.fillMaxWidth().height(PreviewHeight),
                )
                Text(stringResource(R.string.eq_import_summary, bands.size, formatGain(result.curve.preamp)))
                val notes =
                    buildList {
                        if (result.skippedFilters > 0) add(stringResource(R.string.eq_import_skipped, result.skippedFilters))
                        if (result.truncatedFilters > 0) add(stringResource(R.string.eq_import_truncated))
                        if (peak > 0.0) add(stringResource(R.string.eq_import_peak, formatGain(peak)))
                    }
                if (notes.isNotEmpty()) {
                    Text(
                        text = notes.joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NameField(name = name, onNameChange = { name = it }, taken = taken)
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.confirmImport(name) }, enabled = name.isNotBlank() && !taken) {
                Text(stringResource(R.string.eq_import_action))
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissImport) { Text(stringResource(R.string.cancel)) } },
    )
}
