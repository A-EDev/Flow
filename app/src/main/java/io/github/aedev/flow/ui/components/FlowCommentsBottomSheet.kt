package io.github.aedev.flow.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aedev.flow.R
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.utils.formatLikeCount
import io.github.aedev.flow.utils.formatRichText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.ripple
import kotlinx.coroutines.launch

enum class CommentSortFilter {
    TOP,
    NEWEST,
    OLDEST
}

private fun relativeTimeToSeconds(timeStr: String): Long {
    val lower = timeStr.lowercase().trim()
    val number = Regex("\\d+").find(lower)?.value?.toLongOrNull() ?: 0L
    return when {
        "second" in lower -> number
        "minute" in lower -> number * 60L
        "hour" in lower -> number * 3_600L
        "day" in lower -> number * 86_400L
        "week" in lower -> number * 604_800L
        "month" in lower -> number * 2_592_000L
        "year" in lower -> number * 31_536_000L
        else -> Long.MAX_VALUE
    }
}

/** Sorts comments for the given filter, keeping pinned comments first. */
fun sortCommentsByFilter(comments: List<Comment>, filter: CommentSortFilter): List<Comment> {
    val pinned = comments.filter { it.isPinned }
    val unpinned = comments.filterNot { it.isPinned }
    val sortedUnpinned = when (filter) {
        CommentSortFilter.TOP -> unpinned.sortedByDescending { it.likeCount }
        CommentSortFilter.NEWEST -> unpinned.sortedBy { relativeTimeToSeconds(it.publishedTime) }
        CommentSortFilter.OLDEST -> unpinned.sortedByDescending { relativeTimeToSeconds(it.publishedTime) }
    }
    return pinned + sortedUnpinned
}

