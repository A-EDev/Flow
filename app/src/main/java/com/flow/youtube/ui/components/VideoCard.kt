package com.flow.youtube.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.vectorResource
import com.flow.youtube.R
import coil.compose.AsyncImage
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.theme.extendedColors
import com.flow.youtube.utils.formatDuration
import com.flow.youtube.utils.formatViewCount

@Composable
fun VideoCard(
    video: Video,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .width(180.dp)
            .clip(RoundedCornerShape(16.dp)) // Clip the whole card for ripple
            .clickable(onClick = onClick)
            .padding(4.dp) // Subtle spacing between cards
    ) {
        // THUMBNAIL BOX
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)) // More modern rounded corners
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Glassmorphic Duration Badge
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Text(
                    text = formatDuration(video.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White, // Consistent white for badges
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // INFO ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Channel Avatar
            AsyncImage(
                model = video.channelThumbnailUrl?.takeIf { it.isNotEmpty() } ?: Icons.Default.AccountCircle,
                contentDescription = video.channelName,
                modifier = Modifier
                    .size(32.dp) // Slightly smaller for better balance
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, // Stronger weight for readability
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Metadata Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    
                    Text(
                        text = formatViewCount(video.viewCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            IconButton(
                onClick = { /* More actions */ },
                modifier = Modifier
                    .size(24.dp)
                    .offset(x = 4.dp, y = (-4).dp) // Adjust for better alignment
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun VideoCardHorizontal(
    video: Video,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatDuration(video.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Column {
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${formatViewCount(video.viewCount)} • ${video.uploadDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

/**
 * Full-width YouTube-style video card for infinite scroll
 */
/**
 * Full-width YouTube-style video card for infinite scroll
 */
@Composable
fun VideoCardFullWidth(
    video: Video,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    var showQuickActions by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Thumbnail with duration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            SafeAsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Duration badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatDuration(video.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Video info section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Channel avatar
            SafeAsyncImage(
                model = video.channelThumbnailUrl?.takeIf { it.isNotEmpty() } ?: Icons.Default.AccountCircle,
                contentDescription = video.channelName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentScale = ContentScale.Crop
            )

            // Video details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${video.channelName} • ${formatViewCount(video.viewCount)} • ${video.uploadDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // More options button
            IconButton(
                onClick = { showQuickActions = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
    
    // Quick actions bottom sheet
    if (showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = video,
            onDismiss = { showQuickActions = false }
        )
    }
}


@Composable
fun ShortsShelf(
    shorts: List<Video>,
    onShortClick: (Video) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_shorts),
                contentDescription = "Shorts",
                tint = Color.Red,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Shorts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shorts) { short ->
                ShortsCard(video = short, onClick = { onShortClick(short) })
            }
        }
    }
}

@Composable
fun ShortsCard(
    video: Video,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            SafeAsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = video.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "${formatViewCount(video.viewCount)} views",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.textSecondary
        )
    }
}

@Composable
private fun SafeAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    if (model is ImageVector) {
        Image(
            imageVector = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
        )
    } else {
        val isValidModel = when (model) {
            is String -> model.isNotEmpty()
            is Int -> true // Resource ID
            else -> false
        }

        if (isValidModel) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                onError = { 
                    // Fail silently or log
                }
            )
        } else {
            // Fallback placeholder
            Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

