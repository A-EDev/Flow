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
import io.github.aedev.flow.ui.components.shared.card.MediaVideoCard
import io.github.aedev.flow.ui.components.shared.dpadFocusGroup
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import io.github.aedev.flow.ui.theme.PlayerScrimContentSecondary
import io.github.aedev.flow.ui.tv.focus.ProvideTvRowPivot

/** Three cards across and the edge of a fourth, so the row reads as one that scrolls on. */
private const val UP_NEXT_CARD_FRACTION = 0.31f

private val UpNextCardSpacing = 16.dp

/**
 * The row under the player controls: the rest of the queue when one is playing, otherwise the related
 * videos, as the phone's cards. Their text takes the over-video inks, since the row sits on the
 * player's scrim rather than on the page.
 */
@Composable
internal fun TvUpNextRail(
    queue: List<Video>,
    currentQueueIndex: Int,
    relatedVideos: List<Video>,
    onVideoClick: (Video) -> Unit,
    firstItemFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val upcoming = queue.drop(currentQueueIndex + 1)
    val videos = upcoming.ifEmpty { relatedVideos }
    if (videos.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(if (upcoming.isNotEmpty()) R.string.tv_player_queue else R.string.up_next),
            style = MaterialTheme.typography.labelLarge,
            color = PlayerScrimContentSecondary,
        )
        MaterialTheme(
            colorScheme =
                MaterialTheme.colorScheme.copy(
                    onSurface = PlayerScrimContent,
                    onSurfaceVariant = PlayerScrimContentSecondary,
                ),
        ) {
            ProvideTvRowPivot {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().dpadFocusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(UpNextCardSpacing),
                ) {
                    itemsIndexed(videos, key = { index, video -> "${video.id}_$index" }) { index, video ->
                        MediaVideoCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                            useInternalPadding = false,
                            modifier =
                                Modifier
                                    .fillParentMaxWidth(UP_NEXT_CARD_FRACTION)
                                    .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier),
                        )
                    }
                }
            }
        }
    }
}
