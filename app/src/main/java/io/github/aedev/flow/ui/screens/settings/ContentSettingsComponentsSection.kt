package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.WatchedThreshold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ContentComponentsSection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val isContinueWatchingEnabled by preferences.continueWatchingEnabled.collectAsState(initial = true)
    val showRestoredMusicMiniPlayer by preferences.showRestoredMusicMiniPlayer.collectAsState(initial = true)
    val showRelatedVideos by preferences.showRelatedVideos.collectAsState(initial = true)
    val commentsEnabled by preferences.commentsEnabled.collectAsState(initial = true)
    val commentsPreviewEnabled by preferences.commentsPreviewEnabled.collectAsState(initial = true)
    val hideWatchedVideosFromHome by preferences.hideWatchedVideosFromHome.collectAsState(initial = false)
    val hideWatchedVideosFromSubscriptions by preferences.hideWatchedVideosFromSubscriptions.collectAsState(initial = false)
    val hideUnplayableVideosFromSubscriptions by preferences.hideUnplayableVideosFromSubscriptions.collectAsState(initial = false)
    val watchedThreshold by preferences.watchedThreshold.collectAsState(
        initial = io.github.aedev.flow.data.local.WatchedThreshold.ALMOST_FINISHED,
    )
    var showWatchedThresholdDialog by remember { mutableStateOf(false) }
    val shareWithoutText by preferences.shareWithoutText.collectAsState(initial = false)
    val videoCardActionsEnabled by preferences.videoCardActionsEnabled.collectAsState(initial = false)
    val videoCardMarkWatchedEnabled by preferences.videoCardMarkWatchedEnabled.collectAsState(initial = false)

    SectionHeader(text = stringResource(R.string.content_settings_header_content_components))
    SettingsGroup {
        SettingsSwitchItem(
            icon = Icons.Outlined.ViewAgenda,
            title = stringResource(R.string.settings_continue_watching_title),
            subtitle = stringResource(R.string.settings_continue_watching_subtitle),
            checked = isContinueWatchingEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setContinueWatchingEnabled(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon = Icons.Outlined.MusicNote,
            title = stringResource(R.string.content_settings_restored_music_mini_player_title),
            subtitle = stringResource(R.string.content_settings_restored_music_mini_player_subtitle),
            checked = showRestoredMusicMiniPlayer,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setShowRestoredMusicMiniPlayer(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon = Icons.AutoMirrored.Outlined.List,
            title = stringResource(R.string.settings_show_related_videos_title),
            subtitle = stringResource(R.string.settings_show_related_videos_subtitle),
            checked = showRelatedVideos,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setShowRelatedVideos(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon = Icons.AutoMirrored.Outlined.Comment,
            title = stringResource(R.string.content_settings_comments_enabled_title),
            subtitle = stringResource(R.string.content_settings_comments_enabled_subtitle),
            checked = commentsEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setCommentsEnabled(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon = Icons.AutoMirrored.Outlined.Comment,
            title = stringResource(R.string.content_settings_comments_preview_title),
            subtitle = stringResource(R.string.content_settings_comments_preview_subtitle),
            checked = commentsPreviewEnabled,
            enabled = commentsEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setCommentsPreviewEnabled(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon = Icons.Outlined.VisibilityOff,
            title = stringResource(R.string.content_settings_hide_watched_home_title),
            subtitle = stringResource(R.string.content_settings_hide_watched_home_subtitle),
            checked = hideWatchedVideosFromHome,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setHideWatchedVideosFromHome(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon = Icons.Outlined.VisibilityOff,
            title = stringResource(R.string.content_settings_hide_watched_subscriptions_title),
            subtitle = stringResource(R.string.content_settings_hide_watched_subscriptions_subtitle),
            checked = hideWatchedVideosFromSubscriptions,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setHideWatchedVideosFromSubscriptions(enabled)
                }
            },
        )
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon = Icons.Outlined.Block,
            title = stringResource(R.string.content_settings_hide_unplayable_subscriptions_title),
            subtitle = stringResource(R.string.content_settings_hide_unplayable_subscriptions_subtitle),
            checked = hideUnplayableVideosFromSubscriptions,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setHideUnplayableVideosFromSubscriptions(enabled)
                }
            },
        )
        if (hideWatchedVideosFromHome || hideWatchedVideosFromSubscriptions) {
            HorizontalDivider(
                Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            SettingsItem(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.content_settings_watched_threshold_title),
                subtitle = watchedThresholdLabel(watchedThreshold),
                onClick = { showWatchedThresholdDialog = true },
            )
        }
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        SettingsSwitchItem(
            icon = Icons.Outlined.Share,
            title = stringResource(R.string.content_settings_share_without_text_title),
            subtitle = stringResource(R.string.content_settings_share_without_text_subtitle),
            checked = shareWithoutText,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    preferences.setShareWithoutText(enabled)
                }
            },
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
            },
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
            },
        )
    }

    if (showWatchedThresholdDialog) {
        AlertDialog(
            onDismissRequest = { showWatchedThresholdDialog = false },
            title = {
                Text(
                    stringResource(R.string.content_settings_watched_threshold_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.content_settings_watched_threshold_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    listOf(
                        WatchedThreshold.ALMOST_FINISHED,
                        WatchedThreshold.PERCENT_99,
                        WatchedThreshold.PERCENT_95,
                        WatchedThreshold.PERCENT_90,
                    ).forEach { option ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch { preferences.setWatchedThreshold(option) }
                                        showWatchedThresholdDialog = false
                                    }.padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = watchedThreshold == option, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = watchedThresholdLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWatchedThresholdDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            },
        )
    }
}

@Composable
private fun watchedThresholdLabel(threshold: WatchedThreshold): String =
    when (threshold) {
        WatchedThreshold.PERCENT_90 -> stringResource(R.string.content_settings_watched_threshold_90)
        WatchedThreshold.PERCENT_95 -> stringResource(R.string.content_settings_watched_threshold_95)
        WatchedThreshold.PERCENT_99 -> stringResource(R.string.content_settings_watched_threshold_99)
        WatchedThreshold.ALMOST_FINISHED -> stringResource(R.string.content_settings_watched_threshold_almost)
    }
