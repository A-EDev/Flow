package com.flow.youtube.ui.components

import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.theme.extendedColors
import com.flow.youtube.utils.formatSubscriberCount
import com.flow.youtube.utils.formatViewCount

@Composable
fun VideoInfoSection(
    video: Video,
    viewCount: Long,
    uploadDate: String?,
    description: String?,
    channelName: String,
    channelAvatarUrl: String,
    subscriberCount: Long?,
    isSubscribed: Boolean,
    likeState: String, // "LIKED", "DISLIKED", "NONE"
    onSubscribeClick: () -> Unit,
    onChannelClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        // ============ TITLE SECTION (Directly Below Player) ============
        // Title - Now prominently displayed first
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 24.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        // View count and date in a subtle row below title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatViewCount(viewCount)} views",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (!uploadDate.isNullOrBlank()) {
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = uploadDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ============ CHANNEL SECTION ============
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onChannelClick)
            ) {
                AsyncImage(
                    model = channelAvatarUrl.ifEmpty { video.channelThumbnailUrl },
                    contentDescription = "Channel Avatar",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channelName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    val subText = subscriberCount?.let { formatSubscriberCount(it) } ?: ""
                    if (subText.isNotEmpty()) {
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Subscribe Button (Pill Shape)
            SubscribeButton(
                isSubscribed = isSubscribed,
                onClick = onSubscribeClick
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ============ ACTION ROW ============
        VideoActionRow(
            likeState = likeState,
            onLikeClick = onLikeClick,
            onDislikeClick = onDislikeClick,
            onShareClick = onShareClick,
            onDownloadClick = onDownloadClick,
            onSaveClick = onSaveClick
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ============ DESCRIPTION BOX ============
        EnhancedDescriptionBox(
            description = description ?: video.description
        )
    }
}

@Composable
fun SubscribeButton(
    isSubscribed: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSubscribed) 
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    else 
        MaterialTheme.colorScheme.onBackground
        
    val contentColor = if (isSubscribed) 
        MaterialTheme.colorScheme.onSurface 
    else 
        MaterialTheme.colorScheme.surface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor,
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            if (isSubscribed) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Subscribed",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = contentColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            } else {
                Text(
                    text = "Subscribe",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun VideoActionRow(
    likeState: String,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            SegmentedLikeDislikeButton(
                likeState = likeState,
                onLikeClick = onLikeClick,
                onDislikeClick = onDislikeClick
            )
        }
        
        item {
            ActionChip(
                icon = Icons.Outlined.Share,
                label = "Share",
                onClick = onShareClick
            )
        }
        
        item {
            ActionChip(
                icon = Icons.Outlined.Download,
                label = "Download",
                onClick = onDownloadClick
            )
        }
        
        item {
            ActionChip(
                icon = Icons.Outlined.BookmarkBorder,
                label = "Save",
                onClick = onSaveClick
            )
        }
        
        item {
            ActionChip(
                icon = Icons.Outlined.Flag,
                label = "Report",
                onClick = { /* Report */ }
            )
        }
    }
}

@Composable
fun SegmentedLikeDislikeButton(
    likeState: String,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.height(36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Like Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onLikeClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (likeState == "LIKED") Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = "Like",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (likeState == "LIKED") "Liked" else "Like",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
            
            // Dislike Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clickable(onClick = onDislikeClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (likeState == "DISLIKED") Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = "Dislike",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// Legacy description box for compatibility
@Composable
fun VideoDescriptionBox(
    viewCount: Long,
    uploadDate: String?,
    description: String?
) {
    var expanded by remember { mutableStateOf(false) }
    
    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatViewCount(viewCount)} views",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uploadDate ?: "",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (!expanded) {
                    Text(
                        text = "more",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = "Show less",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Enhanced description box with proper rendering of:
 * - Line breaks
 * - Clickable links
 * - Timestamps (0:00 format)
 * - Hashtags
 * - Chapter markers
 */
@Composable
fun EnhancedDescriptionBox(
    description: String?,
    onTimestampClick: ((Long) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = secondaryTextColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = secondaryTextColor
                )
            }
            
            if (!description.isNullOrBlank()) {
                // Format and render the description
                val formattedText = formatDescription(description, primaryColor, textColor)
                
                Text(
                    text = formattedText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp
                    ),
                    color = textColor,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show more/less button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = if (expanded) "Show less" else "Show more",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = primaryColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = primaryColor
                    )
                }
            } else {
                Text(
                    text = "No description available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
            }
        }
    }
}

/**
 * Formats the description text with proper styling for links, timestamps, and hashtags
 */
@Composable
private fun formatDescription(
    description: String,
    primaryColor: Color,
    textColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val text = description.trim()
        var currentIndex = 0
        
        // Regex patterns
        val urlPattern = Regex("""(https?://[^\s]+)""")
        val timestampPattern = Regex("""(\d{1,2}:)?\d{1,2}:\d{2}""")
        val hashtagPattern = Regex("""#\w+""")
        
        // Find all matches and sort by position
        data class Match(val start: Int, val end: Int, val type: String, val text: String)
        val matches = mutableListOf<Match>()
        
        urlPattern.findAll(text).forEach { 
            matches.add(Match(it.range.first, it.range.last + 1, "url", it.value))
        }
        timestampPattern.findAll(text).forEach { 
            matches.add(Match(it.range.first, it.range.last + 1, "timestamp", it.value))
        }
        hashtagPattern.findAll(text).forEach { 
            matches.add(Match(it.range.first, it.range.last + 1, "hashtag", it.value))
        }
        
        matches.sortBy { it.start }
        
        // Build the annotated string
        for (match in matches) {
            // Add text before match
            if (match.start > currentIndex) {
                append(text.substring(currentIndex, match.start))
            }
            
            // Skip if we've already passed this point (overlapping matches)
            if (match.start < currentIndex) continue
            
            // Add styled match
            when (match.type) {
                "url" -> {
                    pushStringAnnotation(tag = "URL", annotation = match.text)
                    withStyle(SpanStyle(
                        color = primaryColor,
                        textDecoration = TextDecoration.Underline
                    )) {
                        // Truncate long URLs for display
                        val displayUrl = if (match.text.length > 50) {
                            match.text.take(47) + "..."
                        } else {
                            match.text
                        }
                        append(displayUrl)
                    }
                    pop()
                }
                "timestamp" -> {
                    pushStringAnnotation(tag = "TIMESTAMP", annotation = match.text)
                    withStyle(SpanStyle(
                        color = primaryColor,
                        fontWeight = FontWeight.Medium
                    )) {
                        append(match.text)
                    }
                    pop()
                }
                "hashtag" -> {
                    pushStringAnnotation(tag = "HASHTAG", annotation = match.text)
                    withStyle(SpanStyle(
                        color = primaryColor,
                        fontWeight = FontWeight.Medium
                    )) {
                        append(match.text)
                    }
                    pop()
                }
            }
            
            currentIndex = match.end
        }
        
        // Add remaining text
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}
