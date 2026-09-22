package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlayerRelatedCardStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ContentPlayerSection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val currentRelatedCardStyle by preferences.playerRelatedCardStyle.collectAsState(initial = PlayerRelatedCardStyle.COMPACT)

    SectionHeader(text = stringResource(R.string.content_settings_header_player))
    SettingsGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.SmartDisplay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.content_settings_related_card_style_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.content_settings_related_card_style_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun ContentTitleLinesSection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val videoTitleMaxLines by preferences.videoTitleMaxLines.collectAsState(initial = 1)

    SettingsGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Title,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.content_settings_video_title_lines_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.content_settings_video_title_lines_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    1 to stringResource(R.string.content_settings_title_lines_1),
                    2 to stringResource(R.string.content_settings_title_lines_2),
                    3 to stringResource(R.string.content_settings_title_lines_3),
                    0 to stringResource(R.string.content_settings_title_lines_unlimited),
                ).forEach { (lines, label) ->
                    val isSelected = videoTitleMaxLines == lines
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(
                                    androidx.compose.foundation.shape
                                        .RoundedCornerShape(12.dp),
                                ).background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    } else {
                                        androidx.compose.ui.graphics.Color.Transparent
                                    },
                                ).border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        },
                                    shape =
                                        androidx.compose.foundation.shape
                                            .RoundedCornerShape(12.dp),
                                ).clickable {
                                    coroutineScope.launch {
                                        preferences.setVideoTitleMaxLines(lines)
                                    }
                                }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight =
                                        if (isSelected) {
                                            androidx.compose.ui.text.font.FontWeight.SemiBold
                                        } else {
                                            androidx.compose.ui.text.font.FontWeight.Normal
                                        },
                                ),
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }
    }
}
