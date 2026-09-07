package io.github.aedev.flow.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.SubscriptionGroup
import io.github.aedev.flow.ui.components.ChannelAvatarImage
import io.github.aedev.flow.ui.components.shared.ReorderHandle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val GroupListMaxHeight = 280.dp
private val ChannelListMaxHeight = 280.dp
private val RowVerticalPadding = 4.dp
private val ActionIconSize = 32.dp
private val ActionGlyphSize = 16.dp
private val ChannelAvatarSize = 32.dp
private val ContentSpacing = 8.dp

@Composable
internal fun SubscriptionGroupsManagerDialog(
    groups: List<SubscriptionGroup>,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onEdit: (SubscriptionGroup) -> Unit,
    onDelete: (SubscriptionGroup) -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_groups)) },
        text = {
            if (groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_groups_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = ContentSpacing),
                )
            } else {
                val listState = rememberLazyListState()
                val reorderableState =
                    rememberReorderableLazyListState(listState) { from, to ->
                        onReorder(from.index, to.index)
                    }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = GroupListMaxHeight),
                ) {
                    items(groups, key = { it.name }) { group ->
                        ReorderableItem(state = reorderableState, key = group.name) { isDragging ->
                            Surface(
                                color =
                                    if (isDragging) {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    } else {
                                        Color.Transparent
                                    },
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = RowVerticalPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ReorderHandle(modifier = Modifier.draggableHandle())
                                    Spacer(modifier = Modifier.width(ContentSpacing))
                                    Text(
                                        text = group.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text =
                                            pluralStringResource(
                                                R.plurals.channels_count,
                                                group.channelIds.size,
                                                group.channelIds.size,
                                            ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = ContentSpacing),
                                    )
                                    IconButton(onClick = { onEdit(group) }, modifier = Modifier.size(ActionIconSize)) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(ActionGlyphSize),
                                        )
                                    }
                                    IconButton(onClick = { onDelete(group) }, modifier = Modifier.size(ActionIconSize)) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(ActionGlyphSize),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNew) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ActionGlyphSize))
                Spacer(modifier = Modifier.width(RowVerticalPadding))
                Text(stringResource(R.string.new_group))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
internal fun SubscriptionCreateEditGroupDialog(
    existingGroup: SubscriptionGroup?,
    allChannels: List<Channel>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, channelIds: List<String>) -> Unit,
) {
    var groupName by remember { mutableStateOf(existingGroup?.name.orEmpty()) }
    var selectedChannelIds by remember { mutableStateOf(existingGroup?.channelIds.orEmpty().toSet()) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredChannels =
        remember(allChannels, searchQuery) {
            if (searchQuery.isBlank()) {
                allChannels
            } else {
                allChannels.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (existingGroup == null) R.string.new_group else R.string.edit_group),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ContentSpacing)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text(stringResource(R.string.group_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_channels_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = ChannelListMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filteredChannels, key = { it.id }) { channel ->
                        val isChecked = channel.id in selectedChannelIds
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedChannelIds =
                                            if (isChecked) {
                                                selectedChannelIds - channel.id
                                            } else {
                                                selectedChannelIds + channel.id
                                            }
                                    }.padding(vertical = RowVerticalPadding, horizontal = RowVerticalPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isChecked, onCheckedChange = null)
                            ChannelAvatarImage(
                                url = channel.thumbnailUrl,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(ChannelAvatarSize)
                                        .clip(subscriptionAvatarShape(channel.isMusic))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Spacer(modifier = Modifier.width(ContentSpacing))
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(groupName.trim(), selectedChannelIds.toList()) },
                enabled = groupName.isNotBlank() && selectedChannelIds.isNotEmpty(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
