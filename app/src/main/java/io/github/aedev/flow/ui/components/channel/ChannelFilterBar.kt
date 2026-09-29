package io.github.aedev.flow.ui.components.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.pages.channel.ChannelFilterGroup
import io.github.aedev.flow.ui.components.shared.FlowDropdownFilterChip
import io.github.aedev.flow.ui.components.shared.FlowFilterChip
import io.github.aedev.flow.ui.components.shared.FlowSearchField

/**
 * A tab's filter bar. YouTube ships two controls here and they are not interchangeable: a sort menu
 * opens a dropdown, and the remaining filters are chips. Rendering the menu as chips would show
 * "Latest Popular Oldest" alongside "Members only" as if all five were the same kind of switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelFilterBar(
    filterGroups: List<ChannelFilterGroup>,
    selected: List<Int>,
    searchActive: Boolean = false,
    searchQuery: String = "",
    showListControls: Boolean = true,
    onFilterSelected: (groupIndex: Int, optionIndex: Int) -> Unit,
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
            FlowSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = stringResource(R.string.channel_search_hint),
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
            )
            IconButton(onClick = onSearchToggle) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.channel_search_close),
                    modifier = Modifier.size(22.dp),
                )
            }
            return@Row
        }

        // One lazy item per control: a group emitted as a single item would draw its chips with no
        // gap, because the row's spacing only separates items.
        val controls =
            remember(filterGroups) {
                filterGroups.flatMapIndexed { groupIndex, group ->
                    if (group.isDropdown) {
                        listOf(groupIndex to -1)
                    } else {
                        group.options.indices.map { optionIndex -> groupIndex to optionIndex }
                    }
                }
            }

        Box(modifier = Modifier.weight(1f)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = controls,
                    key = { (groupIndex, optionIndex) -> "f_${groupIndex}_$optionIndex" },
                ) { (groupIndex, optionIndex) ->
                    val group = filterGroups[groupIndex]
                    val chosen = selected.getOrElse(groupIndex) { -1 }
                    if (optionIndex < 0) {
                        ChannelFilterDropdown(
                            group = group,
                            selectedIndex = chosen,
                            onSelected = { option -> onFilterSelected(groupIndex, option) },
                        )
                    } else {
                        val option = group.options[optionIndex]
                        FlowFilterChip(
                            label = option.label,
                            selected = optionIndex == chosen,
                            onClick = { onFilterSelected(groupIndex, optionIndex) },
                        )
                    }
                }
            }
        }

        if (!showListControls) return@Row
        IconButton(onClick = onSearchToggle) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.channel_search_open),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelFilterDropdown(
    group: ChannelFilterGroup,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val activeIndex = if (selectedIndex >= 0) selectedIndex else group.selectedIndex.coerceAtLeast(0)
    val label =
        group.title ?: group.options
            .getOrNull(activeIndex)
            ?.label
            .orEmpty()

    Box {
        FlowDropdownFilterChip(
            label = label,
            selected = selectedIndex >= 0,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            group.options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    },
                    leadingIcon =
                        if (index == activeIndex) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
}
