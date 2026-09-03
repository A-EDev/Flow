package io.github.aedev.flow.ui.screens.music

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.ArtistDetails
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.recommendation.music.MusicArtistInsights
import io.github.aedev.flow.ui.components.music.common.MusicSegmentedGap
import io.github.aedev.flow.ui.components.music.common.musicSegmentShape
import io.github.aedev.flow.ui.components.music.common.rememberMusicCollectionColorScheme
import io.github.aedev.flow.ui.components.music.detail.ArtistBio
import io.github.aedev.flow.ui.components.music.detail.ArtistHero
import io.github.aedev.flow.ui.components.music.detail.MusicHeroTopBar
import io.github.aedev.flow.ui.components.music.header.MusicSectionAction
import io.github.aedev.flow.ui.components.music.header.MusicSectionHeader
import io.github.aedev.flow.ui.components.music.item.MusicCollectionCard
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.components.music.section.MusicArtistShelf
import io.github.aedev.flow.ui.components.music.section.MusicCollectionShelf
import io.github.aedev.flow.ui.components.music.section.MusicShelf
import io.github.aedev.flow.ui.components.music.sheet.MusicCollectionActionItem
import io.github.aedev.flow.ui.components.music.sheet.MusicCollectionQuickActionsSheet
import io.github.aedev.flow.ui.components.music.sheet.MusicQuickActionsSheet
import io.github.aedev.flow.ui.components.music.sheet.toCollectionActionItem
import io.github.aedev.flow.ui.theme.Dimensions
import io.github.aedev.flow.utils.formatViewCount

