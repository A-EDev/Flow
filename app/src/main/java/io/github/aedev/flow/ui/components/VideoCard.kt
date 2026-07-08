@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.aedev.flow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import coil.compose.AsyncImage
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.DeArrowResult
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.data.repository.DeArrowRepository
import io.github.aedev.flow.data.repository.VideoCollaboratorResolver
import io.github.aedev.flow.ui.theme.extendedColors
import io.github.aedev.flow.utils.avatarImageIdentityKey
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.utils.formatDuration
import io.github.aedev.flow.utils.formatPremiereDate
import io.github.aedev.flow.utils.DateContext
import io.github.aedev.flow.utils.formatViewCount
import kotlinx.coroutines.flow.collectLatest

private const val AVATAR_TAG = "ChannelAvatarImage"

private fun Video.channelAvatarUrls(collaborators: List<VideoCollaborator> = emptyList()): List<String> {
    val collaboratorAvatars = if (collaborators.size > 1) {
        collaborators.map { it.thumbnailUrl }
    } else {
        emptyList()
    }
    return (collaboratorAvatars + channelThumbnailUrls + channelThumbnailUrl)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.avatarImageIdentityKey() }
        .take(3)
}

internal fun Video.collaboratorItems(resolvedCollaborators: List<VideoCollaborator> = emptyList()): List<VideoCollaborator> {
    val modelItems = (collaborators + resolvedCollaborators)
        .filter { it.name.isNotBlank() }
        .filter { it.hasChannelCollaboratorSignal() }
        .distinctBy { it.channelId.ifBlank { it.name.lowercase() } }
    if (modelItems.size > 1) return modelItems

    val names = channelName.splitCollaboratorNames()
    val avatars = channelAvatarUrls()
    val count = maxOf(names.size, avatars.size)
    if (count <= 1) return emptyList()

    return List(count) { index ->
        VideoCollaborator(
            name = names.getOrNull(index).orEmpty(),
            channelId = if (index == 0) channelId else "",
            thumbnailUrl = avatars.getOrNull(index).orEmpty(),
            subscriberCountText = "",
        )
    }
}

private fun String.splitCollaboratorNames(): List<String> =
    split(Regex("""\s+(?:and|&|x|with)\s+""", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }

private fun VideoCollaborator.hasChannelCollaboratorSignal(): Boolean =
    channelId.startsWith("UC") ||
        thumbnailUrl.isNotBlank() ||
        subscriberCountText.contains("subscriber", ignoreCase = true)

internal fun List<VideoCollaborator>.displayCollaboratorChannelName(
    fallback: String,
    moreCollaboratorsText: String? = null,
): String {
    val names = map { it.name }.filter { it.isNotBlank() }
    return when {
        names.size > 2 && moreCollaboratorsText != null -> moreCollaboratorsText
        names.size > 1 -> names.joinToString(" and ")
        else -> fallback
    }
}

@Composable
internal fun rememberCollaboratorChannelDisplayName(
    fallback: String,
    collaborators: List<VideoCollaborator>,
): String {
    val firstName = collaborators.firstOrNull()?.name.orEmpty()
    val compactName = stringResource(
        R.string.channel_and_more_template,
        firstName,
        (collaborators.size - 1).coerceAtLeast(0)
    )
    return remember(fallback, collaborators, compactName) {
        collaborators.displayCollaboratorChannelName(fallback, compactName)
    }
}

@Composable
internal fun rememberCollaboratorItems(video: Video): List<VideoCollaborator> {
    val fetchedCollaborators by produceState<List<VideoCollaborator>>(
        initialValue = emptyList(),
        key1 = video.id,
        key2 = video.collaborators
    ) {
        value = if (video.collaborators.size > 1) {
            emptyList()
        } else {
            VideoCollaboratorResolver.resolve(video.id)
        }
    }
    return remember(video, fetchedCollaborators) {
        video.collaboratorItems(fetchedCollaborators)
    }
}

@Composable
private fun UpcomingReminderBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.7f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Icon(
            imageVector = Icons.Rounded.NotificationsActive,
            contentDescription = stringResource(R.string.upcoming_video_reminder_badge),
            tint = Color.White,
            modifier = Modifier
                .size(20.dp)
                .padding(4.dp)
        )
    }
}

