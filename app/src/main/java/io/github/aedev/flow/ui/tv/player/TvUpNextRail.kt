package io.github.aedev.flow.ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.tv.components.TvVideoCard
import io.github.aedev.flow.ui.tv.focus.ProvideTvRowPivot
import io.github.aedev.flow.ui.tv.focus.tvRowFocus
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * Up-next shelf in the player overlay: the active queue when one exists,
 * otherwise related videos. The current queue item is marked selected.
 */
@Composable
fun TvUpNextRail(
    queue: List<Video>,
    currentQueueIndex: Int,
    relatedVideos: List<Video>,
    onVideoClick: (Video) -> Unit,
    firstItemFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val videos = if (queue.isNotEmpty()) queue else relatedVideos
    if (videos.isEmpty()) return
    val dimens = LocalTvDimens.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (queue.isNotEmpty()) {
                stringResource(R.string.tv_player_queue)
            } else {
                stringResource(R.string.up_next)
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProvideTvRowPivot {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvRowFocus(),
                horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
            ) {
                itemsIndexed(videos, key = { index, video -> "${video.id}_$index" }) { index, video ->
                    TvVideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}
