package io.github.aedev.flow.ui.components.shorts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.FlowSwitchRow

private val SheetBottomPadding = 32.dp
private val DividerPadding = 24.dp
private val RowProgressSize = 18.dp
private val RowProgressStroke = 2.dp

@Composable
internal fun ShortsOptionsSheet(
    isLoadingStreams: Boolean,
    ambientModeEnabled: Boolean,
    currentSpeed: Float,
    onAmbientModeToggle: (Boolean) -> Unit,
    onWantMore: () -> Unit,
    onNotInterested: () -> Unit,
    onDownloadClick: () -> Unit,
    onAudioTrackClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    sheetInsets: ShortsSheetInsetState,
    onDismiss: () -> Unit,
) {
    ShortsPlayerSheet(insets = sheetInsets, onDismiss = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = SheetBottomPadding),
        ) {
            FlowSheetHeader(title = stringResource(R.string.cd_more_options), onClose = onDismiss)
            FlowSwitchRow(
                title = stringResource(R.string.player_settings_ambient_mode),
                checked = ambientModeEnabled,
                onCheckedChange = onAmbientModeToggle,
                leadingIcon = ImageVector.vectorResource(R.drawable.ic_ambient_mode),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = DividerPadding))
            ShortsOptionRow(icon = Icons.Rounded.ThumbUp, title = stringResource(R.string.action_want_more), onClick = onWantMore)
            ShortsOptionRow(
                icon = Icons.Rounded.NotInterested,
                title = stringResource(R.string.action_not_interested),
                onClick = onNotInterested,
            )
            ShortsOptionRow(
                icon = Icons.Default.Download,
                title = stringResource(R.string.download_video),
                onClick = onDownloadClick,
                enabled = !isLoadingStreams,
                loading = isLoadingStreams,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = DividerPadding))
            ShortsOptionRow(
                icon = Icons.Outlined.AudioFile,
                title = stringResource(R.string.shorts_audio_track),
                onClick = onAudioTrackClick,
                enabled = !isLoadingStreams,
                loading = isLoadingStreams,
            )
            ShortsOptionRow(
                icon = Icons.Outlined.HighQuality,
                title = stringResource(R.string.shorts_quality),
                onClick = onQualityClick,
                enabled = !isLoadingStreams,
                loading = isLoadingStreams,
            )
            ShortsOptionRow(
                icon = Icons.Rounded.Speed,
                title = stringResource(R.string.shorts_playback_speed),
                onClick = onSpeedClick,
                trailingText =
                    if (currentSpeed == 1f) {
                        stringResource(R.string.normal)
                    } else {
                        stringResource(R.string.playback_speed_multiplier, currentSpeed.toString())
                    },
            )
        }
    }
}

@Composable
private fun ShortsOptionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingText: String? = null,
) {
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick),
        enabled = enabled,
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        trailingContent =
            when {
                loading -> {
                    { CircularProgressIndicator(modifier = Modifier.size(RowProgressSize), strokeWidth = RowProgressStroke) }
                }

                trailingText != null -> {
                    {
                        Text(
                            text = trailingText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    null
                }
            },
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}
