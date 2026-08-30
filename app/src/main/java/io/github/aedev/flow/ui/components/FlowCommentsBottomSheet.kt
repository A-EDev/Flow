package io.github.aedev.flow.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.distinctByNonBlankKey
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import io.github.aedev.flow.utils.formatLikeCount
import io.github.aedev.flow.utils.formatRichText
import io.github.aedev.flow.utils.formatTimeAgo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val COMMENTS_DISMISS_FRACTION = 0.55f
private const val COMMENTS_DISMISS_VELOCITY = 1_200f

enum class CommentSortFilter {
    TOP,
    NEWEST,
    OLDEST,
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
fun sortCommentsByFilter(
    comments: List<Comment>,
    filter: CommentSortFilter,
): List<Comment> {
    val pinned = comments.filter { it.isPinned }
    val unpinned = comments.filterNot { it.isPinned }
    val sortedUnpinned =
        when (filter) {
            CommentSortFilter.TOP -> unpinned.sortedByDescending { it.likeCount }
            CommentSortFilter.NEWEST -> unpinned.sortedBy { relativeTimeToSeconds(it.publishedTime) }
            CommentSortFilter.OLDEST -> unpinned.sortedByDescending { relativeTimeToSeconds(it.publishedTime) }
        }
    return pinned + sortedUnpinned
}

/** Converts a valid `H:MM:SS` or `MM:SS` comment timestamp into milliseconds. */
fun commentTimestampToMs(timestamp: String): Long {
    val rawParts = timestamp.trim().split(":")
    if (rawParts.size !in 2..3) return 0L
    val parts = rawParts.map { it.toLongOrNull() ?: return 0L }
    if (parts.any { it < 0L }) return 0L
    if (parts.size == 3 && (parts[1] >= 60L || parts[2] >= 60L)) return 0L
    if (parts.size == 2 && parts[1] >= 60L) return 0L

    val seconds =
        when (parts.size) {
            3 -> parts[0] * 3_600L + parts[1] * 60L + parts[2]
            2 -> parts[0] * 60L + parts[1]
            else -> return 0L
        }
    return seconds * 1_000L
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
    dismissOnOutsideTap: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val reduceMotion = rememberFlowReduceMotion()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestOnSheetProgressChange by rememberUpdatedState(onSheetProgressChange)
    val sheetExpandedHeight = expandedHeight ?: (configuration.screenHeightDp.dp * 0.75f)
    val expandedHeightPx = with(density) { sheetExpandedHeight.toPx() }
    val collapsedHeightPx = with(density) { collapsedHeight.toPx() }.coerceIn(0f, expandedHeightPx)
    val sheetProgressRangePx = (expandedHeightPx - collapsedHeightPx).coerceAtLeast(1f)
    val dismissThresholdPx = collapsedHeightPx + sheetProgressRangePx * COMMENTS_DISMISS_FRACTION
    val sheetHeightPx = remember { Animatable(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }
    val commentsListState = rememberLazyListState()

    LaunchedEffect(sheetHeightPx, collapsedHeightPx, sheetProgressRangePx) {
        snapshotFlow {
            ((sheetHeightPx.value - collapsedHeightPx) / sheetProgressRangePx).coerceIn(0f, 1f)
        }.distinctUntilChanged()
            .collect { progress -> latestOnSheetProgressChange(progress) }
    }

    fun animateToExpanded() {
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = expandedHeightPx,
                animationSpec =
                    tween(
                        durationMillis = FlowMotion.durationFor(FlowMotion.EMPHASIZED_DURATION_MILLIS, reduceMotion),
                        easing = FlowMotion.EnterEasing,
                    ),
            )
        }
    }

