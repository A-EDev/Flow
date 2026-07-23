package io.github.aedev.flow.ui.tv.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.tv.components.TvMusicTrackRow
import io.github.aedev.flow.ui.tv.components.TvSidePanel

/** Music queue side panel: current queue plus the automix (radio) continuation. */
@Composable
fun BoxScope.TvMusicQueuePanel(
    visible: Boolean,
    manager: EnhancedMusicPlayerManager,
) {
    val queue by manager.queue.collectAsStateWithLifecycle()
    val automix by manager.automixItems.collectAsStateWithLifecycle()
    val currentIndex by manager.currentQueueIndex.collectAsStateWithLifecycle()

    TvSidePanel(
        visible = visible,
        title = stringResource(R.string.tv_player_queue),
    ) {
        if (queue.isEmpty() && automix.isEmpty()) {
            Text(
                text = stringResource(R.string.tv_library_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@TvSidePanel
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(queue, key = { index, item -> "queue:$index:${item.videoId}" }) { index, item ->
                TvMusicTrackRow(
                    track = item,
                    selected = index == currentIndex,
                    onClick = { manager.playFromQueue(index) },
                )
            }
            if (automix.isNotEmpty()) {
                item(key = "automix-header") {
                    Text(
                        text = stringResource(R.string.tv_music_radio),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(automix, key = { index, item -> "automix:$index:${item.videoId}" }) { _, item ->
                    TvMusicTrackRow(
                        track = item,
                        onClick = { manager.playNext(item) },
                    )
                }
            }
        }
    }
}