@Composable
fun VideoCard(
    video: Video,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onChannelClick: ((String) -> Unit)? = null
) {
    var showQuickActions by remember { mutableStateOf(false) }
    var showCollaborators by remember { mutableStateOf(false) }
    val collaboratorItems = rememberCollaboratorItems(video)
    val displayChannelName = rememberCollaboratorChannelDisplayName(video.channelName, collaboratorItems)
    val openChannelOrCollaborators = {
        if (collaboratorItems.size > 1) {
            showCollaborators = true
        } else {
            onChannelClick?.invoke(video.channelId)
        }
    }
    val context = LocalContext.current
    val dateSettings = rememberDateDisplaySettings()
    val watchProgress by produceState<Float?>(initialValue = null, video.id) {
        ViewHistory.getInstance(context).getVideoHistory(video.id).collectLatest { entry ->
            value = if (entry != null && entry.duration > 0 && entry.progressPercentage >= 3f) {
                if (entry.progressPercentage >= 90f) 1.0f else entry.progressPercentage / 100f
            } else null
        }
    }

    val playerPrefs = remember { PlayerPreferences(context) }
    val deArrowEnabled by playerPrefs.deArrowEnabled.collectAsState(initial = false)
    val deArrowBadgeEnabled by playerPrefs.deArrowBadgeEnabled.collectAsState(initial = false)
    val deArrowResult by produceState<DeArrowResult?>(
        initialValue = null, key1 = video.id, key2 = deArrowEnabled
    ) {
        value = if (deArrowEnabled) DeArrowRepository.getDeArrowResult(video.id) else null
    }
    val displayTitle = deArrowResult?.title ?: video.title
    val displayThumbnailUrl = deArrowResult?.thumbnailUrl ?: video.thumbnailUrl
    val videoCardActionsEnabled by playerPrefs.videoCardActionsEnabled.collectAsState(initial = false)
    val upcomingReminderIds by playerPrefs.upcomingVideoReminderIds.collectAsState(initial = emptySet())
    val quickActionsVm: QuickActionsViewModel = hiltViewModel()
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(180.dp)
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onLongClick = { showQuickActions = true },
                onClick = onClick
            )
            .padding(4.dp)
    ) {
        // THUMBNAIL BOX
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .thumbnailGradientOverlay()
        ) {
            VideoThumbnailImage(
                videoId = video.id,
                model = displayThumbnailUrl,
                contentDescription = displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (video.isUpcoming) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = stringResource(R.string.status_upcoming),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (video.isLive || video.duration > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = if (video.isLive) Color(0xFFCC0000).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = if (video.isLive) stringResource(R.string.status_live) else formatDuration(video.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (video.isUpcoming && video.id in upcomingReminderIds) {
                UpcomingReminderBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }

            // Watch progress bar
            watchProgress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.4f)
                )
            }

            if (deArrowResult != null && deArrowBadgeEnabled) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = stringResource(R.string.dearrow_badge),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(2.dp)
                    )
                }
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
            ChannelAvatarStack(
                urls = video.channelAvatarUrls(collaboratorItems),
                contentDescription = displayChannelName,
                avatarSize = 32.dp,
                modifier = if (onChannelClick != null) {
                    Modifier.clickable { openChannelOrCollaborators() }
                } else {
                    Modifier
                }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.12f
                    ),
                    fontWeight = FontWeight.SemiBold, // Stronger weight for readability
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Metadata Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val premiereDate = formatPremiereDate(video.uploadDate)
                    val displayDate = remember(video.uploadDate, video.timestamp, dateSettings) {
                        dateSettings.format(video.uploadDate, DateContext.LISTS, video.timestamp)
                    }
                    Text(
                        text = if (video.isUpcoming)
                            premiereDate?.let { stringResource(R.string.premiere_date_prefix, it) } ?: stringResource(R.string.premiere_soon)
                        else if (video.viewCount >= 0L)
                            stringResource(R.string.video_metadata_short_template, displayChannelName, stringResource(R.string.views_template, formatViewCount(video.viewCount)))
                        else
                            "$displayChannelName · $displayDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (video.isUpcoming) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onChannelClick != null) {
                            Modifier.clickable { openChannelOrCollaborators() }
                        } else {
                            Modifier
                        }
                    )
                }
            }

            IconButton(
                onClick = { showQuickActions = true },
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

        // Like / Dislike action buttons
        if (videoCardActionsEnabled) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { quickActionsVm.markAsInteresting(video) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.ThumbUp,
                        contentDescription = stringResource(R.string.i_like_this),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.i_like_this),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { quickActionsVm.markNotInterested(video) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.ThumbDown,
                        contentDescription = stringResource(R.string.not_interested),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.not_interested),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    
    if (showQuickActions) {
         VideoQuickActionsBottomSheet(
            video = video,
            onChannelClick = onChannelClick,
            onDismiss = { showQuickActions = false }
        )
    }

    if (showCollaborators) {
        CollaboratorsBottomSheet(
            collaborators = collaboratorItems,
            onChannelClick = onChannelClick,
            onDismiss = { showCollaborators = false }
        )
    }
}

