package io.github.aedev.flow.ui.components.shared

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.ui.components.ChannelAvatarImage
import io.github.aedev.flow.utils.formatLikeCount

private val ThreadHorizontalPadding = 16.dp
private val ThreadLineWidth = 2.dp

/** Width of the elbow column: the turn, plus the gap before the reply's avatar. */
private val ThreadConnectorWidth = 20.dp
private val ThreadElbowRadius = 8.dp

/** Where the line turns: the vertical centre of a reply's 24 dp avatar under its 8 dp top padding. */
private val ThreadElbowY = 20.dp

/** A reply to a reply starts its own branch just inside its parent's text. */
private val NestedThreadStart = 12.dp

/** Centre of the parent avatar: the row's start padding plus half the 40 dp avatar, less the line. */
private val ThreadLineStart = 35.dp
private val ThreadLineGap = 15.dp

@Composable
fun FlowCommentItem(
    comment: Comment,
    onSeekMs: (Long) -> Unit,
    onLoadReplies: (Comment) -> Unit,
    onLoadMoreReplies: (Comment) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onAvatarClick: (String) -> Unit = {},
    tint: MediaArtworkTint? = null,
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

    val accent = tint?.accent ?: MaterialTheme.colorScheme.primary
    val chipColor = tint?.container ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val commentText = rememberCommentText(comment, accent)

    if (showFullSizeImage) {
        FullSizeImageDialog(
            imageUrl = toHighQualityAvatarUrl(comment.authorThumbnail),
            onDismiss = { showFullSizeImage = false },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ThreadHorizontalPadding)
                    .padding(top = 12.dp, bottom = 4.dp),
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
                            text = comment.pinnedByText?.takeIf { it.isNotBlank() } ?: stringResource(R.string.pinned_by_creator),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                CommentAuthorRow(
                    comment = comment,
                    onAuthorClick = onAuthorClick,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(modifier = Modifier.animateContentSize()) {
                    SelectionContainer {
                        BasicText(
                            text = commentText.annotated,
                            inlineContent = commentText.inlineContent,
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
                                Modifier
                                    .richTextHighlights(
                                        text = commentText.annotated,
                                        layoutResult = { commentTextLayoutResult },
                                        color = chipColor,
                                    ).pointerInput(commentText.annotated) {
                                        detectTapGestures(
                                            onTap = { tapOffset ->
                                                val result = commentTextLayoutResult ?: return@detectTapGestures
                                                val offset = result.getOffsetForPosition(tapOffset)
                                                val handled =
                                                    commentText.handleTap(
                                                        offset = offset,
                                                        onSeekMs = onSeekMs,
                                                        onOpenUrl = { url ->
                                                            runCatching { uriHandler.openUri(url) }
                                                        },
                                                        onAuthorClick = onAuthorClick,
                                                    )
                                                if (!handled && !isExpanded && isOverflowing) isExpanded = true
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

                CommentEngagementRow(comment = comment)

                if (comment.replyCount > 0) {
                    RepliesToggle(
                        replyCount = comment.replyCount,
                        expanded = isRepliesVisible,
                        loading = isLoadingReplies,
                        onClick = {
                            if (!isRepliesVisible && comment.replies.isEmpty()) {
                                isLoadingReplies = true
                                onLoadReplies(comment)
                            }
                            isRepliesVisible = !isRepliesVisible
                        },
                    )
                }
            }
        }

        if (isRepliesVisible && comment.replies.isNotEmpty()) {
            val tree = remember(comment.replies) { buildCommentReplyTree(comment.replies) }
            ReplyThread(
                nodes = tree,
                hasMore = comment.repliesPage != null || comment.continuationToken != null,
                startPadding = ThreadLineStart,
                tint = tint,
                onSeekMs = onSeekMs,
                onAuthorClick = onAuthorClick,
                onAvatarClick = onAvatarClick,
                onLoadMore = {
                    isLoadingReplies = true
                    onLoadMoreReplies(comment)
                },
            )
        }
    }
}

/**
 * The replies under one comment, and the replies to those.
 *
 * Each reply hangs off the thread line on an elbow, the way a conversation branches: the line runs
 * down from the parent's avatar, turns into each reply, and carries on past it to the next one. A
 * reply that answered another reply sits one branch deeper, which is the only thing that tells the
 * two apart — YouTube stores a single flat level and marks the nesting with an `@handle` alone.
 */
@Composable
private fun ReplyThread(
    nodes: List<CommentReplyNode>,
    hasMore: Boolean,
    startPadding: Dp,
    tint: MediaArtworkTint?,
    onSeekMs: (Long) -> Unit,
    onAuthorClick: (String) -> Unit,
    onAvatarClick: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = startPadding, end = ThreadHorizontalPadding, bottom = 4.dp),
    ) {
        nodes.forEachIndexed { index, node ->
            val continues = index != nodes.lastIndex || hasMore
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                ThreadConnector(
                    color = lineColor,
                    continuesBelow = continues,
                    modifier = Modifier.fillMaxHeight(),
                )
                Column(modifier = Modifier.weight(1f)) {
                    FlowReplyItem(
                        reply = node.comment,
                        onSeekMs = onSeekMs,
                        onAuthorClick = onAuthorClick,
                        onAvatarClick = onAvatarClick,
                        tint = tint,
                    )
                    if (node.children.isNotEmpty()) {
                        ReplyThread(
                            nodes = node.children,
                            hasMore = false,
                            startPadding = NestedThreadStart,
                            tint = tint,
                            onSeekMs = onSeekMs,
                            onAuthorClick = onAuthorClick,
                            onAvatarClick = onAvatarClick,
                            onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }

        if (hasMore) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                ThreadConnector(
                    color = lineColor,
                    continuesBelow = false,
                    modifier = Modifier.fillMaxHeight(),
                )
                Text(
                    text = stringResource(R.string.load_more_replies),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .padding(start = ThreadLineGap)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onLoadMore)
                            .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * The line into one reply: down from above, a quarter turn towards the text, and on down to the
 * next reply when there is one.
 */
@Composable
private fun ThreadConnector(
    color: Color,
    continuesBelow: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.width(ThreadConnectorWidth)) {
        val stroke = ThreadLineWidth.toPx()
        val elbowY = ThreadElbowY.toPx()
        val radius = ThreadElbowRadius.toPx()
        val x = stroke / 2f

        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, (elbowY - radius).coerceAtLeast(0f)),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = -90f,
            useCenter = false,
            topLeft = Offset(x, elbowY - 2 * radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = color,
            start = Offset(x + radius, elbowY),
            end = Offset(size.width, elbowY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        if (continuesBelow) {
            drawLine(
                color = color,
                start = Offset(x, (elbowY - radius).coerceAtLeast(0f)),
                end = Offset(x, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** "12 replies" with the chevron that says it opens, in place of the rule that used to sit here. */
@Composable
private fun RepliesToggle(
    replyCount: Int,
    expanded: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(top = 4.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text =
                if (expanded) {
                    stringResource(R.string.hide_replies)
                } else {
                    pluralStringResource(R.plurals.view_replies_template, replyCount, replyCount)
                },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        if (loading) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun CommentAuthorRow(
    comment: Comment,
    onAuthorClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CommentAuthorName(
            comment = comment,
            onAuthorClick = onAuthorClick,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (comment.isVerified || comment.isArtist) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.Verified,
                contentDescription = stringResource(R.string.verified),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = localizedCommentPublishedTime(comment.publishedTime),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CommentAuthorName(
    comment: Comment,
    onAuthorClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = formatAuthorName(comment.author)
    val clickable =
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = { onAuthorClick(commentAuthorChannelRef(comment)) },
        )
    if (comment.isCreator) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = CircleShape,
            modifier = clickable,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        return
    }
    Text(
        text = name,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = clickable,
    )
}

/**
 * The like count, and the heart the creator left on this comment.
 *
 * The thumbs-down and reply icons that used to sit here were decoration: neither was clickable,
 * and neither has an action this app can perform without a signed-in session.
 */
@Composable
internal fun CommentEngagementRow(
    comment: Comment,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.ThumbUp,
            contentDescription = stringResource(R.string.like),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        val likeText = comment.likeCountText.takeIf { it.isNotBlank() } ?: comment.likeCount.takeIf { it > 0 }?.let(::formatLikeCount)
        if (likeText != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = likeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (comment.isHearted) {
            Spacer(modifier = Modifier.width(14.dp))
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = comment.heartedByText ?: stringResource(R.string.comment_hearted_by_creator),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
