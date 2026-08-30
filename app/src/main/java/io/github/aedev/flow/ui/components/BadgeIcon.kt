package io.github.aedev.flow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import io.github.aedev.flow.R

object BadgeIcon {
    private val BadgeSize = 14.dp
    private val BadgeSpacing = 4.dp

    @Composable
    fun Explicit(modifier: Modifier = Modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                modifier
                    .padding(end = BadgeSpacing)
                    .size(16.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Text(
                text = stringResource(R.string.explicit_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    fun Download(
        state: Int?,
        modifier: Modifier = Modifier,
    ) {
        val icon =
            when (state) {
                Download.STATE_QUEUED -> Icons.Outlined.Schedule
                Download.STATE_DOWNLOADING -> Icons.Outlined.Downloading
                Download.STATE_COMPLETED -> Icons.Outlined.DownloadDone
                else -> return
            }

        val tint =
            when (state) {
                Download.STATE_COMPLETED -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

        Icon(
            imageVector = icon,
            contentDescription =
                when (state) {
                    Download.STATE_QUEUED -> stringResource(R.string.ui_queued_for_download)
                    Download.STATE_DOWNLOADING -> stringResource(R.string.ui_downloading)
                    Download.STATE_COMPLETED -> stringResource(R.string.downloaded)
                    else -> null
                },
            tint = tint,
            modifier =
                modifier
                    .padding(end = BadgeSpacing)
                    .size(BadgeSize),
        )
    }

    @Composable
    fun ChartPosition(
        position: Int,
        modifier: Modifier = Modifier,
    ) {
        val color =
            when {
                position <= 3 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

        Text(
            text = "#$position",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = modifier.padding(end = BadgeSpacing),
        )
    }
}
