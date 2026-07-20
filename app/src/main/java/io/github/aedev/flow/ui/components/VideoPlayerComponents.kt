package io.github.aedev.flow.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import io.github.aedev.flow.data.local.PlayerPreferences
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
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.ui.theme.extendedColors
import io.github.aedev.flow.utils.avatarImageIdentityKey
import io.github.aedev.flow.utils.formatSubscriberCount
import io.github.aedev.flow.utils.formatViewCount
import io.github.aedev.flow.utils.formatRichText
import io.github.aedev.flow.utils.DateContext
import io.github.aedev.flow.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoInfoSection(
    video: Video,
    title: String,
    viewCount: Long,
    uploadDate: String?,
    description: String?,
    isUpcoming: Boolean = false,
    channelName: String,
    channelAvatarUrl: String,
    channelAvatarUrls: List<String> = emptyList(),
    collaborators: List<VideoCollaborator> = emptyList(),
    subscriberCount: Long?,
    isSubscribed: Boolean,
    isNotificationsEnabled: Boolean = false,
    likeState: String,
    likeCount: Long? = null,
    dislikeCount: Long?,
    onSubscribeClick: () -> Unit,
    onUnsubscribeClick: () -> Unit = {},
    onNotificationChange: (Boolean) -> Unit = {},
    onChannelClick: () -> Unit,
    onCollaboratorClick: (String) -> Unit = {},
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackgroundPlayClick: () -> Unit,
    onCopyLinkClick: () -> Unit = {},
    onCopyLinkAtTimeClick: () -> Unit = {},
    onDescriptionClick: () -> Unit,
    isSaved: Boolean = false,
    isDownloaded: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showCollaborators by remember { mutableStateOf(false) }
    val displayChannelName = rememberCollaboratorChannelDisplayName(channelName, collaborators)
    val avatarUrls = remember(channelAvatarUrl, channelAvatarUrls, collaborators, video.channelThumbnailUrl) {
        val sources = if (collaborators.size > 1) {
            collaborators.map { it.thumbnailUrl }
        } else {
            listOf(channelAvatarUrl, video.channelThumbnailUrl) + channelAvatarUrls
        }
        sources
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.avatarImageIdentityKey() }
            .take(if (collaborators.size > 1) 3 else 1)
    }
    val openChannelOrCollaborators = {
        if (collaborators.size > 1) {
            showCollaborators = true
        } else {
            onChannelClick()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        // ============ TITLE SECTION ============
        val context = LocalContext.current
        val prefs = remember { PlayerPreferences(context) }
        val titleMaxLinesPref by prefs.videoTitleMaxLines.collectAsState(initial = 1)
        val titleMaxLines = if (titleMaxLinesPref <= 0) Int.MAX_VALUE else titleMaxLinesPref
        val dateSettings = rememberDateDisplaySettings()
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 28.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = titleMaxLines,
            overflow = if (titleMaxLinesPref <= 0) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Video Title", title))
                    Toast.makeText(context, context.getString(R.string.title_copied), Toast.LENGTH_SHORT).show()
                }
            )
        )
        
        // View count and date in a subtle row below title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    isUpcoming && viewCount > 0L -> stringResource(R.string.upcoming_waiting_count, formatViewCount(viewCount))
                    isUpcoming -> stringResource(R.string.upcoming_label)
                    else -> stringResource(R.string.views_count_short_template, formatViewCount(viewCount))
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isUpcoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isUpcoming && !uploadDate.isNullOrBlank()) {
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateSettings.format(uploadDate, DateContext.WATCH, video.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Text(
                text = stringResource(R.string.desc_more),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(onClick = onDescriptionClick)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ============ CHANNEL SECTION ============
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { openChannelOrCollaborators() }
            ) {
                ChannelAvatarStack(
                    urls = avatarUrls,
                    contentDescription = displayChannelName,
                    avatarSize = 44.dp
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayChannelName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
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

            Spacer(modifier = Modifier.width(8.dp))

            SubscribeButton(
                isSubscribed = isSubscribed,
                isNotificationsEnabled = isNotificationsEnabled,
                onSubscribeClick = onSubscribeClick,
                onUnsubscribeClick = onUnsubscribeClick,
                onNotificationChange = onNotificationChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ============ ACTION ROW ============
        VideoActionRow(
            likeState = likeState,
            likeCount = likeCount,
            dislikeCount = dislikeCount,
            onLikeClick = onLikeClick,
            onDislikeClick = onDislikeClick,
            onShareClick = onShareClick,
            onDownloadClick = onDownloadClick,
            onSaveClick = onSaveClick,
            onBackgroundPlayClick = onBackgroundPlayClick,
            onCopyLinkClick = onCopyLinkClick,
            onCopyLinkAtTimeClick = onCopyLinkAtTimeClick,
            isSaved = isSaved,
            isDownloaded = isDownloaded
        )
    }

    if (showCollaborators) {
        CollaboratorsBottomSheet(
            collaborators = collaborators,
            onChannelClick = onCollaboratorClick,
            onDismiss = { showCollaborators = false }
        )
    }
}

@Composable
fun CommentsPreview(
    latestComment: String?,
    authorAvatar: String?,
    showPreviewText: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.comments),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            if (showPreviewText && !latestComment.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = authorAvatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Gray),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val annotatedComment = if (!latestComment.isNullOrBlank()) {
                        formatRichText(
                            text = latestComment,
                            primaryColor = primaryColor,
                            textColor = MaterialTheme.colorScheme.onSurface
                        )
                    } else null

                    Text(
                        text = annotatedComment ?: AnnotatedString(""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (showPreviewText) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.add_comment_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SubscribeButton(
    isSubscribed: Boolean,
    isNotificationsEnabled: Boolean = false,
    onSubscribeClick: () -> Unit,
    onUnsubscribeClick: () -> Unit = {},
    onNotificationChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    val backgroundColor = if (isSubscribed)
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    else
        MaterialTheme.colorScheme.onBackground

    val contentColor = if (isSubscribed)
        MaterialTheme.colorScheme.onSurface
    else
        MaterialTheme.colorScheme.surface

    Box {
        Surface(
            onClick = {
                if (isSubscribed) expanded = true else onSubscribeClick()
            },
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
                        imageVector = if (isNotificationsEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.subscribed),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor
                    )
                } else {
                    Text(
                        text = stringResource(R.string.subscribe),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = contentColor
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(200.dp)
        ) {
            Text(
                text = stringResource(R.string.notifications),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.on)) },
                leadingIcon = { Icon(Icons.Rounded.NotificationsActive, null) },
                onClick = {
                    onNotificationChange(true)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.off)) },
                leadingIcon = { Icon(Icons.Rounded.NotificationsOff, null) },
                onClick = {
                    onNotificationChange(false)
                    expanded = false
                }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.unsubscribe)) },
                leadingIcon = { Icon(Icons.Rounded.PersonRemove, null) },
                onClick = {
                    onUnsubscribeClick()
                    expanded = false
                }
            )
        }
    }
}

