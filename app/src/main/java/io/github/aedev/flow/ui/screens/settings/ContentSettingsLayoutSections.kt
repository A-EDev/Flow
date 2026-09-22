package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.components.shared.FlowFilterChip
import io.github.aedev.flow.ui.theme.GridItemSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ContentDisplaySection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val gridSizeString by preferences.gridItemSize.collectAsState(initial = "BIG")
    val currentGridSize =
        try {
            GridItemSize.valueOf(gridSizeString)
        } catch (e: Exception) {
            GridItemSize.BIG
        }
    val showChannelGroupBadges by preferences.showChannelGroupBadges.collectAsState(initial = false)

    SectionHeader(text = stringResource(R.string.content_settings_header_display))
    SettingsGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.GridView,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.content_settings_grid_size_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.content_settings_grid_size_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GridSizeOption(
                    title = stringResource(R.string.content_settings_grid_big_title),
                    description = stringResource(R.string.content_settings_grid_big_desc),
                    isSelected = currentGridSize == GridItemSize.BIG,
                    onClick = {
                        coroutineScope.launch {
                            preferences.setGridItemSize("BIG")
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                GridSizeOption(
                    title = stringResource(R.string.content_settings_grid_small_title),
                    description = stringResource(R.string.content_settings_grid_small_desc),
                    isSelected = currentGridSize == GridItemSize.SMALL,
                    onClick = {
                        coroutineScope.launch {
                            preferences.setGridItemSize("SMALL")
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    SettingsGroup {
        SettingsSwitchItem(
            icon = Icons.Outlined.Label,
            title = stringResource(R.string.content_settings_channel_group_badge_title),
            subtitle = stringResource(R.string.content_settings_channel_group_badge_subtitle),
            checked = showChannelGroupBadges,
            onCheckedChange = { enabled ->
                coroutineScope.launch { preferences.setShowChannelGroupBadges(enabled) }
            },
        )
    }
}

@Composable
internal fun ContentDownloadMenuSection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val downloadDialogStyle by preferences.downloadDialogStyle.collectAsState(
        initial = io.github.aedev.flow.data.local.DownloadDialogStyle.FULL,
    )

    SectionHeader(text = stringResource(R.string.download_menu_style_title))
    SettingsGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ViewAgenda,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.download_menu_style_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GridSizeOption(
                    title = stringResource(R.string.download_menu_style_classic),
                    description = stringResource(R.string.download_menu_style_classic_desc),
                    isSelected = downloadDialogStyle == io.github.aedev.flow.data.local.DownloadDialogStyle.FULL,
                    onClick = {
                        coroutineScope.launch {
                            preferences.setDownloadDialogStyle(io.github.aedev.flow.data.local.DownloadDialogStyle.FULL)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                GridSizeOption(
                    title = stringResource(R.string.download_menu_style_compact),
                    description = stringResource(R.string.download_menu_style_compact_desc),
                    isSelected = downloadDialogStyle == io.github.aedev.flow.data.local.DownloadDialogStyle.COMPACT,
                    onClick = {
                        coroutineScope.launch {
                            preferences.setDownloadDialogStyle(io.github.aedev.flow.data.local.DownloadDialogStyle.COMPACT)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun ContentHomeLayoutSection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val homeViewModeString by preferences.homeViewMode.collectAsState(initial = io.github.aedev.flow.data.local.HomeViewMode.GRID)
    val currentHomeViewMode = homeViewModeString
    val currentHomeFeedColumns by preferences.homeFeedColumns.collectAsState(initial = HomeFeedColumns.AUTO)

    SectionHeader(text = stringResource(R.string.content_settings_header_home_layout))
    SettingsGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (currentHomeViewMode ==
                        io.github.aedev.flow.data.local.HomeViewMode.GRID
                    ) {
                        Icons.Outlined.GridView
                    } else {
                        Icons.AutoMirrored.Outlined.List
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.content_settings_home_layout_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.content_settings_home_layout_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LayoutOption(
                    title = stringResource(R.string.content_settings_layout_grid),
                    icon = Icons.Outlined.GridView,
                    isSelected = currentHomeViewMode == io.github.aedev.flow.data.local.HomeViewMode.GRID,
                    onClick = {
                        coroutineScope.launch {
                            preferences.setHomeViewMode(io.github.aedev.flow.data.local.HomeViewMode.GRID)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                LayoutOption(
                    title = stringResource(R.string.content_settings_layout_list),
                    icon = Icons.AutoMirrored.Outlined.List,
                    isSelected = currentHomeViewMode == io.github.aedev.flow.data.local.HomeViewMode.LIST,
                    onClick = {
                        coroutineScope.launch {
                            preferences.setHomeViewMode(io.github.aedev.flow.data.local.HomeViewMode.LIST)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // List mode is one item per row by definition, so the count only means
            // something for the grid.
            if (currentHomeViewMode == io.github.aedev.flow.data.local.HomeViewMode.GRID) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.content_settings_home_columns_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.content_settings_home_columns_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeFeedColumns.entries.forEach { option ->
                        FlowFilterChip(
                            label =
                                when (option) {
                                    HomeFeedColumns.AUTO -> {
                                        stringResource(R.string.content_settings_home_columns_auto)
                                    }

                                    else -> {
                                        option.fixedCount.toString()
                                    }
                                },
                            selected = currentHomeFeedColumns == option,
                            onClick = {
                                coroutineScope.launch { preferences.setHomeFeedColumns(option) }
                            },
                        )
                    }
                }
            }
        }
    }
}
