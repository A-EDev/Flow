package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlayerRelatedCardStyle
import io.github.aedev.flow.ui.theme.GridItemSize
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember { PlayerPreferences(context) }
    
    val gridSizeString by preferences.gridItemSize.collectAsState(initial = "BIG")
    val currentGridSize = try {
        GridItemSize.valueOf(gridSizeString)
    } catch (e: Exception) {
        GridItemSize.BIG
    }
    
    val isShortsShelfEnabled by preferences.shortsShelfEnabled.collectAsState(initial = true)
    val isHomeShortsShelfEnabled by preferences.homeShortsShelfEnabled.collectAsState(initial = true)
    val isShortsNavigationEnabled by preferences.shortsNavigationEnabled.collectAsState(initial = true)
    val isMusicNavigationEnabled by preferences.musicNavigationEnabled.collectAsState(initial = true)
    val isSearchNavigationEnabled by preferences.searchNavigationEnabled.collectAsState(initial = false)
    val isCategoriesNavigationEnabled by preferences.categoriesNavigationEnabled.collectAsState(initial = false)
    val isContinueWatchingEnabled by preferences.continueWatchingEnabled.collectAsState(initial = true)
    val showRelatedVideos by preferences.showRelatedVideos.collectAsState(initial = true)
    
    val homeViewModeString by preferences.homeViewMode.collectAsState(initial = io.github.aedev.flow.data.local.HomeViewMode.GRID)
    val currentHomeViewMode = homeViewModeString ?: io.github.aedev.flow.data.local.HomeViewMode.GRID

    val homeFeedEnabled by preferences.homeFeedEnabled.collectAsState(initial = true)
    val currentRelatedCardStyle by preferences.playerRelatedCardStyle.collectAsState(initial = PlayerRelatedCardStyle.COMPACT)
    val hideWatchedVideos by preferences.hideWatchedVideos.collectAsState(initial = false)
    val disableShortsPlayer by preferences.disableShortsPlayer.collectAsState(initial = false)
    val showRegionPickerInExplore by preferences.showRegionPickerInExplore.collectAsState(initial = true)
    val videoTitleMaxLines by preferences.videoTitleMaxLines.collectAsState(initial = 1)
    
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.content_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Layout Settings Section
            item {
                SectionHeader(text = stringResource(R.string.content_settings_header_display))
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.GridView,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.content_settings_grid_size_title),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.content_settings_grid_size_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                modifier = Modifier.weight(1f)
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
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Home Layout Section
            item {
                SectionHeader(text = stringResource(R.string.content_settings_header_home_layout))
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (currentHomeViewMode == io.github.aedev.flow.data.local.HomeViewMode.GRID) Icons.Outlined.GridView else Icons.Outlined.List,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.content_settings_home_layout_title),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.content_settings_home_layout_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                modifier = Modifier.weight(1f)
                            )
                            LayoutOption(
                                title = stringResource(R.string.content_settings_layout_list),
                                icon = Icons.Outlined.List,
                                isSelected = currentHomeViewMode == io.github.aedev.flow.data.local.HomeViewMode.LIST,
                                onClick = {
                                    coroutineScope.launch {
                                        preferences.setHomeViewMode(io.github.aedev.flow.data.local.HomeViewMode.LIST)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // Home Feed Section
            item {
                SectionHeader(text = stringResource(R.string.content_settings_header_home_feed))
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Home,
                        title = stringResource(R.string.content_settings_home_feed_title),
                        subtitle = stringResource(R.string.content_settings_home_feed_subtitle),
                        checked = homeFeedEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setHomeFeedEnabled(enabled)
                            }
                        }
                    )
                }
            }

            // Content Components Section
            item {
                SectionHeader(text = stringResource(R.string.content_settings_header_content_components))
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.ViewQuilt,
                        title = stringResource(R.string.settings_subs_shorts_shelf_title),
                        subtitle = stringResource(R.string.settings_subs_shorts_shelf_subtitle),
                        checked = isShortsShelfEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setShortsShelfEnabled(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.DesktopWindows,
                        title = stringResource(R.string.settings_home_shorts_shelf_title),
                        subtitle = stringResource(R.string.settings_home_shorts_shelf_subtitle),
                        checked = isHomeShortsShelfEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setHomeShortsShelfEnabled(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.ViewAgenda,
                        title = stringResource(R.string.settings_continue_watching_title),
                        subtitle = stringResource(R.string.settings_continue_watching_subtitle),
                        checked = isContinueWatchingEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setContinueWatchingEnabled(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.List,
                        title = stringResource(R.string.settings_show_related_videos_title),
                        subtitle = stringResource(R.string.settings_show_related_videos_subtitle),
                        checked = showRelatedVideos,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setShowRelatedVideos(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.VisibilityOff,
                        title = stringResource(R.string.content_settings_hide_watched_title),
                        subtitle = stringResource(R.string.content_settings_hide_watched_subtitle),
                        checked = hideWatchedVideos,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setHideWatchedVideos(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.SmartDisplay,
                        title = stringResource(R.string.content_settings_disable_shorts_player_title),
                        subtitle = stringResource(R.string.content_settings_disable_shorts_player_subtitle),
                        checked = disableShortsPlayer,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setDisableShortsPlayer(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.content_settings_explore_region_picker_title),
                        subtitle = stringResource(R.string.content_settings_explore_region_picker_subtitle),
                        checked = showRegionPickerInExplore,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setShowRegionPickerInExplore(enabled)
                            }
                        }
                    )
                }
            }

            // Navigation Tabs Section
            item {
                SectionHeader(text = stringResource(R.string.content_settings_header_nav_tabs))
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_shorts),
                        title = stringResource(R.string.settings_shorts_nav_tab_title),
                        subtitle = stringResource(R.string.settings_shorts_nav_tab_subtitle),
                        checked = isShortsNavigationEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setShortsNavigationEnabled(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.MusicNote,
                        title = stringResource(R.string.settings_music_nav_tab_title),
                        subtitle = stringResource(R.string.settings_music_nav_tab_subtitle),
                        checked = isMusicNavigationEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setMusicNavigationEnabled(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Search,
                        title = stringResource(R.string.settings_search_nav_tab_title),
                        subtitle = stringResource(R.string.settings_search_nav_tab_subtitle),
                        checked = isSearchNavigationEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setSearchNavigationEnabled(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Explore,
                        title = stringResource(R.string.settings_categories_nav_tab_title),
                        subtitle = stringResource(R.string.settings_categories_nav_tab_subtitle),
                        checked = isCategoriesNavigationEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setCategoriesNavigationEnabled(enabled)
                            }
                        }
                    )
                }
            }

            // Video Player Section
            item {
                SectionHeader(text = stringResource(R.string.content_settings_header_player))
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.SmartDisplay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.content_settings_related_card_style_title),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.content_settings_related_card_style_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GridSizeOption(
                                title = stringResource(R.string.content_settings_related_card_compact),
                                description = stringResource(R.string.content_settings_related_card_compact_desc),
                                isSelected = currentRelatedCardStyle == PlayerRelatedCardStyle.COMPACT,
                                onClick = {
                                    coroutineScope.launch {
                                        preferences.setPlayerRelatedCardStyle(PlayerRelatedCardStyle.COMPACT)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            GridSizeOption(
                                title = stringResource(R.string.content_settings_related_card_full_width),
                                description = stringResource(R.string.content_settings_related_card_full_width_desc),
                                isSelected = currentRelatedCardStyle == PlayerRelatedCardStyle.FULL_WIDTH,
                                onClick = {
                                    coroutineScope.launch {
                                        preferences.setPlayerRelatedCardStyle(PlayerRelatedCardStyle.FULL_WIDTH)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Video Title Lines Section
            item {
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Title,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.content_settings_video_title_lines_title),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.content_settings_video_title_lines_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                1 to stringResource(R.string.content_settings_title_lines_1),
                                2 to stringResource(R.string.content_settings_title_lines_2),
                                3 to stringResource(R.string.content_settings_title_lines_3),
                                0 to stringResource(R.string.content_settings_title_lines_unlimited)
                            ).forEach { (lines, label) ->
                                val isSelected = videoTitleMaxLines == lines
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            coroutineScope.launch {
                                                preferences.setVideoTitleMaxLines(lines)
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold
                                                         else androidx.compose.ui.text.font.FontWeight.Normal
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Preview Section
            item {
                SectionHeader(text = stringResource(R.string.content_settings_header_preview))
                SettingsGroup {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val previewSize = when (currentGridSize) {
                            GridItemSize.BIG -> 140.dp
                            GridItemSize.SMALL -> 110.dp
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(previewSize)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.GridView,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${previewSize.value.toInt()}dp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = stringResource(R.string.content_settings_preview_album_art),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (currentGridSize) {
                                GridItemSize.BIG -> stringResource(R.string.content_settings_preview_big)
                                GridItemSize.SMALL -> stringResource(R.string.content_settings_preview_small)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}


@Composable
private fun LayoutOption(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GridSizeOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}
