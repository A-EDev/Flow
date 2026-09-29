package io.github.aedev.flow.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.rememberFlowSheetState

private val OptionHorizontalPadding = 24.dp
private val OptionVerticalPadding = 12.dp
private val OptionSpacing = 8.dp
private val SheetBottomPadding = 16.dp

/**
 * A single choice where every option is shown, not just named: each row carries a live preview of
 * what picking it looks like, such as a seek bar style or a player background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsPreviewSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    preview: @Composable (T) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        FlowSheetHeader(title = title, subtitle = subtitle, onClose = onDismiss, showDragHandle = false)
        LazyColumn(
            contentPadding =
                PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + SheetBottomPadding,
                ),
        ) {
            items(options) { option ->
                val isSelected = option == selected
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                            .padding(horizontal = OptionHorizontalPadding, vertical = OptionVerticalPadding),
                    verticalArrangement = Arrangement.spacedBy(OptionSpacing),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    preview(option)
                }
            }
        }
    }
}
