package io.github.aedev.flow.ui.components.shared

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.ui.components.ChannelAvatarImage
import io.github.aedev.flow.utils.formatLikeCount
import io.github.aedev.flow.utils.formatRichText

@Composable
fun FlowCommentItem(
    comment: Comment,
    onTimestampClick: (String) -> Unit,
    onLoadReplies: (Comment) -> Unit,
    onLoadMoreReplies: (Comment) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onAvatarClick: (String) -> Unit = {},
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isRepliesVisible by remember { mutableStateOf(false) }
    var isOverflowing by remember { mutableStateOf(false) }
    var isLoadingReplies by remember { mutableStateOf(false) }
    var commentTextLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var showFullSizeImage by remember { mutableStateOf(false) }

    val uriHandler = LocalUriHandler.current

    LaunchedEffect(comment.replies) {
        isLoadingReplies = false
    }

    // Process text — cached so it isn't rebuilt on every recomposition.
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val annotatedText =
        remember(comment.text, primaryColor) {
            formatRichText(
                text = comment.text,
                primaryColor = primaryColor,
                textColor = onSurface,
            )
        }

    // Full-size image viewer
    if (showFullSizeImage) {
        FullSizeImageDialog(
            imageUrl = toHighQualityAvatarUrl(comment.authorThumbnail),
            onDismiss = { showFullSizeImage = false },
        )
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
    ) {
        ChannelAvatarImage(
            url = comment.authorThumbnail,
            contentDescription = null,
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        onAvatarClick(comment.authorThumbnail)
                        showFullSizeImage = true
                    },
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Pinned indicator
            if (comment.isPinned) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.pinned_comment),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pinned_by_creator),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Header: Author + Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatAuthorName(comment.author),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = { onAuthorClick(commentAuthorChannelRef(comment)) },
                            ),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = localizedCommentPublishedTime(comment.publishedTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Comment Body with "Read More" logic
            Box(modifier = Modifier.animateContentSize()) {
                SelectionContainer {
                    BasicText(
                        text = annotatedText,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp,
                            ),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            commentTextLayoutResult = result
                            if (result.hasVisualOverflow) isOverflowing = true
                        },
                        modifier =
                            Modifier.pointerInput(annotatedText) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        commentTextLayoutResult?.let { result ->
                                            val offset = result.getOffsetForPosition(tapOffset)
                                            val ts =
                                                annotatedText
                                                    .getStringAnnotations("TIMESTAMP", offset, offset)
                                                    .firstOrNull()
                                            val url =
                                                annotatedText
                                                    .getStringAnnotations("URL", offset, offset)
                                                    .firstOrNull()
                                            if (ts != null) {
                                                onTimestampClick(ts.item)
                                            } else if (url != null) {
                                                try {
                                                    uriHandler.openUri(url.item)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            } else {
                                                if (!isExpanded && isOverflowing) isExpanded = true
                                            }
                                        }
                                    },
                                )
                            },
                    )
                }
            }

            if (isOverflowing && !isExpanded) {
                Text(
                    text = stringResource(R.string.read_more),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .padding(top = 4.dp)
                            .clickable { isExpanded = true },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Bar (Like, Dislike, Reply)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Like
                Icon(
                    imageVector = Icons.Outlined.ThumbUp,
                    contentDescription = stringResource(R.string.like),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (comment.likeCount > 0) {
                    Text(
                        text = formatLikeCount(comment.likeCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Dislike (Visual only usually)
                Icon(
                    imageVector = Icons.Outlined.ThumbDown,
                    contentDescription = stringResource(R.string.dislikes),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp),
                )

                Spacer(modifier = Modifier.width(24.dp))

                // Replies
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = stringResource(R.string.reply),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp),
                )
            }

            // View Replies Button
            if (comment.replyCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                if (!isRepliesVisible && comment.replies.isEmpty()) {
                                    isLoadingReplies = true
                                    onLoadReplies(comment)
                                }
                                isRepliesVisible = !isRepliesVisible
                            }.padding(vertical = 4.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp, 1.dp)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text =
                            if (isRepliesVisible) {
                                stringResource(
                                    R.string.hide_replies,
                                )
                            } else {
                                pluralStringResource(
                                    R.plurals.view_replies_template,
                                    comment.replyCount,
                                    comment.replyCount,
                                )
                            },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isLoadingReplies) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Display Replies
            if (isRepliesVisible && comment.replies.isNotEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                ) {
                    comment.replies.forEach { reply ->
                        FlowReplyItem(
                            reply = reply,
                            onTimestampClick = onTimestampClick,
                            onAuthorClick = onAuthorClick,
                            onAvatarClick = onAvatarClick,
                        )
                    }

                    if (comment.repliesPage != null || comment.continuationToken != null) {
                        Text(
                            text = stringResource(R.string.load_more_replies),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier =
                                Modifier
                                    .padding(top = 8.dp)
                                    .clickable {
                                        isLoadingReplies = true
                                        onLoadMoreReplies(comment)
                                    },
                        )
                    }
                }
            }
        }
    }
}
