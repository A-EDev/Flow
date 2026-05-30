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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ThumbUp
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
    val showAppLogoIcon by preferences.showAppLogoIcon.collectAsState(initial = true)
    val currentRelatedCardStyle by preferences.playerRelatedCardStyle.collectAsState(initial = PlayerRelatedCardStyle.COMPACT)
    val hideWatchedVideos by preferences.hideWatchedVideos.collectAsState(initial = false)
    val disableShortsPlayer by preferences.disableShortsPlayer.collectAsState(initial = false)
    val showRegionPickerInExplore by preferences.showRegionPickerInExplore.collectAsState(initial = true)
    val videoTitleMaxLines by preferences.videoTitleMaxLines.collectAsState(initial = 1)
    val videoCardActionsEnabled by preferences.videoCardActionsEnabled.collectAsState(initial = false)
    val videoCardMarkWatchedEnabled by preferences.videoCardMarkWatchedEnabled.collectAsState(initial = false)
    val subscriptionRefreshOnStartup by preferences.subscriptionRefreshOnStartup.collectAsState(initial = false)
    val commentsEnabled by preferences.commentsEnabled.collectAsState(initial = true)
    val commentsPreviewEnabled by preferences.commentsPreviewEnabled.collectAsState(initial = true)
    val subscriptionShowVideos by preferences.subscriptionShowVideos.collectAsState(initial = true)
    val subscriptionShowShorts by preferences.subscriptionShowShorts.collectAsState(initial = true)
    val subscriptionShowLive by preferences.subscriptionShowLive.collectAsState(initial = true)
    val navTabOrder by preferences.navTabOrder.collectAsState(initial = io.github.aedev.flow.data.local.DEFAULT_NAV_TAB_ORDER)
    val defaultNavTabIndex by preferences.defaultNavTabIndex.collectAsState(initial = 0)
    val downloadDialogStyle by preferences.downloadDialogStyle.collectAsState(initial = io.github.aedev.flow.data.local.DownloadDialogStyle.FULL)
    
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
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

            // Download Menu Style Section
            item {
                SectionHeader(text = stringResource(R.string.download_menu_style_title))
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.ViewAgenda,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.download_menu_style_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                modifier = Modifier.weight(1f)
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
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsSwitchItem(
                        icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_notification_logo),
                        title = stringResource(R.string.content_settings_show_app_logo_title),
                        subtitle = stringResource(R.string.content_settings_show_app_logo_subtitle),
                        checked = showAppLogoIcon,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch { preferences.setShowAppLogoIcon(enabled) }
                        }
                    )
                }
            }

            // Content Components Section
            item {
                SectionHeader(text = stringResource(R.string.content_settings_header_content_components))
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_shorts),
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
                        icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_shorts),
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
                        icon = Icons.Outlined.Comment,
                        title = stringResource(R.string.content_settings_comments_enabled_title),
                        subtitle = stringResource(R.string.content_settings_comments_enabled_subtitle),
                        checked = commentsEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setCommentsEnabled(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Comment,
                        title = stringResource(R.string.content_settings_comments_preview_title),
                        subtitle = stringResource(R.string.content_settings_comments_preview_subtitle),
                        checked = commentsPreviewEnabled,
                        enabled = commentsEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setCommentsPreviewEnabled(enabled)
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
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.ThumbUp,
                        title = stringResource(R.string.content_settings_video_card_actions_title),
                        subtitle = stringResource(R.string.content_settings_video_card_actions_subtitle),
                        checked = videoCardActionsEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setVideoCardActionsEnabled(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Visibility,
                        title = stringResource(R.string.content_settings_video_card_mark_watched_title),
                        subtitle = stringResource(R.string.content_settings_video_card_mark_watched_subtitle),
                        checked = videoCardMarkWatchedEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setVideoCardMarkWatchedEnabled(enabled)
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
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Subscriptions,
                        title = stringResource(R.string.content_settings_subs_startup_refresh_title),
                        subtitle = stringResource(R.string.content_settings_subs_startup_refresh_subtitle),
                        checked = subscriptionRefreshOnStartup,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setSubscriptionRefreshOnStartup(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.VideoLibrary,
                        title = stringResource(R.string.content_settings_subs_show_videos_title),
                        subtitle = stringResource(R.string.content_settings_subs_show_videos_subtitle),
                        checked = subscriptionShowVideos,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setSubscriptionShowVideos(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_shorts),
                        title = stringResource(R.string.content_settings_subs_show_shorts_title),
                        subtitle = stringResource(R.string.content_settings_subs_show_shorts_subtitle),
                        checked = subscriptionShowShorts,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setSubscriptionShowShorts(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Subscriptions,
                        title = stringResource(R.string.content_settings_subs_show_live_title),
                        subtitle = stringResource(R.string.content_settings_subs_show_live_subtitle),
                        checked = subscriptionShowLive,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setSubscriptionShowLive(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    NavTabOrderSettings(
                        order = navTabOrder,
                        defaultTabIndex = defaultNavTabIndex,
                        onMove = { index, direction ->
                            val currentIndex = navTabOrder.indexOf(index)
                            val targetIndex = (currentIndex + direction).coerceIn(0, navTabOrder.lastIndex)
                            if (currentIndex >= 0 && currentIndex != targetIndex) {
                                val updated = navTabOrder.toMutableList()
                                val moved = updated.removeAt(currentIndex)
                                updated.add(targetIndex, moved)
                                coroutineScope.launch {
                                    preferences.setNavTabOrder(updated)
                                }
                            }
                        },
                        onDefaultSelected = { index ->
                            coroutineScope.launch {
                                preferences.setDefaultNavTabIndex(index)
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

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}


@Composable
private fun NavTabOrderSettings(
    order: List<Int>,
    defaultTabIndex: Int,
    onMove: (index: Int, direction: Int) -> Unit,
    onDefaultSelected: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.DragIndicator,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.content_settings_nav_order_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.content_settings_nav_order_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        order.forEachIndexed { position, index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = defaultTabIndex == index,
                    onClick = { onDefaultSelected(index) }
                )
                Icon(
                    navTabIcon(index),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = navTabLabel(index),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onMove(index, -1) },
                    enabled = position > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                }
                IconButton(
                    onClick = { onMove(index, 1) },
                    enabled = position < order.lastIndex,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
                }
            }
        }
    }
}

@Composable
private fun navTabLabel(index: Int): String = when (index) {
    0 -> stringResource(R.string.nav_home)
    1 -> stringResource(R.string.nav_shorts)
    2 -> stringResource(R.string.nav_music)
    3 -> stringResource(R.string.nav_subs)
    4 -> stringResource(R.string.nav_library)
    5 -> stringResource(R.string.nav_search)
    6 -> stringResource(R.string.nav_explore)
    else -> stringResource(R.string.nav_home)
}

@Composable
private fun navTabIcon(index: Int): ImageVector = when (index) {
    0 -> Icons.Outlined.Home
    1 -> ImageVector.vectorResource(id = R.drawable.ic_shorts)
    2 -> Icons.Outlined.MusicNote
    3 -> Icons.Outlined.Subscriptions
    4 -> Icons.Outlined.VideoLibrary
    5 -> Icons.Outlined.Search
    6 -> Icons.Outlined.Explore
    else -> Icons.Outlined.Home
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
