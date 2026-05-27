package io.github.aedev.flow.ui.screens.music.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.animation.core.*
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.screens.music.formatDuration
import io.github.aedev.flow.ui.screens.music.formatViews
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import androidx.compose.foundation.background

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
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(56.dp),
            tonalElevation = 4.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.listThumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.46f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MusicWaveAnimation(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(width = 28.dp, height = 24.dp)
                        )
                    }
                }
                thumbnailOverlay?.invoke(this)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (track.isExplicit == true) {
                    ExplicitBadge()
                }

                Text(
                    text = track.musicMetadataLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        if (isDownloaded) {
            Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = stringResource(R.string.status_downloaded),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
            )
        }

        if (trailingContent != null) {
            trailingContent()
        }

        if (showMenu) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ArtistGridItem(
    artist: String,
    thumbnailUrl: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            modifier = Modifier.size(100.dp),
            tonalElevation = 4.dp
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun QuickPickItem(
    track: MusicTrack,
    isPlaying: Boolean = false,
    isDownloaded: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMenuClick: () -> Unit
) {
    TrackListItem(
        track = track,
        isPlaying = isPlaying,
        isDownloaded = isDownloaded,
        onClick = onClick,
        onLongClick = onLongClick,
        onMenuClick = onMenuClick,
        modifier = Modifier.width(320.dp) // Fixed width for horizontal lists
    )
}

@Composable
fun CompactTrackCard(
    track: MusicTrack,
    isDownloaded: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null
) {
    TrackListItem(
        track = track,
        isDownloaded = isDownloaded,
        onClick = onClick,
        onLongClick = onLongClick,
        showMenu = onMenuClick != null,
        onMenuClick = { onMenuClick?.invoke() }
    )
}

@Composable
fun MusicWaveAnimation(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val delay = index * 100
            val height by infiniteTransition.animateFloat(
                initialValue = 8f,
                targetValue = 20f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun ExplicitBadge() {
    Icon(
        painter = painterResource(R.drawable.ic_explicit),
        contentDescription = stringResource(R.string.label_explicit),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
private fun MusicTrack.musicMetadataLine(): String {
    val suffix = when {
        duration > 0 -> formatDuration(duration)
        views > 0 -> formatViews(views)
        else -> null
    }
    return if (suffix != null) stringResource(R.string.year_artist_template, artist, suffix) else artist
}

@Composable
fun TrendingTrackCard(
    track: MusicTrack,
    rank: Int,
    isDownloaded: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onArtistClick: (String) -> Unit,
    onMenuClick: () -> Unit
) {
    TrackListItem(
        track = track,
        isDownloaded = isDownloaded,
        leadingContent = {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center
            )
        },
        onClick = onClick,
        onLongClick = onLongClick,
        onMenuClick = onMenuClick
    )
}