private const val TOP_TRACKS_SHOWN = 5
private val VideoCardHeight = 124.dp
private const val VIDEO_ASPECT_RATIO = 16f / 9f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPage(
    artistDetails: ArtistDetails,
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onAlbumClick: (MusicPlaylist) -> Unit,
    onArtistClick: (String) -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloadedTrackIds: Set<String> = emptySet(),
    insights: MusicArtistInsights? = null,
    knownRelatedArtistIds: Set<String> = emptySet(),
    onSeeAllClick: (String, String?) -> Unit = { _, _ -> },
) {
    val scrollState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val showBarTitle by remember {
        derivedStateOf { scrollState.firstVisibleItemIndex > 0 }
    }

    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var selectedCollection by remember { mutableStateOf<MusicCollectionActionItem?>(null) }
    var descriptionExpanded by remember { mutableStateOf(false) }

    fun showTrackMenu(track: MusicTrack) {
        selectedTrack = track
        showBottomSheet = true
    }

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

    val pageScheme = rememberMusicCollectionColorScheme(artistDetails.thumbnailUrl.ifEmpty { artistDetails.bannerUrl })

    MaterialTheme(colorScheme = pageScheme) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                MusicHeroTopBar(
                    title = artistDetails.name,
                    showTitle = showBarTitle,
                    onBack = onBackClick,
                ) { iconColors ->
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString("https://music.youtube.com/channel/${artistDetails.channelId}"))
                            Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
                        },
                        colors = iconColors,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = stringResource(R.string.share_link_cd),
                        )
                    }
                }
            },
        ) { _ ->
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "hero") {
                    ArtistHero(
                        artist = artistDetails,
                        onFollowClick = onFollowClick,
                        onShuffleClick = {
                            if (artistDetails.topTracks.isNotEmpty()) {
                                onTrackClick(artistDetails.topTracks.random(), artistDetails.topTracks.shuffled())
                            }
                        },
                        onPlayClick = {
                            if (artistDetails.topTracks.isNotEmpty()) {
                                onTrackClick(artistDetails.topTracks.first(), artistDetails.topTracks)
                            }
                        },
                    )
                }

                if (artistDetails.description.isNotEmpty()) {
                    item(key = "bio") {
                        ArtistBio(
                            description = artistDetails.description,
                            isExpanded = descriptionExpanded,
                            onToggleExpanded = { descriptionExpanded = !descriptionExpanded },
                        )
                    }
                }

                if (artistDetails.topTracks.isNotEmpty()) {
                    item(key = "top_songs_header") {
                        val browseId = artistDetails.topTracksBrowseId
                        MusicSectionHeader(
                            title = stringResource(R.string.filter_popular),
                            action =
                                if (browseId != null) {
                                    MusicSectionAction.SeeAll { onSeeAllClick(browseId, artistDetails.topTracksParams) }
                                } else {
                                    null
                                },
                        )
                    }
                    segmentedTracks(
                        id = "top_songs",
                        tracks = artistDetails.topTracks.take(TOP_TRACKS_SHOWN),
                        queue = artistDetails.topTracks,
                        downloadedTrackIds = downloadedTrackIds,
                        onTrackClick = onTrackClick,
                        onTrackMenu = ::showTrackMenu,
                    )
                }

                if (insights != null && insights.topTracks.isNotEmpty()) {
                    item(key = "history_header") {
                        MusicSectionHeader(
                            title = stringResource(R.string.section_your_history),
                            subtitle = insights.summaryLine(),
                        )
                    }
                    segmentedTracks(
                        id = "history",
                        tracks = insights.topTracks.take(TOP_TRACKS_SHOWN),
                        queue = insights.topTracks,
                        downloadedTrackIds = downloadedTrackIds,
                        onTrackClick = onTrackClick,
                        onTrackMenu = ::showTrackMenu,
                    )
                }

                if (artistDetails.singles.isNotEmpty()) {
                    item(key = "singles") {
                        MusicCollectionShelf(
                            title = stringResource(R.string.section_singles),
                            collections = artistDetails.singles,
                            keyNamespace = "singles",
                            onCollectionClick = onAlbumClick,
                            onCollectionMenu = { selectedCollection = it.toCollectionActionItem(isAlbum = true) },
                            action = seeAllAction(artistDetails.singlesBrowseId, artistDetails.singlesParams, onSeeAllClick),
                            collectionSubtitle = { it.collectionSubtitle(showAuthor = false) },
                        )
                    }
                }

                if (artistDetails.albums.isNotEmpty()) {
                    item(key = "albums") {
                        MusicCollectionShelf(
                            title = stringResource(R.string.filter_albums),
                            collections = artistDetails.albums,
                            keyNamespace = "albums",
                            onCollectionClick = onAlbumClick,
                            onCollectionMenu = { selectedCollection = it.toCollectionActionItem(isAlbum = true) },
                            action = seeAllAction(artistDetails.albumsBrowseId, artistDetails.albumsParams, onSeeAllClick),
                            collectionSubtitle = { it.collectionSubtitle(showAuthor = false) },
                        )
                    }
                }

                if (artistDetails.videos.isNotEmpty()) {
                    item(key = "videos") {
                        MusicShelf(
                            title = stringResource(R.string.tab_videos),
                            items = artistDetails.videos,
                            key = { "videos:${it.videoId}" },
                        ) { video ->
                            MusicCollectionCard(
                                title = video.title,
                                subtitle = video.videoSubtitle(),
                                thumbnailUrl = video.thumbnailUrl,
                                thumbnailHeight = VideoCardHeight,
                                aspectRatio = VIDEO_ASPECT_RATIO,
                                mediaId = video.videoId,
                                onClick = { onTrackClick(video, listOf(video)) },
                            )
                        }
                    }
                }

                if (artistDetails.featuredOn.isNotEmpty()) {
                    item(key = "featured_on") {
                        MusicCollectionShelf(
                            title = stringResource(R.string.section_featured_on),
                            collections = artistDetails.featuredOn,
                            keyNamespace = "featured_on",
                            onCollectionClick = onAlbumClick,
                            onCollectionMenu = { selectedCollection = it.toCollectionActionItem(isAlbum = false) },
                            collectionSubtitle = { it.collectionSubtitle(showAuthor = true) },
                        )
                    }
                }

                if (artistDetails.relatedArtists.isNotEmpty()) {
                    item(key = "related_artists") {
                        MusicArtistShelf(
                            title = stringResource(R.string.section_fans_also_like),
                            artists = artistDetails.relatedArtists,
                            key = { "related:${it.channelId}" },
                            name = { it.name },
                            thumbnailUrl = { it.thumbnailUrl },
                            subtitle = { artist ->
                                stringResource(R.string.artist_known_related_badge)
                                    .takeIf { artist.channelId in knownRelatedArtistIds }
                            },
                            onArtistClick = { onArtistClick(it.channelId) },
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.segmentedTracks(
    id: String,
    tracks: List<MusicTrack>,
    queue: List<MusicTrack>,
    downloadedTrackIds: Set<String>,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
) {
    itemsIndexed(tracks, key = { index, track -> "$id:$index:${track.videoId}" }) { index, track ->
        MusicTrackItem(
            track = track,
            index = index + 1,
            shape = musicSegmentShape(index = index, count = tracks.size),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            isDownloaded = downloadedTrackIds.contains(track.videoId),
            onClick = { onTrackClick(track, queue) },
            onLongClick = { onTrackMenu(track) },
            onMenuClick = { onTrackMenu(track) },
            modifier =
                Modifier
                    .padding(horizontal = Dimensions.ContentPaddingHorizontal)
                    .padding(bottom = MusicSegmentedGap),
        )
    }
}

private fun seeAllAction(
    browseId: String?,
    params: String?,
    onSeeAllClick: (String, String?) -> Unit,
): MusicSectionAction? = browseId?.let { MusicSectionAction.SeeAll { onSeeAllClick(it, params) } }

@Composable
private fun MusicArtistInsights.summaryLine(): String {
    val plays = stringResource(R.string.artist_insights_played_times, this.plays)
    return if (liked) {
        "$plays ${stringResource(R.string.metadata_separator)} ${stringResource(R.string.artist_insights_liked)}"
    } else {
        plays
    }
}

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
