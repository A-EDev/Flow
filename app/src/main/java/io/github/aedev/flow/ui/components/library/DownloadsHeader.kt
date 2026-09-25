package io.github.aedev.flow.ui.components.library

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

/**
 * How much space downloads take, split by kind, against what is free on the volume they are saved
 * to. The bar is the share of used plus free that downloads take.
 */
@Composable
internal fun DownloadsStorageCard(
    videoBytes: Long,
    musicBytes: Long,
    freeBytes: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val used = videoBytes + musicBytes
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.downloads_storage_used, Formatter.formatShortFileSize(context, used)),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (freeBytes > 0L) {
                    Text(
                        text = stringResource(R.string.downloads_storage_free, Formatter.formatShortFileSize(context, freeBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val total = (used + freeBytes).coerceAtLeast(1L)
            LinearProgressIndicator(
                progress = { used.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            val separator = stringResource(R.string.metadata_separator)
            Text(
                text =
                    listOf(
                        stringResource(R.string.downloads_storage_videos, Formatter.formatShortFileSize(context, videoBytes)),
                        stringResource(R.string.downloads_storage_music, Formatter.formatShortFileSize(context, musicBytes)),
                    ).joinToString(" $separator "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