    fun animateToDismiss() {
        if (isAnimatingOut) return
        isAnimatingOut = true
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = collapsedHeightPx,
                animationSpec =
                    tween(
                        durationMillis = FlowMotion.durationFor(FlowMotion.EXIT_DURATION_MILLIS, reduceMotion),
                        easing = FlowMotion.ExitEasing,
                    ),
            )
            latestOnDismiss()
        }
    }

    LaunchedEffect(expandedHeightPx, collapsedHeightPx, reduceMotion) {
        isAnimatingOut = false
        sheetHeightPx.updateBounds(lowerBound = collapsedHeightPx, upperBound = expandedHeightPx)
        if (sheetHeightPx.value == 0f || sheetHeightPx.value < collapsedHeightPx) {
            sheetHeightPx.snapTo(collapsedHeightPx)
        }
        sheetHeightPx.animateTo(
            targetValue = expandedHeightPx,
            animationSpec =
                tween(
                    durationMillis = FlowMotion.durationFor(FlowMotion.EMPHASIZED_DURATION_MILLIS, reduceMotion),
                    easing = FlowMotion.EnterEasing,
                ),
        )
    }

    LaunchedEffect(selectedFilter, reduceMotion) {
        if (reduceMotion) {
            commentsListState.scrollToItem(0)
        } else {
            commentsListState.animateScrollToItem(0)
        }
    }

    BackHandler(onBack = ::animateToDismiss)

    val headerDragModifier =
        Modifier.pointerInput(expandedHeightPx, collapsedHeightPx, dismissThresholdPx, isAnimatingOut) {
            val velocityTracker = VelocityTracker()
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    if (isAnimatingOut) return@detectVerticalDragGestures
                    velocityTracker.addPointerInputChange(change)
                    coroutineScope.launch {
                        val nextValue =
                            (sheetHeightPx.value - dragAmount).coerceIn(collapsedHeightPx, expandedHeightPx)
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
                        velocityY > COMMENTS_DISMISS_VELOCITY || sheetHeightPx.value < dismissThresholdPx -> {
                            animateToDismiss()
                        }
                        else -> animateToExpanded()
                    }
                },
            )
        }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (dismissOnOutsideTap) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(isAnimatingOut) {
                            detectTapGestures { animateToDismiss() }
                        },
            )
        }
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val height =
                            sheetHeightPx.value
                                .roundToInt()
                                .coerceIn(constraints.minHeight, constraints.maxHeight)
                        val placeable =
                            measurable.measure(
                                constraints.copy(
                                    minHeight = height,
                                    maxHeight = height,
                                ),
                            )
                        layout(placeable.width, height) {
                            placeable.placeRelative(0, 0)
                        }
                    },
            shape = BottomSheetDefaults.ExpandedShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .then(headerDragModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomSheetDefaults.DragHandle()
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(headerDragModifier)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.comments),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = ::animateToDismiss,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                    CommentSortFilterChips(
                        selectedFilter = selectedFilter,
                        onFilterChanged = onFilterChanged,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }
        }
    }
}

