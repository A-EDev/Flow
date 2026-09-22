package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ContentNotesSection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val notesEnabled by preferences.notesEnabled.collectAsState(initial = true)
    val channelNotesEnabled by preferences.channelNotesEnabled.collectAsState(initial = true)
    val videoNotesEnabled by preferences.videoNotesEnabled.collectAsState(initial = true)

    SectionHeader(text = stringResource(R.string.content_settings_notes_title))
    SettingsGroup {
        SettingsSwitchItem(
            icon = Icons.Outlined.StickyNote2,
            title = stringResource(R.string.content_settings_notes_title),
            subtitle = stringResource(R.string.content_settings_notes_subtitle),
            checked = notesEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch { preferences.setNotesEnabled(enabled) }
            },
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
        SettingsSwitchItem(
            icon = Icons.Outlined.Person,
            title = stringResource(R.string.content_settings_channel_notes_title),
            subtitle = stringResource(R.string.content_settings_channel_notes_subtitle),
            checked = channelNotesEnabled,
            enabled = notesEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch { preferences.setChannelNotesEnabled(enabled) }
            },
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
        SettingsSwitchItem(
            icon = Icons.Outlined.Movie,
            title = stringResource(R.string.content_settings_video_notes_title),
            subtitle = stringResource(R.string.content_settings_video_notes_subtitle),
            checked = videoNotesEnabled,
            enabled = notesEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch { preferences.setVideoNotesEnabled(enabled) }
            },
        )
    }
}

@Composable
internal fun ContentHomeFeedSection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val homeFeedEnabled by preferences.homeFeedEnabled.collectAsState(initial = true)
    val refreshHomeOnReselect by preferences.refreshHomeOnReselect.collectAsState(initial = true)
    val showAppLogoIcon by preferences.showAppLogoIcon.collectAsState(initial = true)

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
            },
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
        SettingsSwitchItem(
            icon = Icons.Outlined.Refresh,
            title = stringResource(R.string.content_settings_home_reselect_refresh_title),
            subtitle = stringResource(R.string.content_settings_home_reselect_refresh_subtitle),
            checked = refreshHomeOnReselect,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setRefreshHomeOnReselect(enabled)
                }
            },
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
        SettingsSwitchItem(
            icon =
                androidx.compose.ui.graphics.vector.ImageVector
                    .vectorResource(id = R.drawable.ic_notification_logo),
            title = stringResource(R.string.content_settings_show_app_logo_title),
            subtitle = stringResource(R.string.content_settings_show_app_logo_subtitle),
            checked = showAppLogoIcon,
            onCheckedChange = { enabled ->
                coroutineScope.launch { preferences.setShowAppLogoIcon(enabled) }
            },
        )
    }
}

@Composable
internal fun ContentShortsSection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val shortsContentEnabled by preferences.shortsContentEnabled.collectAsState(initial = true)
    val isShortsShelfEnabled by preferences.shortsShelfEnabled.collectAsState(initial = true)
    val isHomeShortsShelfEnabled by preferences.homeShortsShelfEnabled.collectAsState(initial = true)
    val isShortsNavigationEnabled by preferences.shortsNavigationEnabled.collectAsState(initial = true)
    val disableShortsPlayer by preferences.disableShortsPlayer.collectAsState(initial = false)
    val showShortsPlayerPrompt by preferences.showShortsPlayerPrompt.collectAsState(initial = true)
    val subscriptionShowShorts by preferences.subscriptionShowShorts.collectAsState(initial = true)

    SectionHeader(text = stringResource(R.string.content_settings_header_shorts))
    SettingsGroup {
        SettingsSwitchItem(
            icon =
                androidx.compose.ui.graphics.vector.ImageVector
                    .vectorResource(id = R.drawable.ic_shorts),
            title = stringResource(R.string.content_settings_shorts_content_title),
            subtitle = stringResource(R.string.content_settings_shorts_content_subtitle),
            checked = shortsContentEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setShortsContentEnabled(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon =
                androidx.compose.ui.graphics.vector.ImageVector
                    .vectorResource(id = R.drawable.ic_shorts),
            title = stringResource(R.string.settings_shorts_nav_tab_title),
            subtitle = stringResource(R.string.settings_shorts_nav_tab_subtitle),
            checked = isShortsNavigationEnabled && shortsContentEnabled,
            enabled = shortsContentEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setShortsNavigationEnabled(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon =
                androidx.compose.ui.graphics.vector.ImageVector
                    .vectorResource(id = R.drawable.ic_shorts),
            title = stringResource(R.string.settings_home_shorts_shelf_title),
            subtitle = stringResource(R.string.settings_home_shorts_shelf_subtitle),
            checked = isHomeShortsShelfEnabled && shortsContentEnabled,
            enabled = shortsContentEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setHomeShortsShelfEnabled(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon =
                androidx.compose.ui.graphics.vector.ImageVector
                    .vectorResource(id = R.drawable.ic_shorts),
            title = stringResource(R.string.settings_subs_shorts_shelf_title),
            subtitle = stringResource(R.string.settings_subs_shorts_shelf_subtitle),
            checked = isShortsShelfEnabled && shortsContentEnabled,
            enabled = shortsContentEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setShortsShelfEnabled(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon =
                androidx.compose.ui.graphics.vector.ImageVector
                    .vectorResource(id = R.drawable.ic_shorts),
            title = stringResource(R.string.content_settings_subs_show_shorts_title),
            subtitle = stringResource(R.string.content_settings_subs_show_shorts_subtitle),
            checked = subscriptionShowShorts && shortsContentEnabled,
            enabled = shortsContentEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setSubscriptionShowShorts(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon = Icons.Outlined.SmartDisplay,
            title = stringResource(R.string.content_settings_disable_shorts_player_title),
            subtitle = stringResource(R.string.content_settings_disable_shorts_player_subtitle),
            checked = disableShortsPlayer || !shortsContentEnabled,
            enabled = shortsContentEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setDisableShortsPlayer(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon = Icons.Outlined.SmartDisplay,
            title = stringResource(R.string.content_settings_shorts_player_prompt_title),
            subtitle = stringResource(R.string.content_settings_shorts_player_prompt_subtitle),
            checked = showShortsPlayerPrompt && shortsContentEnabled && !disableShortsPlayer,
            enabled = shortsContentEnabled && !disableShortsPlayer,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setShowShortsPlayerPrompt(enabled)
                }
            },
        )
    }
}

@Composable
internal fun ContentLibrarySection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val libraryShelfPreviewsEnabled by preferences.libraryShelfPreviewsEnabled.collectAsState(initial = true)

    SectionHeader(text = stringResource(R.string.content_settings_header_library))
    SettingsGroup {
        SettingsSwitchItem(
            icon = Icons.Outlined.VideoLibrary,
            title = stringResource(R.string.content_settings_library_previews_title),
            subtitle = stringResource(R.string.content_settings_library_previews_subtitle),
            checked = libraryShelfPreviewsEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setLibraryShelfPreviewsEnabled(enabled)
                }
            },
        )
    }
}
