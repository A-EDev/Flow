package io.github.aedev.flow.ui.screens.music

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.model.ArtistDetails
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.AddToPlaylistDialog
import io.github.aedev.flow.ui.components.MusicCollectionActionItem
import io.github.aedev.flow.ui.components.MusicCollectionQuickActionsSheet
import io.github.aedev.flow.ui.components.MusicQuickActionsSheet
import io.github.aedev.flow.ui.components.music.header.MusicSectionAction
import io.github.aedev.flow.ui.components.music.header.MusicSectionHeader
import io.github.aedev.flow.ui.components.music.item.MusicCardOverflowButton
import io.github.aedev.flow.ui.components.music.item.MusicCollectionCard
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.utils.formatViewCount

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistPage(
    artistDetails: ArtistDetails,
    downloadedTrackIds: Set<String> = emptySet(),
    insights: io.github.aedev.flow.data.recommendation.music.MusicArtistInsights? = null,
    knownRelatedArtistIds: Set<String> = emptySet(),
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onAlbumClick: (MusicPlaylist) -> Unit,
    onArtistClick: (String) -> Unit,
    onFollowClick: () -> Unit,
    onSeeAllClick: (String, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current

    val transparentAppBar by remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex == 0 && scrollState.firstVisibleItemScrollOffset < 100
        }
    }

    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var selectedCollection by remember { mutableStateOf<MusicCollectionActionItem?>(null) }
    var descriptionExpanded by remember { mutableStateOf(false) }

    if (showBottomSheet && selectedTrack != null) {
        MusicQuickActionsSheet(
            track = selectedTrack!!,
            onDismiss = { showBottomSheet = false },
            onViewArtist = {
                if (selectedTrack!!.channelId.isNotEmpty()) {
                    onArtistClick(selectedTrack!!.channelId)
                }
            },
            onViewAlbum = {
                selectedTrack!!.albumId?.let { albumId ->
                    onAlbumClick(
                        MusicPlaylist(
                            id = albumId,
                            title = selectedTrack!!.album ?: context.getString(R.string.album_label),
                            thumbnailUrl = "",
                        ),
                    )
                }
            },
            onShare = {
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, selectedTrack!!.title)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            context.getString(
                                R.string.share_message_template,
                                selectedTrack!!.title,
                                selectedTrack!!.artist,
                                selectedTrack!!.videoId,
                            ),
                        )
                    }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_song)))
            },
        )
    }

    selectedCollection?.let { collection ->
        MusicCollectionQuickActionsSheet(
            item = collection,
            onDismiss = { selectedCollection = null },
            onOpen = {
                onAlbumClick(
                    MusicPlaylist(
                        id = collection.id,
                        title = collection.title,
                        thumbnailUrl = collection.thumbnailUrl.orEmpty(),
                        author = collection.subtitle,
                    ),
                )
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    if (!transparentAppBar) {
                        Text(
                            text = artistDetails.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                            tint = if (transparentAppBar) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareText = "https://music.youtube.com/channel/${artistDetails.channelId}"
                        clipboardManager.setText(AnnotatedString(shareText))
                        Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = stringResource(R.string.share_link_cd),
                            tint = if (transparentAppBar) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = if (transparentAppBar) Color.Transparent else MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Header Image Setup
                item {
                    val imageUrl = artistDetails.thumbnailUrl.ifEmpty { artistDetails.bannerUrl }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Background Image
                        AsyncImage(
                            model =
                                ImageRequest
                                    .Builder(context)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .build(),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(400.dp)
                                    .blur(50.dp)
                                    .mediaQuery(
                                        comparator = androidx.compose.ui.layout.ContentScale.Crop,
                                    ),
                            contentScale = ContentScale.Crop,
                            alpha = 0.6f,
                        )

                        // Main Hero Image
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                        ) {
                            AsyncImage(
                                model =
                                    ImageRequest
                                        .Builder(context)
                                        .data(imageUrl)
                                        .crossfade(true)
                                        .build(),
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )

                            // Bottom Gradient for Text
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors =
                                                    listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.1f),
                                                        Color.Black.copy(alpha = 0.5f),
                                                        MaterialTheme.colorScheme.background,
                                                    ),
                                                startY = 0.5f,
                                            ),
                                        ),
                            )
                        }
                    }
                }

                // Artist Info & Controls
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .offset(y = (-32).dp)
                                .padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = artistDetails.name,
                            style =
                                MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-1).sp,
                                ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Subscribe Button
                            Button(
                                onClick = onFollowClick,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor =
                                            if (artistDetails.isSubscribed) {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                        contentColor =
                                            if (artistDetails.isSubscribed) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onPrimary
                                            },
                                    ),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                shape = RoundedCornerShape(32.dp),
                                modifier = Modifier.height(44.dp),
                            ) {
                                Text(
                                    text =
                                        if (artistDetails.isSubscribed) {
                                            stringResource(
                                                R.string.subscribed,
                                            )
                                        } else {
                                            stringResource(R.string.subscribe)
                                        },
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Shuffle Button
                            FilledIconButton(
                                onClick = {
                                    if (artistDetails.topTracks.isNotEmpty()) {
                                        onTrackClick(artistDetails.topTracks.random(), artistDetails.topTracks.shuffled())
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                                colors =
                                    IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                            ) {
                                Icon(Icons.Default.Shuffle, stringResource(R.string.shuffle))
                            }

                            // Play Button
                            FilledIconButton(
                                onClick = {
                                    if (artistDetails.topTracks.isNotEmpty()) {
                                        onTrackClick(artistDetails.topTracks.first(), artistDetails.topTracks)
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                                colors =
                                    IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                            ) {
                                Icon(Icons.Default.PlayArrow, stringResource(R.string.play))
                            }
                        }
                    }
                }

                // About Artist Section
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 24.dp)
                                .animateContentSize(),
                    ) {
                        if (artistDetails.subscriberCount > 0) {
                            Text(
                                text = stringResource(R.string.subscribers_count_template, formatViewCount(artistDetails.subscriberCount)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (artistDetails.description.isNotEmpty()) {
                            Text(
                                text = artistDetails.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { descriptionExpanded = !descriptionExpanded },
                            )
                        }
                    }
                }

                // Top Songs
                if (artistDetails.topTracks.isNotEmpty()) {
                    item {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.filter_popular),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            if (artistDetails.topTracks.size > 5 || artistDetails.topTracksBrowseId != null) {
                                TextButton(onClick = {
                                    artistDetails.topTracksBrowseId?.let { onSeeAllClick(it, artistDetails.topTracksParams) }
                                }) {
                                    Text(stringResource(R.string.action_view_all))
                                }
                            }
                        }
                    }

                    itemsIndexed(artistDetails.topTracks.take(5)) { index, track ->
                        MusicTrackItem(
                            track = track,
                            isDownloaded = downloadedTrackIds.contains(track.videoId),
                            leadingContent = {
                                Text(
                                    text = (index + 1).toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(32.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            },
                            onClick = { onTrackClick(track, artistDetails.topTracks) },
                            onLongClick = {
                                selectedTrack = track
                                showBottomSheet = true
                            },
                            onMenuClick = {
                                selectedTrack = track
                                showBottomSheet = true
                            },
                        )
                    }
                }

                // Your history — the local brain's record of this artist, zero network
                if (insights != null && insights.topTracks.isNotEmpty()) {
                    item {
                        MusicSectionHeader(title = stringResource(R.string.section_your_history))
                        Text(
                            text =
                                buildString {
                                    append(stringResource(R.string.artist_insights_played_times, insights.plays))
                                    if (insights.liked) {
                                        append(" · ")
                                        append(stringResource(R.string.artist_insights_liked))
                                    }
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                    itemsIndexed(insights.topTracks.take(5)) { index, track ->
                        MusicTrackItem(
                            track = track,
                            isDownloaded = downloadedTrackIds.contains(track.videoId),
                            leadingContent = {
                                Text(
                                    text = (index + 1).toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(32.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            },
                            onClick = { onTrackClick(track, insights.topTracks) },
                            onLongClick = {
                                selectedTrack = track
                                showBottomSheet = true
                            },
                            onMenuClick = {
                                selectedTrack = track
                                showBottomSheet = true
                            },
                        )
                    }
                }

                // Singles & EPs
                if (artistDetails.singles.isNotEmpty()) {
                    item {
                        MusicSectionHeader(
                            title = stringResource(R.string.section_singles),
                            action =
                                MusicSectionAction.SeeAll {
                                    artistDetails.singlesBrowseId?.let {
                                        onSeeAllClick(
                                            it,
                                            artistDetails.singlesParams,
                                        )
                                    }
                                },
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(artistDetails.singles) { album ->
                                MusicCollectionCard(
                                    title = album.title,
                                    subtitle = album.collectionSubtitle(showAuthor = false),
                                    thumbnailUrl = album.thumbnailUrl,
                                    thumbnailHeight = 160.dp,
                                    onClick = { onAlbumClick(album) },
                                    onLongClick = { selectedCollection = album.toCollectionActionItem(isAlbum = true) },
                                    trailingContent = {
                                        MusicCardOverflowButton(
                                            onClick = { selectedCollection = album.toCollectionActionItem(isAlbum = true) },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // Albums
                if (artistDetails.albums.isNotEmpty()) {
                    item {
                        MusicSectionHeader(
                            title = stringResource(R.string.filter_albums),
                            action =
                                MusicSectionAction.SeeAll {
                                    artistDetails.albumsBrowseId?.let {
                                        onSeeAllClick(
                                            it,
                                            artistDetails.albumsParams,
                                        )
                                    }
                                },
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(artistDetails.albums) { album ->
                                MusicCollectionCard(
                                    title = album.title,
                                    subtitle = album.collectionSubtitle(showAuthor = false),
                                    thumbnailUrl = album.thumbnailUrl,
                                    thumbnailHeight = 160.dp,
                                    onClick = { onAlbumClick(album) },
                                    onLongClick = { selectedCollection = album.toCollectionActionItem(isAlbum = true) },
                                    trailingContent = {
                                        MusicCardOverflowButton(
                                            onClick = { selectedCollection = album.toCollectionActionItem(isAlbum = true) },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // Videos
                if (artistDetails.videos.isNotEmpty()) {
                    item { MusicSectionHeader(title = stringResource(R.string.tab_videos)) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(artistDetails.videos, key = { it.videoId }) { video ->
                                MusicCollectionCard(
                                    title = video.title,
                                    subtitle = video.videoSubtitle(),
                                    thumbnailUrl = video.thumbnailUrl,
                                    thumbnailHeight = 124.dp,
                                    aspectRatio = 16f / 9f,
                                    onClick = { onTrackClick(video, listOf(video)) },
                                )
                            }
                        }
                    }
                }

                // Featured On
                if (artistDetails.featuredOn.isNotEmpty()) {
                    item { MusicSectionHeader(title = stringResource(R.string.section_featured_on)) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(artistDetails.featuredOn) { playlist ->
                                MusicCollectionCard(
                                    title = playlist.title,
                                    subtitle = playlist.collectionSubtitle(showAuthor = true),
                                    thumbnailUrl = playlist.thumbnailUrl,
                                    thumbnailHeight = 160.dp,
                                    onClick = { onAlbumClick(playlist) },
                                    onLongClick = { selectedCollection = playlist.toCollectionActionItem(isAlbum = false) },
                                    trailingContent = {
                                        MusicCardOverflowButton(
                                            onClick = { selectedCollection = playlist.toCollectionActionItem(isAlbum = false) },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // Related Artists
                if (artistDetails.relatedArtists.isNotEmpty()) {
                    item { MusicSectionHeader(title = stringResource(R.string.section_fans_also_like)) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(artistDetails.relatedArtists) { artist ->
                                MusicCollectionCard(
                                    title = artist.name,
                                    subtitle =
                                        stringResource(R.string.artist_known_related_badge)
                                            .takeIf { artist.channelId in knownRelatedArtistIds },
                                    thumbnailUrl = artist.thumbnailUrl,
                                    thumbnailHeight = 100.dp,
                                    shape = CircleShape,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    onClick = { onArtistClick(artist.channelId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.mediaQuery(comparator: androidx.compose.ui.layout.ContentScale): Modifier = this

private fun MusicPlaylist.toCollectionActionItem(isAlbum: Boolean): MusicCollectionActionItem =
    MusicCollectionActionItem(
        id = id,
        title = title,
        subtitle = author,
        thumbnailUrl = thumbnailUrl,
        description = if (trackCount > 0) "$trackCount tracks" else author,
        isAlbum = isAlbum,
    )

@Composable
private fun MusicPlaylist.collectionSubtitle(showAuthor: Boolean): String =
    when {
        showAuthor -> stringResource(R.string.subtitle_playlist_template, author)
        trackCount > 0 -> stringResource(R.string.tracks_count_template, trackCount)
        else -> stringResource(R.string.album_label)
    }

@Composable
private fun MusicTrack.videoSubtitle(): String {
    val viewsText = if (views > 0) formatViewCount(views) else null
    return if (viewsText != null) stringResource(R.string.artist_views_template, artist, viewsText) else artist
}