@Composable
fun VideoCardHorizontal(
    video: Video,
    modifier: Modifier = Modifier,
    onChannelClick: ((String) -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val dateSettings = rememberDateDisplaySettings()
    val playerPrefs = remember { PlayerPreferences(context) }
    val deArrowEnabled by playerPrefs.deArrowEnabled.collectAsState(initial = false)
    val deArrowResult by produceState<DeArrowResult?>(
        initialValue = null, key1 = video.id, key2 = deArrowEnabled
    ) {
        value = if (deArrowEnabled) DeArrowRepository.getDeArrowResult(video.id) else null
    }
    val displayTitle = deArrowResult?.title ?: video.title
    val displayThumbnailUrl = deArrowResult?.thumbnailUrl ?: video.thumbnailUrl
    val upcomingReminderIds by playerPrefs.upcomingVideoReminderIds.collectAsState(initial = emptySet())
    val watchProgress by produceState<Float?>(initialValue = null, video.id) {
        ViewHistory.getInstance(context).getVideoHistory(video.id).collectLatest { entry ->
            value = if (entry != null && entry.duration > 0 && entry.progressPercentage >= 3f) {
                if (entry.progressPercentage >= 90f) 1.0f else entry.progressPercentage / 100f
            } else null
        }
    }

    var showQuickActions by remember { mutableStateOf(false) }
    var showCollaborators by remember { mutableStateOf(false) }
    val collaboratorItems = rememberCollaboratorItems(video)
    val displayChannelName = rememberCollaboratorChannelDisplayName(video.channelName, collaboratorItems)
    val openChannelOrCollaborators = {
        if (collaboratorItems.size > 1) {
            showCollaborators = true
        } else {
            onChannelClick?.invoke(video.channelId)
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onLongClick = { showQuickActions = true },
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp)) // Sleek corners
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            VideoThumbnailImage(
                videoId = video.id,
                model = displayThumbnailUrl,
                contentDescription = displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (video.isUpcoming) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.status_upcoming),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            } else if (video.isLive || video.duration > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    color = if (video.isLive) Color(0xFFCC0000).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (video.isLive) stringResource(R.string.status_live) else formatDuration(video.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            if (video.isUpcoming && video.id in upcomingReminderIds) {
                UpcomingReminderBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
            }

            // Watch progress bar
            watchProgress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.4f)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Column {
                Text(
                    text = displayChannelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onChannelClick != null) {
                        Modifier.clickable { openChannelOrCollaborators() }
                    } else {
                        Modifier
                    }
                )

                val premiereDate = formatPremiereDate(video.uploadDate)
                val displayDate = remember(video.uploadDate, video.timestamp, dateSettings) {
                    dateSettings.format(video.uploadDate, DateContext.LISTS, video.timestamp)
                }
                Text(
                    text = if (video.isUpcoming)
                               premiereDate?.let { stringResource(R.string.premiere_date_prefix, it) } ?: stringResource(R.string.premiere_soon)
                           else if (video.viewCount >= 0L)
                               stringResource(R.string.video_metadata_short_template, stringResource(R.string.views_template, formatViewCount(video.viewCount)), displayDate)
                           else
                               "$displayChannelName · $displayDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (video.isUpcoming) MaterialTheme.colorScheme.primary
                            else MaterialTheme.extendedColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = video,
            onChannelClick = onChannelClick,
            onDismiss = { showQuickActions = false }
        )
    }

    if (showCollaborators) {
        CollaboratorsBottomSheet(
            collaborators = collaboratorItems,
            onChannelClick = onChannelClick,
            onDismiss = { showCollaborators = false }
        )
    }
}