@Composable
fun CommentSortFilterChips(
    selectedFilter: CommentSortFilter,
    onFilterChanged: (CommentSortFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CommentSortFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChanged(filter) },
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                CommentSortFilter.TOP -> R.string.filter_top
                                CommentSortFilter.NEWEST -> R.string.filter_newest
                                CommentSortFilter.OLDEST -> R.string.filter_oldest
                            },
                        ),
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
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
    contentPadding: PaddingValues = PaddingValues(bottom = 32.dp),
) {
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    val uniqueComments = remember(comments) { comments.distinctByNonBlankKey(Comment::id) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        if (isLoading) {
            item(key = "loading") {
                Column(Modifier.padding(16.dp)) {
                    repeat(6) { CommentSkeleton() }
                }
            }
        } else if (uniqueComments.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier =
                        Modifier
                            .fillParentMaxWidth()
                            .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.no_comments_yet),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            itemsIndexed(
                items = uniqueComments,
                key = { index, comment ->
                    "${selectedFilter.name}_${comment.id.ifBlank { "comment_$index" }}"
                },
            ) { _, comment ->
                FlowCommentItem(
                    comment = comment,
                    onTimestampClick = onTimestampClick,
                    onLoadReplies = onLoadReplies,
                    onLoadMoreReplies = onLoadMoreReplies,
                    onAuthorClick = onAuthorClick,
                    onAvatarClick = onAvatarClick,
                )
            }
            if (hasMore) {
                item(key = "load_more_trigger") {
                    LaunchedEffect(comments.size) {
                        latestOnLoadMore()
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
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
    onAvatarClick: (String) -> Unit = {},
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isRepliesVisible by remember { mutableStateOf(false) }
    var isOverflowing by remember { mutableStateOf(false) }
    var isLoadingReplies by remember { mutableStateOf(false) }
    var commentTextLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var showFullSizeImage by remember { mutableStateOf(false) }

    val uriHandler = LocalUriHandler.current
    val reduceMotion = rememberFlowReduceMotion()

    LaunchedEffect(comment.replies) {
        isLoadingReplies = false
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val annotatedText =
        remember(comment.text, primaryColor, onSurface) {
            formatRichText(
                text = comment.text,
                primaryColor = primaryColor,
                textColor = onSurface,
            )
        }

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
        Surface(
            onClick = {
                onAvatarClick(comment.authorThumbnail)
                showFullSizeImage = true
            },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ChannelAvatarImage(
                    url = comment.authorThumbnail,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

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
                        text = stringResource(R.string.pinned_by_creator),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatAuthorName(comment.author),
                    style = MaterialTheme.typography.labelMedium,
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

            Box(
                modifier =
                    Modifier.animateContentSize(
                        animationSpec =
                            tween(
                                durationMillis = FlowMotion.durationFor(FlowMotion.CONTENT_DURATION_MILLIS, reduceMotion),
                                easing = FlowMotion.EnterEasing,
                            ),
                    ),
            ) {
                SelectionContainer {
                    BasicText(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
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
                                            val timestamp =
                                                annotatedText
                                                    .getStringAnnotations("TIMESTAMP", offset, offset)
                                                    .firstOrNull()
                                            val url =
                                                annotatedText
                                                    .getStringAnnotations("URL", offset, offset)
                                                    .firstOrNull()
                                            if (timestamp != null) {
                                                onTimestampClick(timestamp.item)
                                            } else if (url != null) {
                                                runCatching { uriHandler.openUri(url.item) }
                                            } else if (!isExpanded && isOverflowing) {
                                                isExpanded = true
                                            }
                                        }
                                    },
                                )
                            },
                    )
                }
            }

            if (isOverflowing && !isExpanded) {
                TextButton(onClick = { isExpanded = true }) {
                    Text(
                        text = stringResource(R.string.read_more),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ThumbUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                if (comment.likeCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatLikeCount(comment.likeCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(24.dp))
                Icon(
                    imageVector = Icons.Outlined.ThumbDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(24.dp))
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }

            if (comment.replyCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        if (!isRepliesVisible && comment.replies.isEmpty()) {
                            isLoadingReplies = true
                            onLoadReplies(comment)
                        }
                        isRepliesVisible = !isRepliesVisible
                    },
                ) {
                    Text(
                        text =
                            if (isRepliesVisible) {
                                stringResource(R.string.hide_replies)
                            } else {
                                pluralStringResource(
                                    R.plurals.view_replies_template,
                                    comment.replyCount,
                                    comment.replyCount,
                                )
                            },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isLoadingReplies) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

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
                        TextButton(
                            onClick = {
                                isLoadingReplies = true
                                onLoadMoreReplies(comment)
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.load_more_replies),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
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
    onAvatarClick: (String) -> Unit = {},
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val uriHandler = LocalUriHandler.current
    val annotatedText =
        remember(reply.text, primaryColor, onSurface) {
            formatRichText(
                text = reply.text,
                primaryColor = primaryColor,
                textColor = onSurface,
            )
        }
    var replyTextLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var showFullSizeImage by remember { mutableStateOf(false) }

    if (showFullSizeImage) {
        FullSizeImageDialog(
            imageUrl = toHighQualityAvatarUrl(reply.authorThumbnail),
            onDismiss = { showFullSizeImage = false },
        )
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        Surface(
            onClick = {
                onAvatarClick(reply.authorThumbnail)
                showFullSizeImage = true
            },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ChannelAvatarImage(
                    url = reply.authorThumbnail,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatAuthorName(reply.author),
                    style = MaterialTheme.typography.labelSmall,
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
                                onClick = { onAuthorClick(commentAuthorChannelRef(reply)) },
                            ),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = localizedCommentPublishedTime(reply.publishedTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            SelectionContainer {
                BasicText(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    onTextLayout = { replyTextLayoutResult = it },
                    modifier =
                        Modifier.pointerInput(annotatedText) {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    replyTextLayoutResult?.let { result ->
                                        val offset = result.getOffsetForPosition(tapOffset)
                                        val timestamp =
                                            annotatedText
                                                .getStringAnnotations("TIMESTAMP", offset, offset)
                                                .firstOrNull()
                                        if (timestamp != null) {
                                            onTimestampClick(timestamp.item)
                                        } else {
                                            annotatedText
                                                .getStringAnnotations("URL", offset, offset)
                                                .firstOrNull()
                                                ?.let { annotation ->
                                                    runCatching { uriHandler.openUri(annotation.item) }
                                                }
                                        }
                                    }
                                },
                            )
                        },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ThumbUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                if (reply.likeCount > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatLikeCount(reply.likeCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun localizedCommentPublishedTime(publishedTime: String): String {
    val editedSuffix = Regex("\\s*\\(?edited\\)?\\s*$", RegexOption.IGNORE_CASE)
    val isEdited = editedSuffix.containsMatchIn(publishedTime)
    val time = formatTimeAgo(publishedTime.replace(editedSuffix, "").trim())
    return if (isEdited) {
        stringResource(R.string.comment_time_edited_template, time, stringResource(R.string.comment_edited))
    } else {
        time
    }
}

@Composable
fun CommentSkeleton() {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Box(modifier = Modifier.size(40.dp).background(placeholderColor, CircleShape))
        Spacer(modifier = Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(
                modifier =
                    Modifier
                        .width(100.dp)
                        .height(12.dp)
                        .background(placeholderColor, MaterialTheme.shapes.extraSmall),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(placeholderColor, MaterialTheme.shapes.extraSmall),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier =
                    Modifier
                        .width(200.dp)
                        .height(12.dp)
                        .background(placeholderColor, MaterialTheme.shapes.extraSmall),
            )
        }
    }
}

@Composable
fun FullSizeImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val reduceMotion = rememberFlowReduceMotion()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            onClick = onDismiss,
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
            ) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(toHighQualityAvatarUrl(imageUrl))
                            .crossfade(!reduceMotion)
                            .size(1600, 1600)
                            .scale(coil3.size.Scale.FIT)
                            .allowHardware(false)
                            .build(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.extraLarge),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.tap_to_close),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

fun formatAuthorName(author: String): String {
    val trimmed = author.trim()
    return if (trimmed.startsWith("@")) trimmed else "@$trimmed"
}

/**
 * Returns the channel reference in the form accepted by the channel navigator.
 * Keeping a real `UC…` id intact avoids turning it into an invalid handle.
 */
fun commentAuthorChannelRef(comment: Comment): String =
    comment.authorChannelId.trim().ifBlank { comment.author.trim().removePrefix("@") }

private fun toHighQualityAvatarUrl(url: String): String {
    if (url.isBlank()) return url

    return url
        .replace(Regex("=s\\d+"), "=s1024")
        .replace(Regex("/s\\d+-"), "/s1024-")
        .replace(Regex("=w\\d+-h\\d+"), "=w1024-h1024")
}
