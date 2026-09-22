package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.components.layout.navigation.NavigationVisibility
import io.github.aedev.flow.ui.components.layout.navigation.resolveDefaultFlowTab
import io.github.aedev.flow.ui.components.layout.navigation.visibleFlowTabs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ContentNavigationSection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val shortsContentEnabled by preferences.shortsContentEnabled.collectAsState(initial = true)
    val isHomeNavigationEnabled by preferences.homeNavigationEnabled.collectAsState(initial = true)
    val isShortsNavigationEnabled by preferences.shortsNavigationEnabled.collectAsState(initial = true)
    val isMusicNavigationEnabled by preferences.musicNavigationEnabled.collectAsState(initial = true)
    val isSearchNavigationEnabled by preferences.searchNavigationEnabled.collectAsState(initial = false)
    val isCategoriesNavigationEnabled by preferences.categoriesNavigationEnabled.collectAsState(initial = false)
    val bottomNavHideOnScroll by preferences.bottomNavHideOnScroll.collectAsState(initial = true)
    val subscriptionRefreshOnStartup by preferences.subscriptionRefreshOnStartup.collectAsState(initial = false)
    val subscriptionShowCheckedVideoCount by preferences.subscriptionShowCheckedVideoCount.collectAsState(initial = true)
    val subscriptionShowVideos by preferences.subscriptionShowVideos.collectAsState(initial = true)
    val subscriptionShowLive by preferences.subscriptionShowLive.collectAsState(initial = true)
    val navTabOrder by preferences.navTabOrder.collectAsState(initial = io.github.aedev.flow.data.local.DEFAULT_NAV_TAB_ORDER)
    val defaultNavTabIndex by preferences.defaultNavTabIndex.collectAsState(initial = 0)
    val navigationVisibility =
        NavigationVisibility(
            home = isHomeNavigationEnabled,
            shorts = isShortsNavigationEnabled && shortsContentEnabled,
            music = isMusicNavigationEnabled,
            search = isSearchNavigationEnabled,
            categories = isCategoriesNavigationEnabled,
        )
    val visibleNavTabs = visibleFlowTabs(navTabOrder, navigationVisibility)
    val defaultNavTab = resolveDefaultFlowTab(defaultNavTabIndex, navTabOrder, navigationVisibility)

    SectionHeader(text = stringResource(R.string.content_settings_header_nav_tabs))
    SettingsGroup {
        SettingsSwitchItem(
            icon = Icons.Outlined.Home,
            title = stringResource(R.string.settings_home_nav_tab_title),
            subtitle = stringResource(R.string.settings_home_nav_tab_subtitle),
            checked = isHomeNavigationEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setHomeNavigationEnabled(enabled)
                }
            },
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
            },
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
            },
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
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon = Icons.Default.KeyboardArrowDown,
            title = stringResource(R.string.content_settings_navbar_hide_on_scroll_title),
            subtitle = stringResource(R.string.content_settings_navbar_hide_on_scroll_subtitle),
            checked = bottomNavHideOnScroll,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setBottomNavHideOnScroll(enabled)
                }
            },
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
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon = Icons.Outlined.Visibility,
            title = stringResource(R.string.content_settings_subs_show_checked_count_title),
            subtitle = stringResource(R.string.content_settings_subs_show_checked_count_subtitle),
            checked = subscriptionShowCheckedVideoCount,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setSubscriptionShowCheckedVideoCount(enabled)
                }
            },
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
            },
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
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        NavTabOrderSettings(
            order = navTabOrder,
            enabledTabs = visibleNavTabs.toSet(),
            defaultTab = defaultNavTab,
            onOrderChanged = { updated ->
                coroutineScope.launch {
                    preferences.setNavTabOrder(updated)
                }
            },
            onDefaultSelected = { tab ->
                coroutineScope.launch {
                    preferences.setDefaultNavTabIndex(tab.id)
                }
            },
        )
    }
}
