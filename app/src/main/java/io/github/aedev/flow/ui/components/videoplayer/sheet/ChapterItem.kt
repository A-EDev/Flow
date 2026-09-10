package io.github.aedev.flow.ui.components.videoplayer.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.schabi.newpipe.extractor.stream.StreamSegment

@Composable
internal fun ChapterItem(
    chapter: StreamSegment,
    isCurrent: Boolean,
    progress: Float,
    durationLabel: String?,
    thumbnailUrl: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color =
            if (isCurrent) {
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.7f,
                )
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        border = if (isCurrent) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) else null,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val thumbnailWidth = (maxWidth * 0.42f).coerceIn(72.dp, 146.dp)
            val thumbnailHeight = thumbnailWidth * (82f / 146f)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChapterThumbnail(
                    thumbnailUrl = thumbnailUrl,
                    isCurrent = isCurrent,
                    progress = progress,
                    width = thumbnailWidth,
                    height = thumbnailHeight,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                ) {
                    if (isCurrent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatChapterTime(chapter.startTimeSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    } else {
                        Text(
                            text = formatChapterTime(chapter.startTimeSeconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (durationLabel != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = durationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterThumbnail(
    thumbnailUrl: String,
    isCurrent: Boolean,
    progress: Float,
    width: Dp,
    height: Dp,
) {
    val context = LocalContext.current

    Box(
        modifier =
            Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(14.dp)),
    ) {
        if (thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(context)
                        .data(thumbnailUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isCurrent) 0.16f else 0.26f)),
        )

        if (isCurrent) {
            LinearProgressIndicator(
                progress = { progress },
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.22f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

private fun formatChapterTime(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
