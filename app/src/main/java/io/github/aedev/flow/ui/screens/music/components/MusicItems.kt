package io.github.aedev.flow.ui.screens.music.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.PlayingWaveform
import io.github.aedev.flow.ui.components.pressScale
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.screens.music.formatDuration
import io.github.aedev.flow.ui.screens.music.formatViews
import io.github.aedev.flow.ui.theme.Dimensions
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: MusicTrack,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isDownloaded: Boolean = false,
    showMenu: Boolean = true,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    thumbnailOverlay: (@Composable BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMenuClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val reduceMotion = rememberFlowReduceMotion()
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Dimensions.ListItemHeight)
                .pressScale(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(Dimensions.ListThumbnailSize),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(track.listThumbnailUrl)
                            .crossfade(!reduceMotion)
                            .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                AnimatedVisibility(
                    visible = isPlaying,
                    modifier = Modifier.fillMaxSize(),
                    enter =
                        fadeIn(
                            tween(
                                durationMillis =
                                    FlowMotion.durationFor(
                                        FlowMotion.FEEDBACK_DURATION_MILLIS,
                                        reduceMotion,
                                    ),
                            ),
                        ),
                    exit =
                        fadeOut(
                            tween(
                                durationMillis =
                                    FlowMotion.durationFor(
                                        FlowMotion.FEEDBACK_DURATION_MILLIS,
                                        reduceMotion,
                                    ),
                            ),
                        ),
                    label = "trackPlayingOverlay",
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.46f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MusicWaveAnimation(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(width = 28.dp, height = 24.dp),
                        )
                    }
                }

                thumbnailOverlay?.invoke(this)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
            ) {
                if (track.isExplicit == true) {
                    ExplicitBadge()
                }

                Text(
                    text = track.musicMetadataLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isDownloaded) {
            Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = stringResource(R.string.status_downloaded),
                tint = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .padding(start = 8.dp)
                        .size(18.dp),
            )
        }

        trailingContent?.invoke(this)

        if (showMenu) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MusicWaveAnimation(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    PlayingWaveform(
        modifier = modifier,
        color = color,
        minBarHeight = 8.dp,
        maxBarHeight = 20.dp,
        cycleMillis = 400,
    )
}

@Composable
private fun ExplicitBadge() {
    Icon(
        painter = painterResource(R.drawable.ic_explicit),
        contentDescription = stringResource(R.string.label_explicit),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun MusicTrack.musicMetadataLine(): String {
    val suffix =
        when {
            duration > 0 -> formatDuration(duration)
            views > 0 -> formatViews(views)
            else -> null
        }
    return if (suffix != null) stringResource(R.string.year_artist_template, artist, suffix) else artist
}