@Composable
fun VideoActionRow(
    likeState: String,
    likeCount: Long? = null,
    dislikeCount: Long?,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackgroundPlayClick: () -> Unit,
    onCopyLinkClick: () -> Unit = {},
    onCopyLinkAtTimeClick: () -> Unit = {},
    isSaved: Boolean = false,
    isDownloaded: Boolean = false
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            SegmentedLikeDislikeButton(
                likeState = likeState,
                likeCount = likeCount,
                dislikeCount = dislikeCount,
                onLikeClick = onLikeClick,
                onDislikeClick = onDislikeClick
            )
        }
        
        item {
            ActionChip(
                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (isSaved) stringResource(R.string.saved) else stringResource(R.string.save),
                onClick = onSaveClick,
                tint = if (isSaved) MaterialTheme.colorScheme.primary else null
            )
        }
        
        item {
            ActionChip(
                icon = if (isDownloaded) Icons.Outlined.CheckCircle else Icons.Outlined.Download,
                label = if (isDownloaded) stringResource(R.string.downloaded) else stringResource(R.string.download),
                onClick = onDownloadClick,
                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else null
            )
        }
        
        item {
            ActionChip(
                icon = Icons.Outlined.Headphones,
                label = stringResource(R.string.player_action_background),
                onClick = onBackgroundPlayClick
            )
        }
        
        item {
            ActionChip(
                icon = Icons.Outlined.Share,
                label = stringResource(R.string.share),
                onClick = onShareClick
            )
        }

        item {
            ActionChip(
                icon = Icons.Outlined.Link,
                label = stringResource(R.string.player_action_copy_link),
                onClick = onCopyLinkClick
            )
        }

        item {
            ActionChip(
                icon = Icons.Outlined.Timer,
                label = stringResource(R.string.player_action_copy_link_at_time),
                onClick = onCopyLinkAtTimeClick
            )
        }
        
    }
}

@Composable
fun SegmentedLikeDislikeButton(
    likeState: String,
    likeCount: Long? = null,
    dislikeCount: Long?,
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
                    contentDescription = stringResource(R.string.like),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                val likeText = if (likeCount != null && likeCount > 0) {
                    formatViewCount(likeCount) 
                } else if (likeState == "LIKED") stringResource(R.string.liked) else stringResource(R.string.like)
                
                Text(
                    text = likeText,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onDislikeClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (likeState == "DISLIKED") Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = stringResource(R.string.player_action_dislike),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                             
                if (dislikeCount != null && dislikeCount > 0) {
                     Spacer(modifier = Modifier.width(6.dp))
                     Text(
                        text = formatViewCount(dislikeCount),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null
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
                tint = tint ?: MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = tint ?: MaterialTheme.colorScheme.onSurface
            )
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
                    text = stringResource(R.string.description),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = secondaryTextColor
                )
            }
            
            if (!description.isNullOrBlank()) {
                // Use unified rich text formatter for HTML, links, and timestamps
                val annotatedDescription = formatRichText(
                    text = description,
                    primaryColor = primaryColor,
                    textColor = textColor
                )
                
                Text(
                    text = annotatedDescription,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 24.sp
                    ),
                    color = textColor,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Show more/less button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = if (expanded) stringResource(R.string.ui_show_less) else stringResource(R.string.ui_show_more),
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
                    text = stringResource(R.string.empty_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
            }
        }
    }
}

