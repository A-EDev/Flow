package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/**
 * A list's sort order as a chip that opens a menu of [options]. The chip reads as selected
 * once the order is not [default], so a changed order is visible at a glance.
 */
@Composable
fun <T> FlowSortChip(
    options: List<T>,
    selected: T,
    default: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FlowDropdownFilterChip(label = label(selected), selected = selected != default, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    trailingIcon = if (option == selected) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                    modifier = Modifier.semantics { this.selected = option == selected },
                    onClick = {
                        open = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}