@Composable
fun VideoCardFullWidth(
    video: Video,
    modifier: Modifier = Modifier,
    useInternalPadding: Boolean = true,
    onClick: () -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    onMoreClick: () -> Unit = {}
) {
    var showQuickActions by remember { mutableStateOf(false) }
    var showCollaborators by remember { mutableStateOf(false) }
    val collaboratorItems = rememberCollaboratorItems(video)
    val displayChannelName = rememberCollaboratorChannelDisplayName(video.channelName, collaboratorItems)
    val openChannelOrCollaborators = {
        if (collaboratorItems.size > 1) {
            showCollaborators = true
        } else {
            onChannelClick?.invoke(video.channelId)
        }
    }
    val context = LocalContext.current
    val dateSettings = rememberDateDisplaySettings()
    val watchProgress by produceState<Float?>(initialValue = null, video.id) {
        ViewHistory.getInstance(context).getVideoHistory(video.id).collectLatest { entry ->
            value = if (entry != null && entry.duration > 0 && entry.progressPercentage >= 3f) {
                if (entry.progressPercentage >= 90f) 1.0f else entry.progressPercentage / 100f
            } else null
        }
    }

    // DeArrow: replace clickbait titles and thumbnails if enabled
    val playerPrefsFullWidth = remember { PlayerPreferences(context) }
    val deArrowEnabledFullWidth by playerPrefsFullWidth.deArrowEnabled.collectAsState(initial = false)
    val deArrowBadgeEnabledFullWidth by playerPrefsFullWidth.deArrowBadgeEnabled.collectAsState(initial = false)
    val deArrowResultFullWidth by produceState<DeArrowResult?>(
        initialValue = null, key1 = video.id, key2 = deArrowEnabledFullWidth
    ) {
        value = if (deArrowEnabledFullWidth) DeArrowRepository.getDeArrowResult(video.id) else null
    }
    val displayTitle = deArrowResultFullWidth?.title ?: video.title
    val displayThumbnailUrl = deArrowResultFullWidth?.thumbnailUrl ?: video.thumbnailUrl
    val videoCardActionsEnabledFW by playerPrefsFullWidth.videoCardActionsEnabled.collectAsState(initial = false)
    val videoCardMarkWatchedEnabledFW by playerPrefsFullWidth.videoCardMarkWatchedEnabled.collectAsState(initial = false)
    val upcomingReminderIds by playerPrefsFullWidth.upcomingVideoReminderIds.collectAsState(initial = emptySet())
    val quickActionsVmFW: QuickActionsViewModel = hiltViewModel()
    val watchedVideoIdsFW by quickActionsVmFW.watchedVideoIds.collectAsState()
    val isWatchedFW = remember(watchedVideoIdsFW, watchProgress, video.id) {
        watchedVideoIdsFW.contains(video.id) || (watchProgress ?: 0f) >= 0.90f
    }

    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onLongClick = { showQuickActions = true },
                onClick = onClick
            )
            .then(if (useInternalPadding) Modifier.padding(horizontal = 12.dp) else Modifier)
    ) {
        // Thumbnail with duration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .thumbnailGradientOverlay()
        ) {
            VideoThumbnailImage(
                videoId = video.id,
                model = displayThumbnailUrl,
                contentDescription = displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (video.isUpcoming) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(5.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = stringResource(R.string.status_upcoming),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (video.isLive || video.duration > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = if (video.isLive) Color(0xFFCC0000).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(5.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = if (video.isLive) stringResource(R.string.status_live) else formatDuration(video.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (video.isUpcoming && video.id in upcomingReminderIds) {
                UpcomingReminderBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }

            // Watch progress bar
            watchProgress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.4f)
                )
            }

            if (deArrowResultFullWidth != null && deArrowBadgeEnabledFullWidth) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = stringResource(R.string.dearrow_badge),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(2.dp)
                    )
                }
            }
        }

        // Video info section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChannelAvatarStack(
                urls = video.channelAvatarUrls(collaboratorItems),
                contentDescription = displayChannelName,
                avatarSize = 40.dp,
                modifier = if (onChannelClick != null) {
                    Modifier.clickable { openChannelOrCollaborators() }
                } else {
                    Modifier
                }
            )

            // Video details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.12f
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val premiereDate = formatPremiereDate(video.uploadDate)
                val displayDate = remember(video.uploadDate, video.timestamp, dateSettings) {
                    dateSettings.format(video.uploadDate, DateContext.LISTS, video.timestamp)
                }
                Text(
                    text = if (video.isUpcoming)
                           premiereDate?.let { stringResource(R.string.premiere_date_prefix, it) } ?: stringResource(R.string.premiere_soon)
                           else if (video.viewCount >= 0L)
                               stringResource(R.string.video_metadata_template, displayChannelName, stringResource(R.string.views_template, formatViewCount(video.viewCount)), displayDate)
                           else
                               "$displayChannelName · $displayDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (video.isUpcoming) MaterialTheme.colorScheme.primary
                            else MaterialTheme.extendedColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onChannelClick != null)
                        Modifier.clickable { openChannelOrCollaborators() }
                    else Modifier
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

        // Video card quick actions (like/dislike/mark watched)
        if (videoCardActionsEnabledFW || videoCardMarkWatchedEnabledFW) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (videoCardActionsEnabledFW) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { quickActionsVmFW.markAsInteresting(video) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.ThumbUp,
                                contentDescription = stringResource(R.string.i_like_this),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.i_like_this),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { quickActionsVmFW.markNotInterested(video) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.ThumbDown,
                                contentDescription = stringResource(R.string.not_interested),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.not_interested),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (videoCardMarkWatchedEnabledFW) {
                    val watchedTint = if (isWatchedFW) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (!isWatchedFW) quickActionsVmFW.markAsWatched(video)
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = stringResource(R.string.mark_as_watched),
                            modifier = Modifier.size(16.dp),
                            tint = watchedTint
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.mark_as_watched),
                            style = MaterialTheme.typography.labelMedium,
                            color = watchedTint
                        )
                    }
                }
            }
        }
    }
    
    // Quick actions bottom sheet
    if (showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = video,
            onChannelClick = onChannelClick,
            onDismiss = { showQuickActions = false }
        )
    }

    if (showCollaborators) {
        CollaboratorsBottomSheet(
            collaborators = collaboratorItems,
            onChannelClick = onChannelClick,
            onDismiss = { showCollaborators = false }
        )
    }
}

