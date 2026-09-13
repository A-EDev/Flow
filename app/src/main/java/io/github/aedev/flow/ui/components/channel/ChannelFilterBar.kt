package io.github.aedev.flow.ui.components.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelFilterBar(
    sortOptions: List<String>,
    selectedSort: Int,
    isGridView: Boolean,
    searchActive: Boolean = false,
    searchQuery: String = "",
    showListControls: Boolean = true,
    onSortSelected: (Int) -> Unit,
    onToggleGridView: () -> Unit,
    onSearchToggle: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchActive && showListControls) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                placeholder = { Text(stringResource(R.string.channel_search_hint), style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onSearch = { onSearchQueryChange(searchQuery) },
                    ),
                shape = RoundedCornerShape(20.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
            )
            IconButton(onClick = onSearchToggle) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.channel_search_close),
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                ChannelSortChipRow(
                    options = sortOptions,
                    selectedIndex = selectedSort,
                    onSelected = onSortSelected,
                )
            }
            if (!showListControls) return@Row
            IconButton(onClick = onSearchToggle) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.channel_search_open),
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onToggleGridView) {
                Icon(
                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = if (isGridView) stringResource(R.string.ui_list_view) else stringResource(R.string.ui_grid_view),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// Channel header — banner + avatar + info + subscribe
