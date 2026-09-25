package io.github.aedev.flow.ui.screens.equalizer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.audio.eq.BuiltInEqPresets
import io.github.aedev.flow.data.audio.eq.EqMode
import io.github.aedev.flow.data.audio.eq.EqPreset
import io.github.aedev.flow.data.audio.eq.EqState
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.components.shared.FlowRowGroup
import io.github.aedev.flow.ui.components.shared.FlowSectionHeader
import io.github.aedev.flow.ui.components.shared.FlowSegmentedGap
import io.github.aedev.flow.ui.components.shared.FlowSelectionRow
import io.github.aedev.flow.ui.components.shared.flowRowGroupShape

private val ItemInset = 12.dp

/** Import first, then the user's presets with their actions, then the built-ins with Flat on top. */
@Composable
internal fun EqualizerPresetsPage(
    state: EqState,
    viewModel: EqualizerViewModel,
    onSelected: () -> Unit,
    onImportFile: () -> Unit,
    onPaste: () -> Unit,
    onShare: (String?) -> Unit,
    onDialog: (EqDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
    val selectedId = state.active.presetId
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(FlowSegmentedGap),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottom),
    ) {
        item("import") {
            EqFramed {
                FlowRowGroup {
                    FlowNavRow(
                        title = stringResource(R.string.eq_import_file),
                        supportingText = stringResource(R.string.eq_import_file_desc),
                        onClick = onImportFile,
                        leadingIcon = Icons.Outlined.UploadFile,
                        showChevron = false,
                        shape = flowRowGroupShape(0, 2),
                    )
                    FlowNavRow(
                        title = stringResource(R.string.eq_paste_text),
                        onClick = onPaste,
                        leadingIcon = Icons.Outlined.ContentPaste,
                        showChevron = false,
                        shape = flowRowGroupShape(1, 2),
                    )
                }
            }
        }
        if (state.userPresets.isNotEmpty()) {
            item("yours-header") { EqFramed { FlowSectionHeader(stringResource(R.string.eq_your_presets)) } }
            itemsIndexed(state.userPresets, key = { _, preset -> preset.id }) { index, preset ->
                EqFramed(Modifier.padding(horizontal = ItemInset)) {
                    UserPresetRow(
                        preset = preset,
                        selected = preset.id == selectedId,
                        shape = flowRowGroupShape(index, state.userPresets.size),
                        onClick = {
                            viewModel.selectPreset(preset.id)
                            onSelected()
                        },
                        onRename = { onDialog(EqDialog.Rename(preset.id, preset.name)) },
                        onDuplicate = { onDialog(EqDialog.Duplicate(preset.id, preset.name)) },
                        onExport = { onShare(preset.id) },
                        onDelete = { viewModel.deletePreset(preset.id) },
                    )
                }
            }
        }
        item("built-in-header") { EqFramed { FlowSectionHeader(stringResource(R.string.eq_built_in)) } }
        itemsIndexed(BuiltInEqPresets.all, key = { _, preset -> preset.id }) { index, preset ->
            EqFramed(Modifier.padding(horizontal = ItemInset)) {
                FlowSelectionRow(
                    title = stringResource(preset.nameRes),
                    selected = preset.id == selectedId,
                    onClick = {
                        viewModel.selectPreset(preset.id)
                        onSelected()
                    },
                    shape = flowRowGroupShape(index, BuiltInEqPresets.all.size),
                )
            }
        }
    }
}

@Composable
private fun UserPresetRow(
    preset: EqPreset,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val summary =
        when {
            preset.imported -> stringResource(R.string.eq_imported_badge)
            preset.mode == EqMode.GRAPHIC -> stringResource(R.string.eq_mode_graphic)
            else -> pluralStringResource(R.plurals.eq_band_total, preset.curve.bands.size, preset.curve.bands.size)
        }
    FlowNavRow(
        title = preset.name,
        supportingText = summary,
        onClick = onClick,
        selected = selected,
        shape = shape,
        trailingContent = {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    MenuItem(R.string.eq_rename, Icons.Outlined.DriveFileRenameOutline) {
                        open = false
                        onRename()
                    }
                    MenuItem(R.string.eq_duplicate, Icons.Outlined.ContentCopy) {
                        open = false
                        onDuplicate()
                    }
                    MenuItem(R.string.eq_export, Icons.Outlined.IosShare) {
                        open = false
                        onExport()
                    }
                    MenuItem(R.string.delete, Icons.Outlined.Delete) {
                        open = false
                        onDelete()
                    }
                }
            }
        },
    )
}

@Composable
private fun MenuItem(
    @StringRes text: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(text)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