/** Converts a "H:MM:SS" / "MM:SS" comment timestamp into milliseconds. */
fun commentTimestampToMs(timestamp: String): Long {
    val parts = timestamp.split(":").map { it.toLongOrNull() ?: 0L }
    val seconds = when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        2 -> parts[0] * 60 + parts[1]
        else -> 0L
    }
    return seconds * 1000L
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowCommentsBottomSheet(
    comments: List<Comment>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onTimestampClick: (String) -> Unit = {},
    onFilterChanged: (CommentSortFilter) -> Unit = {},
    onLoadReplies: (Comment) -> Unit = {},
    onLoadMoreReplies: (Comment) -> Unit = {},
    selectedFilter: CommentSortFilter = CommentSortFilter.TOP,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    hasMore: Boolean = false,
    onAuthorClick: (String) -> Unit = {},
    onAvatarClick: (String) -> Unit = {},
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
    onSheetProgressChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val sheetExpandedHeight = expandedHeight ?: (configuration.screenHeightDp.dp * 0.75f)
    val expandedHeightPx = with(density) { sheetExpandedHeight.toPx() }
    val collapsedHeightPx = with(density) { collapsedHeight.toPx() }.coerceIn(0f, expandedHeightPx)
    val sheetProgressRangePx = (expandedHeightPx - collapsedHeightPx).coerceAtLeast(1f)
    val dismissThresholdPx = collapsedHeightPx + sheetProgressRangePx * 0.55f
    val sheetHeightPx = remember { Animatable(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }
    val sheetProgress = if (expandedHeightPx > 0f) {
        ((sheetHeightPx.value - collapsedHeightPx) / sheetProgressRangePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    SideEffect {
        onSheetProgressChange(sheetProgress)
    }

    val commentsListState = rememberLazyListState()

    fun animateToExpanded() {
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = expandedHeightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    fun animateToDismiss() {
        if (isAnimatingOut) return
        isAnimatingOut = true
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = collapsedHeightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            latestOnDismiss()
        }
    }

    LaunchedEffect(expandedHeightPx, collapsedHeightPx) {
        isAnimatingOut = false
        sheetHeightPx.updateBounds(lowerBound = collapsedHeightPx, upperBound = expandedHeightPx)
        if (sheetHeightPx.value == 0f || sheetHeightPx.value < collapsedHeightPx) {
            sheetHeightPx.snapTo(collapsedHeightPx)
        }
        sheetHeightPx.animateTo(
            targetValue = expandedHeightPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    LaunchedEffect(selectedFilter) {
        commentsListState.scrollToItem(0)
    }

    BackHandler(onBack = ::animateToDismiss)

    val headerDragModifier = Modifier.pointerInput(expandedHeightPx, collapsedHeightPx, dismissThresholdPx, isAnimatingOut) {
        val velocityTracker = VelocityTracker()
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                if (isAnimatingOut) return@detectVerticalDragGestures
                velocityTracker.addPointerInputChange(change)
                coroutineScope.launch {
                    val nextValue = (sheetHeightPx.value - dragAmount).coerceIn(collapsedHeightPx, expandedHeightPx)
                    sheetHeightPx.snapTo(nextValue)
                }
            },
            onDragCancel = {
                velocityTracker.resetTracking()
                if (!isAnimatingOut) animateToExpanded()
            },
            onDragEnd = {
                val velocityY = velocityTracker.calculateVelocity().y
                velocityTracker.resetTracking()
                when {
                    velocityY > 1200f || sheetHeightPx.value < dismissThresholdPx -> animateToDismiss()
                    else -> animateToExpanded()
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { sheetHeightPx.value.toDp() }),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .then(headerDragModifier),
                    contentAlignment = Alignment.Center
                ) {
                    BottomSheetDefaults.DragHandle()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(headerDragModifier)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.comments),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = ::animateToDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                    CommentSortFilterChips(
                        selectedFilter = selectedFilter,
                        onFilterChanged = onFilterChanged,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                FlowCommentsList(
                    comments = comments,
                    isLoading = isLoading,
                    listState = commentsListState,
                    selectedFilter = selectedFilter,
                    onTimestampClick = onTimestampClick,
                    onLoadReplies = onLoadReplies,
                    onLoadMoreReplies = onLoadMoreReplies,
                    onAuthorClick = onAuthorClick,
                    onAvatarClick = onAvatarClick,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = onLoadMore,
                    hasMore = hasMore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
fun CommentSortFilterChips(
    selectedFilter: CommentSortFilter,
    onFilterChanged: (CommentSortFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == CommentSortFilter.TOP,
            onClick = { onFilterChanged(CommentSortFilter.TOP) },
            label = { Text(stringResource(R.string.filter_top)) }
        )
        FilterChip(
            selected = selectedFilter == CommentSortFilter.NEWEST,
            onClick = { onFilterChanged(CommentSortFilter.NEWEST) },
            label = { Text(stringResource(R.string.filter_newest)) }
        )
        FilterChip(
            selected = selectedFilter == CommentSortFilter.OLDEST,
            onClick = { onFilterChanged(CommentSortFilter.OLDEST) },
            label = { Text(stringResource(R.string.filter_oldest)) }
        )
    }
}

@Composable
fun FlowCommentsList(
    comments: List<Comment>,
    isLoading: Boolean,
    listState: LazyListState,
    selectedFilter: CommentSortFilter,
    onTimestampClick: (String) -> Unit,
    onLoadReplies: (Comment) -> Unit,
    onLoadMoreReplies: (Comment) -> Unit,
    onAuthorClick: (String) -> Unit,
    onAvatarClick: (String) -> Unit,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    hasMore: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 32.dp)
) {
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        if (isLoading) {
            item(key = "loading") {
                Column(Modifier.padding(16.dp)) {
                    repeat(6) { CommentSkeleton() }
                }
            }
        } else if (comments.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.no_comments_yet),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = comments,
                key = { comment -> "${selectedFilter.name}_${comment.id}" }
            ) { comment ->
                FlowCommentItem(
                    comment = comment,
                    onTimestampClick = onTimestampClick,
                    onLoadReplies = onLoadReplies,
                    onLoadMoreReplies = onLoadMoreReplies,
                    onAuthorClick = onAuthorClick,
                    onAvatarClick = onAvatarClick
                )
            }
            if (hasMore) {
                item(key = "load_more_trigger") {
                    LaunchedEffect(comments.size) {
                        latestOnLoadMore()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlowCommentItem(
    comment: Comment,
    onTimestampClick: (String) -> Unit,
    onLoadReplies: (Comment) -> Unit,
    onLoadMoreReplies: (Comment) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onAvatarClick: (String) -> Unit = {}
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
    val annotatedText = remember(comment.text, primaryColor) {
        formatRichText(
            text = comment.text,
            primaryColor = primaryColor,
            textColor = onSurface
        )
    }

    // Full-size image viewer
    if (showFullSizeImage) {
        FullSizeImageDialog(
            imageUrl = toHighQualityAvatarUrl(comment.authorThumbnail),
            onDismiss = { showFullSizeImage = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        val context = LocalContext.current
        // Avatar
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(comment.authorThumbnail)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    onAvatarClick(comment.authorThumbnail)
                    showFullSizeImage = true
                },
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            
            // Pinned indicator
            if (comment.isPinned) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.pinned_comment),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pinned_by_creator),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
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
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = {
                                onAuthorClick(
                                    comment.authorChannelId.ifBlank { getAuthorHandle(comment.author) }
                                )
                            }
                        )
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
                SelectionContainer {
                    BasicText(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        ),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            commentTextLayoutResult = result
                            if (result.hasVisualOverflow) isOverflowing = true
                        },
                        modifier = Modifier.pointerInput(annotatedText) {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    commentTextLayoutResult?.let { result ->
                                        val offset = result.getOffsetForPosition(tapOffset)
                                        val ts = annotatedText
                                            .getStringAnnotations("TIMESTAMP", offset, offset)
                                            .firstOrNull()
                                        val url = annotatedText
                                            .getStringAnnotations("URL", offset, offset)
                                            .firstOrNull()
                                        if (ts != null) {
                                            onTimestampClick(ts.item)
                                        } else if (url != null) {
                                            try { uriHandler.openUri(url.item) } catch (e: Exception) { e.printStackTrace() }
                                        } else {
                                            if (!isExpanded && isOverflowing) isExpanded = true
                                        }
                                    }
                                }
                            )
                        }
                    )
                }
            }

            if (isOverflowing && !isExpanded) {
                Text(
                    text = stringResource(R.string.read_more),
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
                    contentDescription = stringResource(R.string.like),
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
                    contentDescription = stringResource(R.string.dislikes),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp)
                )
                
                Spacer(modifier = Modifier.width(24.dp))

                // Replies
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = stringResource(R.string.reply),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp)
                )
            }

            // View Replies Button
            if (comment.replyCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { 
                            if (!isRepliesVisible && comment.replies.isEmpty()) {
                                isLoadingReplies = true
                                onLoadReplies(comment)
                            }
                            isRepliesVisible = !isRepliesVisible 
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp, 1.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isRepliesVisible) stringResource(R.string.hide_replies) else stringResource(R.string.view_replies_template, comment.replyCount),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isLoadingReplies) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Display Replies
            if (isRepliesVisible && comment.replies.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    comment.replies.forEach { reply ->
                        FlowReplyItem(
                            reply = reply,
                            onTimestampClick = onTimestampClick,
                            onAuthorClick = onAuthorClick,
                            onAvatarClick = onAvatarClick
                        )
                    }

                    if (comment.repliesPage != null || comment.continuationToken != null) {
                        Text(
                            text = stringResource(R.string.load_more_replies),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable {
                                    isLoadingReplies = true
                                    onLoadMoreReplies(comment)
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlowReplyItem(
    reply: Comment,
    onTimestampClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onAvatarClick: (String) -> Unit = {}
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val uriHandler = LocalUriHandler.current
    val annotatedText = remember(reply.text, primaryColor) {
        formatRichText(
            text = reply.text,
            primaryColor = primaryColor,
            textColor = onSurface
        )
    }
    var replyTextLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var showFullSizeImage by remember { mutableStateOf(false) }

    if (showFullSizeImage) {
        FullSizeImageDialog(
            imageUrl = toHighQualityAvatarUrl(reply.authorThumbnail),
            onDismiss = { showFullSizeImage = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        val context = LocalContext.current
        // Avatar (Small for replies)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(reply.authorThumbnail)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    onAvatarClick(reply.authorThumbnail)
                    showFullSizeImage = true
                },
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Header: Author + Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatAuthorName(reply.author),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = {
                                onAuthorClick(
                                    reply.authorChannelId.ifBlank { getAuthorHandle(reply.author) }
                                )
                            }
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = reply.publishedTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Reply Body
            SelectionContainer {
                BasicText(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp
                    ),
                    onTextLayout = { replyTextLayoutResult = it },
                    modifier = Modifier.pointerInput(annotatedText) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                replyTextLayoutResult?.let { result ->
                                    val offset = result.getOffsetForPosition(tapOffset)
                                    val ts = annotatedText
                                        .getStringAnnotations("TIMESTAMP", offset, offset)
                                        .firstOrNull()
                                    if (ts != null) {
                                        onTimestampClick(ts.item)
                                    } else {
                                        annotatedText
                                            .getStringAnnotations("URL", offset, offset)
                                            .firstOrNull()
                                            ?.let { try { uriHandler.openUri(it.item) } catch (_: Exception) {} }
                                    }
                                }
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Bar (Minimal for replies)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ThumbUp,
                    contentDescription = stringResource(R.string.like),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                if (reply.likeCount > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatLikeCount(reply.likeCount),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
            }
        }
    }
}
}

// ==========================================
// SKELETONS
// ==========================================

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

// HELPER COMPOSABLES & UTILITIES
@Composable
fun FullSizeImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(toHighQualityAvatarUrl(imageUrl))
                        .crossfade(true)
                        .size(1600, 1600)
                        .scale(coil.size.Scale.FIT)
                        .allowHardware(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.tap_to_close),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}


fun formatAuthorName(author: String): String {
    val trimmed = author.trim()
    return if (trimmed.startsWith("@")) {
        trimmed
    } else {
        "@$trimmed"
    }
}

fun getAuthorHandle(author: String): String {
    return author.trim().removePrefix("@")
}

private fun toHighQualityAvatarUrl(url: String): String {
    if (url.isBlank()) return url

    return url
        .replace(Regex("=s\\d+"), "=s1024")
        .replace(Regex("/s\\d+-"), "/s1024-")
        .replace(Regex("=w\\d+-h\\d+"), "=w1024-h1024")
}