/**
 * A horizontal Video Card optimized for side panes (tablets/foldables) or lists.
 * Image on Left, Info on Right.
 */
@Composable
fun CompactVideoCard(
    video: Video,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {},
    onChannelClick: ((String) -> Unit)? = null
) {
    var showQuickActions by remember { mutableStateOf(false) }
    var showCollaborators by remember { mutableStateOf(false) }
    val collaboratorItems = rememberCollaboratorItems(video)
    val displayChannelName = rememberCollaboratorChannelDisplayName(video.channelName, collaboratorItems)
    val openChannelOrCollaborators = {
        if (collaboratorItems.size > 1) {
            showCollaborators = true
        } else {
            onChannelClick?.invoke(video.channelId)
        }
    }
    val context = LocalContext.current
    val dateSettings = rememberDateDisplaySettings()
    val watchProgress by produceState<Float?>(initialValue = null, video.id) {
        ViewHistory.getInstance(context).getVideoHistory(video.id).collectLatest { entry ->
            value = if (entry != null && entry.duration > 0 && entry.progressPercentage >= 3f) {
                if (entry.progressPercentage >= 90f) 1.0f else entry.progressPercentage / 100f
            } else null
        }
    }

    // DeArrow: replace clickbait titles and thumbnails if enabled
    val playerPrefsCompact = remember { PlayerPreferences(context) }
    val deArrowEnabledCompact by playerPrefsCompact.deArrowEnabled.collectAsState(initial = false)
    val deArrowBadgeEnabledCompact by playerPrefsCompact.deArrowBadgeEnabled.collectAsState(initial = false)
    val deArrowResultCompact by produceState<DeArrowResult?>(
        initialValue = null, key1 = video.id, key2 = deArrowEnabledCompact
    ) {
        value = if (deArrowEnabledCompact) DeArrowRepository.getDeArrowResult(video.id) else null
    }
    val videoCardMarkWatchedEnabledCompact by playerPrefsCompact.videoCardMarkWatchedEnabled.collectAsState(initial = false)
    val quickActionsVmCompact: QuickActionsViewModel = hiltViewModel()
    val watchedVideoIdsCompact by quickActionsVmCompact.watchedVideoIds.collectAsState()
    val isWatchedCompact = remember(watchedVideoIdsCompact, watchProgress, video.id) {
        watchedVideoIdsCompact.contains(video.id) || (watchProgress ?: 0f) >= 0.90f
    }
    val displayTitle = deArrowResultCompact?.title ?: video.title
    val displayThumbnailUrl = deArrowResultCompact?.thumbnailUrl ?: video.thumbnailUrl

    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onLongClick = { showQuickActions = true },
                onClick = onClick
            )
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        // Thumbnail (Left side)
        Box(
            modifier = Modifier
                .width(168.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            VideoThumbnailImage(
                videoId = video.id,
                model = displayThumbnailUrl,
                contentDescription = displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (video.isUpcoming || video.viewCount < 0L) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.status_upcoming),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (video.isLive || video.duration > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    color = if (video.isLive) Color(0xFFCC0000).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (video.isLive) stringResource(R.string.status_live) else formatDuration(video.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Watch progress bar
            watchProgress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.4f)
                )
            }

            if (deArrowResultCompact != null && deArrowBadgeEnabledCompact) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = stringResource(R.string.dearrow_badge),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info (Right side)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.12f
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = displayChannelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onChannelClick != null)
                    Modifier.clickable { openChannelOrCollaborators() }
                else Modifier
            )
            
            val premiereDate = formatPremiereDate(video.uploadDate)
            val displayDate = remember(video.uploadDate, video.timestamp, dateSettings) {
                dateSettings.format(video.uploadDate, DateContext.LISTS, video.timestamp)
            }
            Text(
                text = if (video.viewCount < 0L)
                           premiereDate?.let { stringResource(R.string.premiere_date_prefix, it) } ?: stringResource(R.string.premiere_soon)
                       else stringResource(R.string.video_metadata_short_template, stringResource(R.string.views_template, formatViewCount(video.viewCount)), displayDate),
                style = MaterialTheme.typography.bodySmall,
                color = if (video.viewCount < 0L) MaterialTheme.colorScheme.primary
                        else MaterialTheme.extendedColors.textSecondary.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            IconButton(
                onClick = { showQuickActions = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (videoCardMarkWatchedEnabledCompact) {
                IconButton(
                    onClick = {
                        if (!isWatchedCompact) quickActionsVmCompact.markAsWatched(video)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = stringResource(R.string.mark_as_watched),
                        tint = if (isWatchedCompact) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
    
    if (showQuickActions) {
         VideoQuickActionsBottomSheet(
            video = video,
            onChannelClick = onChannelClick,
            onDismiss = { showQuickActions = false }
        )
    }

    if (showCollaborators) {
        CollaboratorsBottomSheet(
            collaborators = collaboratorItems,
            onChannelClick = onChannelClick,
            onDismiss = { showCollaborators = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaboratorsBottomSheet(
    collaborators: List<VideoCollaborator>,
    onChannelClick: ((String) -> Unit)?,
    onDismiss: () -> Unit,
    viewModel: QuickActionsViewModel = hiltViewModel()
) {
    val subscribedChannelIds by viewModel.subscribedChannelIds.collectAsState()
    val collaboratorChannelIds = remember(collaborators) {
        collaborators.map { it.channelId }.filter { it.isNotBlank() }.distinct()
    }
    androidx.compose.runtime.LaunchedEffect(collaboratorChannelIds) {
        collaboratorChannelIds.forEach { channelId ->
            viewModel.loadSubscriptionState(channelId)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.collaborators),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            collaborators.forEach { collaborator ->
                val canOpenChannel = onChannelClick != null && collaborator.channelId.isNotBlank()
                val isSubscribed = subscribedChannelIds.contains(collaborator.channelId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ChannelAvatarStack(
                        urls = listOf(collaborator.thumbnailUrl).filter { it.isNotBlank() },
                        contentDescription = collaborator.name,
                        avatarSize = 48.dp,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (canOpenChannel) {
                                    Modifier.clickable {
                                        onDismiss()
                                        onChannelClick?.invoke(collaborator.channelId)
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        val collaboratorName = collaborator.name.ifBlank { stringResource(R.string.collaborator) }
                        Text(
                            text = collaboratorName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (collaborator.subscriberCountText.isNotBlank()) {
                            Text(
                                text = collaborator.subscriberCountText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (collaborator.channelId.isNotBlank()) {
                        SubscribeButton(
                            isSubscribed = isSubscribed,
                            onSubscribeClick = {
                                viewModel.toggleSubscription(
                                    channelId = collaborator.channelId,
                                    channelName = collaborator.name,
                                    channelThumbnail = collaborator.thumbnailUrl
                                )
                            },
                            onUnsubscribeClick = {
                                viewModel.toggleSubscription(
                                    channelId = collaborator.channelId,
                                    channelName = collaborator.name,
                                    channelThumbnail = collaborator.thumbnailUrl
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingShelf(
    entries: List<VideoHistoryEntry>,
    onVideoClick: (String) -> Unit,
    onRemove: (String) -> Unit = {},
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onSeeAllClick != null) Modifier.clickable(onClick = onSeeAllClick) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = context.getString(R.string.continue_watching_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (onSeeAllClick != null) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(entries, key = { it.videoId }) { entry ->
                ContinueWatchingCard(
                    entry = entry,
                    onClick = { onVideoClick(entry.videoId) },
                    onRemove = { onRemove(entry.videoId) }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: VideoHistoryEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val resolvedCollaborators by produceState<List<VideoCollaborator>>(
        initialValue = emptyList(),
        key1 = entry.videoId
    ) {
        value = VideoCollaboratorResolver.resolve(entry.videoId)
    }
    val displayChannelName = rememberCollaboratorChannelDisplayName(entry.channelName, resolvedCollaborators)
    Column(
        modifier = Modifier
            .width(350.dp)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .thumbnailGradientOverlay()
        ) {
            VideoThumbnailImage(
                videoId = entry.videoId,
                model = entry.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Remaining time badge
            if (entry.duration > 0) {
                val remainingMs = (entry.duration - entry.position).coerceAtLeast(0L)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatContinueWatchingTime(remainingMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Progress bar
            LinearProgressIndicator(
                progress = { (entry.progressPercentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.4f)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.12f
                    ),
                    fontWeight = FontWeight.SemiBold
                )
                if (displayChannelName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayChannelName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatContinueWatchingTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}


@Composable
fun ShortsShelf(
    shorts: List<Video>,
    onShortClick: (Video) -> Unit,
    onSeeAllClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onSeeAllClick != null) Modifier.clickable(onClick = onSeeAllClick) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                text = context.getString(R.string.shorts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (onSeeAllClick != null) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(shorts, key = { it.id }) { short ->
                ShortsCard(video = short, onClick = { onShortClick(short) })
            }
        }
    }
}

@Composable
fun ShortsCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(160.dp)
) {
    var showQuickActions by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressScale(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onLongClick = { showQuickActions = true },
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .thumbnailGradientOverlay()
        ) {
            VideoThumbnailImage(
                videoId = video.id,
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
            text = stringResource(R.string.views_template, formatViewCount(video.viewCount)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.textSecondary
        )
    }

    if (showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = video,
            onChannelClick = null,
            onDismiss = { showQuickActions = false }
        )
    }
}

@Composable
fun VideoThumbnailImage(
    videoId: String,
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val models = remember(videoId, model) {
        when {
            model is String || model == null ->
                ThumbnailUrlResolver.resolveVideoThumbnailCandidates(videoId, model as? String)
            else -> listOf(model)
        }
    }

    SafeAsyncImage(
        models = models,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}

@Composable
private fun SafeAsyncImage(
    models: List<Any>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    var index by remember(models) { mutableStateOf(0) }
    val currentModel = models.getOrNull(index)

    when {
        currentModel is ImageVector -> Image(
            imageVector = currentModel,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        (currentModel is String && currentModel.isNotEmpty()) || currentModel is Int -> AsyncImage(
            model = currentModel,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            onError = {
                index = if (index < models.lastIndex) index + 1 else models.size
            }
        )
        else -> Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/**
 * Channel avatar that gracefully degrades on load failure:
 *  1. Tries the original URL (may be high-res, e.g. =s800)
 *  2. On failure, retries with =s88 (low-res) if a size parameter is present
 *  3. On second failure, or no size param, shows the AccountCircle icon
 */
@Composable
fun ChannelAvatarImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var currentModel by remember(url) {
        val highQualityUrl = ThumbnailUrlResolver.resolveChannelAvatar(url)
        val initial = highQualityUrl.takeIf { it.isNotEmpty() } ?: Icons.Default.AccountCircle
        if (initial is ImageVector) {
            Log.d(AVATAR_TAG, "null/empty url for '$contentDescription', using icon")
        } else {
            Log.d(AVATAR_TAG, "init url='$highQualityUrl' for '$contentDescription'")
        }
        mutableStateOf<Any>(initial)
    }
    var didRetry by remember(url) { mutableStateOf(false) }

    when (val model = currentModel) {
        is ImageVector -> Image(
            imageVector = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        else -> AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            onError = { errorResult ->
                val errMsg = errorResult.result.throwable?.message ?: "unknown error"
                if (!didRetry) {
                    didRetry = true
                    val src = currentModel as? String ?: run {
                        Log.e(AVATAR_TAG, "Expected String model but got ${currentModel::class.simpleName}")
                        return@AsyncImage
                    }
                    val lowRes = src.replace(Regex("=s\\d+"), "=s88")
                    if (lowRes != src) {
                        Log.w(AVATAR_TAG, "Failed '$src' ($errMsg) → retrying with '$lowRes'")
                        currentModel = lowRes
                    } else {
                        Log.e(AVATAR_TAG, "Failed '$src' ($errMsg), no size param to replace → icon")
                        currentModel = Icons.Default.AccountCircle
                    }
                } else {
                    Log.e(AVATAR_TAG, "Retry also failed for '$model' ($errMsg) → icon")
                    currentModel = Icons.Default.AccountCircle
                }
            }
        )
    }
}

@Composable
fun ChannelAvatarStack(
    urls: List<String>,
    contentDescription: String?,
    avatarSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val avatarUrls = urls.ifEmpty { listOf("") }.take(3)
    val primaryUrl = avatarUrls.first()
    val secondaryUrl = avatarUrls.getOrNull(1)
    val tertiaryUrl = avatarUrls.getOrNull(2)
    val stackedAvatarSize = when {
        !tertiaryUrl.isNullOrBlank() -> avatarSize * 0.64f
        !secondaryUrl.isNullOrBlank() -> avatarSize * 0.78f
        else -> avatarSize
    }

    Box(
        modifier = modifier
            .size(avatarSize)
    ) {
        if (!tertiaryUrl.isNullOrBlank()) {
            ChannelAvatarImage(
                url = tertiaryUrl,
                contentDescription = contentDescription,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(stackedAvatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.background,
                        shape = CircleShape
                    )
            )
        }

        if (!secondaryUrl.isNullOrBlank()) {
            ChannelAvatarImage(
                url = secondaryUrl,
                contentDescription = contentDescription,
                modifier = Modifier
                    .align(if (tertiaryUrl.isNullOrBlank()) Alignment.BottomEnd else Alignment.BottomStart)
                    .size(stackedAvatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.background,
                        shape = CircleShape
                    )
            )
        }

        ChannelAvatarImage(
            url = primaryUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .align(if (tertiaryUrl.isNullOrBlank()) Alignment.TopStart else Alignment.TopCenter)
                .size(stackedAvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (!secondaryUrl.isNullOrBlank()) {
                        Modifier
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.background,
                                shape = CircleShape
                            )
                    } else {
                        Modifier
                    }
                )
        )
    }
}
