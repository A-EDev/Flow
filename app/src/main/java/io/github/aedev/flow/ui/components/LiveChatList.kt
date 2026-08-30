package io.github.aedev.flow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.LiveChatMessage
import io.github.aedev.flow.data.model.LiveChatMessageType
import io.github.aedev.flow.data.model.LiveChatSegment
import io.github.aedev.flow.data.model.distinctByNonBlankKey
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun LiveChatList(
    messages: List<LiveChatMessage>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
) {
    val uniqueMessages =
        remember(messages) {
            messages.distinctByNonBlankKey(LiveChatMessage::id)
        }
    val reduceMotion = rememberFlowReduceMotion()

    when {
        isLoading && uniqueMessages.isEmpty() -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.live_chat_connecting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        uniqueMessages.isEmpty() -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.live_chat_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        else -> {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            var followLive by remember { mutableStateOf(true) }
            val isAtLiveEdge by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                    layoutInfo.totalItemsCount == 0 ||
                        (lastVisibleItem != null && lastVisibleItem.index >= layoutInfo.totalItemsCount - 1)
                }
            }

            LaunchedEffect(listState) {
                snapshotFlow { isAtLiveEdge to listState.isScrollInProgress }
                    .distinctUntilChanged()
                    .collect { (atLiveEdge, isScrolling) ->
                        if (atLiveEdge) {
                            followLive = true
                        } else if (isScrolling) {
                            followLive = false
                        }
                    }
            }

            LaunchedEffect(uniqueMessages.lastOrNull()?.id, uniqueMessages.size, followLive) {
                if (followLive && uniqueMessages.isNotEmpty()) {
                    listState.scrollToItem(uniqueMessages.lastIndex)
                }
            }

            Box(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding,
                ) {
                    itemsIndexed(
                        items = uniqueMessages,
                        key = { index, message ->
                            message.id.takeIf { it.isNotBlank() }
                                ?: "chat-${message.timestamp.orEmpty()}-$index"
                        },
                    ) { _, message ->
                        when (message.type) {
                            LiveChatMessageType.SUPER_CHAT -> SuperChatRow(message)
                            LiveChatMessageType.MEMBERSHIP -> MembershipRow(message)
                            else -> ChatTextRow(message)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !isAtLiveEdge,
                    modifier = Modifier.align(Alignment.BottomEnd),
                    enter =
                        fadeIn(
                            tween(
                                durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                                easing = FlowMotion.EnterEasing,
                            ),
                        ),
                    exit =
                        fadeOut(
                            tween(
                                durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                                easing = FlowMotion.ExitEasing,
                            ),
                        ),
                    label = "liveChatJumpAction",
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            followLive = true
                            coroutineScope.launch {
                                if (reduceMotion) {
                                    listState.scrollToItem(uniqueMessages.lastIndex)
                                } else {
                                    listState.animateScrollToItem(uniqueMessages.lastIndex)
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.live_chat_jump_to_latest),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatTextRow(message: LiveChatMessage) {
    val containerColor =
        when {
            message.isOwner -> MaterialTheme.colorScheme.errorContainer
            message.isModerator -> MaterialTheme.colorScheme.primaryContainer
            else -> null
        }
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(containerColor?.let { Modifier.background(it) } ?: Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.Top,
    ) {
        ChatAvatar(message.authorPhotoUrl, message.memberBadgeUrl)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            AuthorLine(message)
            ChatMessageText(
                segments = message.segments,
                fallback = message.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MembershipRow(message: LiveChatMessage) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChatAvatar(message.authorPhotoUrl, message.memberBadgeUrl)
            Spacer(Modifier.width(8.dp))
            Text(
                text = message.author.ifBlank { "—" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (message.message.isNotBlank() || message.segments.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            ChatMessageText(
                segments = message.segments,
                fallback = message.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun SuperChatRow(message: LiveChatMessage) {
    val bodyColor = message.superChatArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.tertiaryContainer
    val headerColor = message.superChatHeaderArgb?.let { Color(it) } ?: bodyColor
    val onBody = if (bodyColor.luminance() > 0.5f) Color.Black else Color.White
    val onHeader = if (headerColor.luminance() > 0.5f) Color.Black else Color.White

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(bodyColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatAvatar(message.authorPhotoUrl, message.memberBadgeUrl)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = message.author,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = onHeader,
                )
                message.superChatAmount?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = onHeader,
                    )
                }
            }
        }
        if (message.message.isNotBlank() || message.segments.isNotEmpty()) {
            ChatMessageText(
                segments = message.segments,
                fallback = message.message,
                style = MaterialTheme.typography.bodyMedium,
                color = onBody,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ChatMessageText(
    segments: List<LiveChatSegment>,
    fallback: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) {
        Text(text = fallback, style = style, color = color, modifier = modifier)
        return
    }
    if (segments.none { it.emojiImageUrl != null }) {
        Text(
            text = segments.joinToString("") { it.text },
            style = style,
            color = color,
            modifier = modifier,
        )
        return
    }

    val inlineContent = HashMap<String, InlineTextContent>()
    val annotated =
        buildAnnotatedString {
            segments.forEachIndexed { index, segment ->
                val imageUrl = segment.emojiImageUrl
                if (imageUrl != null) {
                    val key = "emoji_$index"
                    appendInlineContent(key, segment.text.ifBlank { ":emoji:" })
                    inlineContent[key] =
                        InlineTextContent(
                            placeholder =
                                Placeholder(
                                    width = 1.4.em,
                                    height = 1.4.em,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                                ),
                        ) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = segment.text,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                } else {
                    append(segment.text)
                }
            }
        }
    Text(
        text = annotated,
        style = style,
        color = color,
        inlineContent = inlineContent,
        modifier = modifier,
    )
}

@Composable
private fun AuthorLine(message: LiveChatMessage) {
    val authorColor =
        when {
            message.isOwner -> MaterialTheme.colorScheme.onErrorContainer
            message.isModerator -> MaterialTheme.colorScheme.onPrimaryContainer
            message.isMember -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (message.isModerator) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = authorColor,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = message.author.ifBlank { "—" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = authorColor,
        )
        if (message.isVerified) {
            Spacer(Modifier.width(3.dp))
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
        message.timestamp?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.width(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LiveChatPreview(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.live_chat),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChatAvatar(
    url: String?,
    memberBadgeUrl: String? = null,
) {
    Box {
        if (url.isNullOrBlank()) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape),
            )
        }
        if (!memberBadgeUrl.isNullOrBlank()) {
            AsyncImage(
                model = memberBadgeUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape),
            )
        }
    }
}
