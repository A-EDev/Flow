package com.flow.youtube.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.flow.youtube.data.model.Comment
import com.flow.youtube.utils.formatLikeCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowCommentsBottomSheet(
    comments: List<Comment>,
    commentCount: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onTimestampClick: (String) -> Unit = {}, // Callback when user clicks "2:04"
    onFilterChanged: (Boolean) -> Unit = {},
    isTopSelected: Boolean = true,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. Header & Filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            ) {
                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Comments",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = commentCount,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Filter Chips
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = isTopSelected,
                        onClick = { onFilterChanged(true) },
                        label = { Text("Top") }
                    )
                    FilterChip(
                        selected = !isTopSelected,
                        onClick = { onFilterChanged(false) },
                        label = { Text("Newest") }
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            // 2. Content
            if (isLoading) {
                // Skeleton Loading Effect
                Column(Modifier.padding(16.dp)) {
                    repeat(6) { CommentSkeleton() }
                }
            } else if (comments.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No comments yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(comments) { comment ->
                        FlowCommentItem(
                            comment = comment,
                            onTimestampClick = onTimestampClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlowCommentItem(
    comment: Comment,
    onTimestampClick: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isOverflowing by remember { mutableStateOf(false) }

    // Process text to make timestamps blue and clickable
    val annotatedText = remember(comment.text) {
        processCommentText(comment.text)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Optional: Open thread */ }
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        // Avatar
        AsyncImage(
            model = comment.authorThumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            
            // Pinned Indicator (You'll need to map this field from your model)
            // if (comment.isPinned) { ... }
            
            // Header: Author + Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${comment.author.trim()}",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = comment.publishedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Comment Body with "Read More" logic
            Box(modifier = Modifier.animateContentSize()) {
                ClickableText(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    ),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textLayoutResult ->
                        if (textLayoutResult.hasVisualOverflow) {
                            isOverflowing = true
                        }
                    },
                    onClick = { offset ->
                        // Handle Timestamp Clicks
                        annotatedText.getStringAnnotations(tag = "TIMESTAMP", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                onTimestampClick(annotation.item)
                            }
                        // Handle URL Clicks
                        annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { 
                                // Handle Link opening here
                            }
                            
                        // Expand if clicked anywhere else and is overflowing
                        if (!isExpanded && isOverflowing) isExpanded = true
                    }
                )
            }

            if (isOverflowing && !isExpanded) {
                Text(
                    text = "Read more",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { isExpanded = true }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Bar (Like, Dislike, Reply)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Like
                Icon(
                    imageVector = Icons.Outlined.ThumbUp,
                    contentDescription = "Like",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (comment.likeCount > 0) {
                    Text(
                        text = formatLikeCount(comment.likeCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                // Dislike (Visual only usually)
                Icon(
                    imageVector = Icons.Outlined.ThumbDown,
                    contentDescription = "Dislike",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp)
                )
                
                Spacer(modifier = Modifier.width(24.dp))

                // Replies
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Reply",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp)
                )
            }

            // View Replies Button
            if (comment.replies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { /* Load replies */ }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp, 1.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${comment.replies.size} replies",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// HELPERS & SKELETONS
// ==========================================

/**
 * Parses comment text to highlight Timestamps (e.g. 2:04) and URLs.
 */
fun processCommentText(text: String): AnnotatedString {
    return buildAnnotatedString {
        val timestampRegex = Regex("(\\d{1,2}:\\d{2}(:\\d{2})?)") // Matches 1:30 or 01:20:30
        val urlRegex = Regex("(https?://\\S+)")
        
        var lastIndex = 0
        
        // Find timestamps first (Simple approach)
        timestampRegex.findAll(text).forEach { match ->
            append(text.substring(lastIndex, match.range.first))
            
            pushStringAnnotation(tag = "TIMESTAMP", annotation = match.value)
            withStyle(SpanStyle(color = Color(0xFF3EA6FF), fontWeight = FontWeight.SemiBold)) {
                append(match.value)
            }
            pop()
            lastIndex = match.range.last + 1
        }
        
        append(text.substring(lastIndex))
    }
}

@Composable
fun CommentSkeleton() {
    Row(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray.copy(0.2f)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(modifier = Modifier.width(100.dp).height(12.dp).background(Color.Gray.copy(0.2f), RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(Color.Gray.copy(0.2f), RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.width(200.dp).height(12.dp).background(Color.Gray.copy(0.2f), RoundedCornerShape(4.dp)))
        }
    }
}