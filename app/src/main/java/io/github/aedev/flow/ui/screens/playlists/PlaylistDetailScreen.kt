package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.dao.VideoDao
import io.github.aedev.flow.data.local.entity.VideoEntity
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.YouTubeMusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore

import androidx.hilt.navigation.compose.hiltViewModel

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onNavigateBack: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onPlayPlaylist: (List<Video>, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDownloadingPlaylist by viewModel.isDownloadingPlaylist.collectAsState()
    val playlistDownloadProgress by viewModel.playlistDownloadProgress.collectAsState()
    val currentDownloadingTitle by viewModel.currentDownloadingTitle.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { }, // Moved to header
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    val isUserCreatedPlaylist = uiState.isLocalPlaylist && !uiState.isSaved
                    if (!isUserCreatedPlaylist) {
                        IconButton(
                            onClick = {
                                if (uiState.isSaved) viewModel.unsaveFromLibrary()
                                else viewModel.saveToLibrary()
                            }
                        ) {
                            Icon(
                                imageVector = if (uiState.isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (uiState.isSaved) "Remove from library" else "Save to library",
                                tint = if (uiState.isSaved) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                    if (isUserCreatedPlaylist) {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_playlist_action)) },
                            onClick = {
                                showOptionsMenu = false
                                showEditDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_playlist_action)) },
                            onClick = {
                                showOptionsMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (uiState.isPrivate) stringResource(R.string.make_public_action) else stringResource(R.string.make_private_action)) },
                            onClick = {
                                showOptionsMenu = false
                                viewModel.togglePrivacy()
                            },
                            leadingIcon = {
                                Icon(
                                    if (uiState.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                                    null
                                )
                            }
                        )
                    }
                }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Playlist Header
            item {
                PlaylistHeader(
                    name = uiState.playlistName,
                    description = uiState.description,
                    videoCount = uiState.videos.size,
                    thumbnailUrl = uiState.thumbnailUrl.ifEmpty { uiState.videos.firstOrNull()?.thumbnailUrl ?: "" },
                    isPrivate = uiState.isPrivate,
                    onPlayAll = {
                        onPlayPlaylist(uiState.videos, 0)
                    },
                    onShuffle = {
                        val shuffled = uiState.videos.shuffled()
                        onPlayPlaylist(shuffled, 0)
                    },
                    onDownloadAll = { viewModel.downloadPlaylist() },
                    isDownloading = isDownloadingPlaylist,
                    downloadProgress = playlistDownloadProgress,
                    currentDownloadingTitle = currentDownloadingTitle
                )
            }

            // Video List
            if (uiState.videos.isEmpty()) {
                item {
                    EmptyPlaylistState(
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                itemsIndexed(
                    items = uiState.videos,
                    key = { _, video -> video.id }
                ) { index, video ->
                    PlaylistVideoItem(
                        video = video,
                        position = index + 1,
                        onVideoClick = { onPlayPlaylist(uiState.videos, index) },
                        onRemove = { viewModel.removeVideo(video.id) }
                    )
                }
            }
        }
    }

    // Edit Dialog
    if (showEditDialog && uiState.playlistName.isNotEmpty()) {
        EditPlaylistDialog(
            name = uiState.playlistName,
            description = uiState.description,
            onDismiss = { showEditDialog = false },
            onSave = { name, description ->
                viewModel.updatePlaylist(name, description)
                showEditDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text(stringResource(R.string.delete_playlist_dialog_title)) },
            text = { Text(stringResource(R.string.delete_playlist_dialog_text, uiState.playlistName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist()
                        showDeleteDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PlaylistHeader(
    name: String,
    description: String,
    videoCount: Int,
    thumbnailUrl: String,
    isPrivate: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onDownloadAll: () -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    currentDownloadingTitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Thumbnail - Hero style
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .aspectRatio(16f/9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Info Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Dynamic User Name Placeholder removed
            // Metadata Row
            Text(
                text = stringResource(
                    if (isPrivate) R.string.playlist_metadata_private_template else R.string.playlist_metadata_public_template,
                    videoCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.height(48.dp).weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play_all), fontWeight = FontWeight.Bold)
                }

                // Random (Dice) Shuffle Action
                Surface(
                    onClick = onShuffle,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Shuffle, stringResource(R.string.shuffle), modifier = Modifier.size(24.dp))
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    Surface(
                        onClick = { if (!isDownloading) onDownloadAll() },
                        shape = CircleShape,
                        color = if (isDownloading)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDownloading) Icons.Default.Downloading else Icons.Default.ArrowDownward,
                                contentDescription = if (isDownloading) "Downloading playlist" else "Download all",
                                tint = if (isDownloading) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (isDownloading && downloadProgress > 0f) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isDownloading && currentDownloadingTitle != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "Downloading: ${currentDownloadingTitle ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistVideoItem(
    video: Video,
    position: Int,
    onVideoClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onVideoClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag Handle removed as per request
        
        // Thumbnail
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Duration overlay
            video.duration?.let { duration ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Video Info
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Column {
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${formatViewCount(video.viewCount)} • ${video.uploadDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }

        // More Options
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_from_playlist_action)) },
                    onClick = {
                        onRemove()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyPlaylistState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PlaylistPlay,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Text(
            text = stringResource(R.string.playlist_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = stringResource(R.string.playlist_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun EditPlaylistDialog(
    name: String,
    description: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var editedName by remember { mutableStateOf(name) }
    var editedDescription by remember { mutableStateOf(description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, null) },
        title = { Text(stringResource(R.string.edit_playlist_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = editedDescription,
                    onValueChange = { editedDescription = it },
                    label = { Text(stringResource(R.string.playlist_description_optional)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(editedName, editedDescription) },
                enabled = editedName.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// Helper Functions
private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
        else -> String.format("%d:%02d", minutes, secs)
    }
}

private fun formatViewCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format("%.1f%s views", count / 1_000_000_000.0, "B")
        count >= 1_000_000 -> String.format("%.1f%s views", count / 1_000_000.0, "M")
        count >= 1_000 -> String.format("%.1f%s views", count / 1_000.0, "K")
        else -> "$count views"
    }
}

// ViewModel

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlaylistRepository,
    private val youTubeRepository: io.github.aedev.flow.data.repository.YouTubeRepository,
    private val videoDao: VideoDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])

    data class UiState(
        val playlistName: String = "",
        val description: String = "",
        val isPrivate: Boolean = false,
        val videos: List<Video> = emptyList(),
        val thumbnailUrl: String = "",
        val isLocalPlaylist: Boolean = false,
        val isSaved: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ── Playlist download state ──────────────────────────────────────────────
    private val _isDownloadingPlaylist = MutableStateFlow(false)
    val isDownloadingPlaylist: StateFlow<Boolean> = _isDownloadingPlaylist.asStateFlow()

    private val _playlistDownloadProgress = MutableStateFlow(0f)
    val playlistDownloadProgress: StateFlow<Float> = _playlistDownloadProgress.asStateFlow()

    private val _currentDownloadingTitle = MutableStateFlow<String?>(null)
    val currentDownloadingTitle: StateFlow<String?> = _currentDownloadingTitle.asStateFlow()

    init {
        loadPlaylist()
    }

    /**
     * Download every video in this playlist sequentially via [FlowDownloadService].
     * Fetches stream info (NewPipe) for each video, picks the best 720p-compatible MP4
     * stream with a paired AAC audio track, then hands off to the background service.
     */
    fun downloadPlaylist() {
        if (_isDownloadingPlaylist.value) return

        viewModelScope.launch {
            val videos = _uiState.value.videos
            if (videos.isEmpty()) {
                Toast.makeText(context, "Playlist is empty", Toast.LENGTH_SHORT).show()
                return@launch
            }

            _isDownloadingPlaylist.value = true
            _playlistDownloadProgress.value = 0f
            Toast.makeText(context, "Downloading ${videos.size} videos…", Toast.LENGTH_SHORT).show()

            var successCount = 0
            var processedCount = 0
            val total = videos.size

            val semaphore = Semaphore(2)

            for (video in videos) {
                semaphore.withPermit {
                    _currentDownloadingTitle.value = video.title
                    try {
                        val streamInfo = withContext(Dispatchers.IO) {
                            youTubeRepository.getVideoStreamInfo(video.id)
                        }

                        if (streamInfo != null) {
                            // ── Select best video stream (prefer MP4, 720p cap for bandwidth) ──
                            val videoOnlyStreams = streamInfo.videoOnlyStreams
                                ?.filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>()
                                ?: emptyList()
                            val combinedStreams = streamInfo.videoStreams
                                ?.filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>()
                                ?: emptyList()
                            val allAudio = streamInfo.audioStreams ?: emptyList()

                            fun isMp4(s: org.schabi.newpipe.extractor.stream.VideoStream): Boolean {
                                val mime = (s.format?.mimeType ?: "").lowercase()
                                val name = (s.format?.name ?: "").lowercase()
                                return mime.contains("mp4") || name.contains("mp4") || name.contains("mpeg")
                            }

                            fun isAacAudio(a: org.schabi.newpipe.extractor.stream.AudioStream): Boolean {
                                val mime = (a.format?.mimeType ?: "").lowercase()
                                val name = (a.format?.name ?: "").lowercase()
                                return !name.contains("opus") && !name.contains("webm") &&
                                       !mime.contains("opus") && !mime.contains("webm")
                            }

                            // Prefer video-only MP4 ≤720p for offline storage efficiency
                            val bestVideoOnly = videoOnlyStreams
                                .filter { isMp4(it) && it.height <= 720 }
                                .maxByOrNull { it.height }
                                ?: videoOnlyStreams.filter { isMp4(it) }.maxByOrNull { it.height }

                            val bestCombined = combinedStreams.filter { isMp4(it) }
                                .maxByOrNull { it.height }

                            val selectedStream = bestVideoOnly ?: bestCombined
                                ?: (videoOnlyStreams + combinedStreams).maxByOrNull { it.height }

                            if (selectedStream != null) {
                                val videoUrl = selectedStream.content ?: selectedStream.url
                                val audioUrl = if (selectedStream in videoOnlyStreams) {
                                    val aac = allAudio.filter { isAacAudio(it) }.maxByOrNull { it.averageBitrate }
                                    aac?.content ?: aac?.url
                                } else null

                                // Detect codec so the service picks the correct container
                                // (VP9 → .webm, AV1 → .mkv, H264 → .mp4).
                                // Without this, VP9 content written into a .mp4 filename
                                // causes MediaMuxer to fail during audio/video merging.
                                val videoCodec = io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
                                    .codecKeyFromStream(selectedStream)

                                val qualityLabel = "${selectedStream.height}p"
                                val fullVideo = video.copy(
                                    thumbnailUrl = video.thumbnailUrl.ifBlank {
                                        streamInfo.thumbnails?.maxByOrNull { it.height }?.url ?: ""
                                    }
                                )

                                if (videoUrl != null) {
                                    withContext(Dispatchers.Main) {
                                        io.github.aedev.flow.data.video.downloader.FlowDownloadService.startDownload(
                                            context = context,
                                            video = fullVideo,
                                            url = videoUrl,
                                            quality = qualityLabel,
                                            audioUrl = audioUrl,
                                            videoCodec = videoCodec
                                        )
                                        successCount++
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("PlaylistDetailVM", "Failed to queue download for ${video.title}", e)
                    }

                    processedCount++
                    _playlistDownloadProgress.value = processedCount.toFloat() / total
                    delay(400L)
                }
            }

            val msg = if (successCount > 0)
                "Queued $successCount/${total} downloads"
            else
                "Could not queue any downloads from this playlist"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

            _isDownloadingPlaylist.value = false
            _currentDownloadingTitle.value = null
            _playlistDownloadProgress.value = 0f
        }
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            // Try Local first
            val localInfo = repository.getPlaylistInfo(playlistId)
            if (localInfo != null) {
                val isSaved = repository.isExternalPlaylistSaved(playlistId)
                _uiState.update { it.copy(
                    playlistName = localInfo.name,
                    description = localInfo.description,
                    isPrivate = localInfo.isPrivate,
                    thumbnailUrl = localInfo.thumbnailUrl,
                    isLocalPlaylist = true,
                    isSaved = isSaved
                )}
                repository.getPlaylistVideosFlow(playlistId).collect { videos ->
                    _uiState.update { it.copy(videos = videos) }
                    val stubs = videos.filter { it.title.isEmpty() }.take(50)
                    if (stubs.isNotEmpty()) {
                        enrichMetadata(stubs)
                    }
                }
            } else {
                // Try Remote (YouTube)
                try {
                    val details = youTubeRepository.getPlaylistDetails(playlistId)
                    if (details != null) {
                        _uiState.update { it.copy(
                            playlistName = details.name,
                            description = details.description ?: "",
                            isPrivate = false,
                            videos = details.videos,
                            thumbnailUrl = details.thumbnailUrl,
                            isLocalPlaylist = false,
                            isSaved = false
                        )}
                    } else {
                        // Fallback to Music Service if regular fails (e.g. music playlist)
                        val musicDetails = YouTubeMusicService.fetchPlaylistDetails(playlistId)
                        if (musicDetails != null) {
                            val videos = musicDetails.tracks.map { track ->
                                Video(
                                    id = track.videoId,
                                    title = track.title,
                                    channelName = track.artist,
                                    channelId = track.channelId,
                                    thumbnailUrl = track.thumbnailUrl,
                                    duration = track.duration,
                                    viewCount = track.views ?: 0,
                                    uploadDate = "",
                                    isMusic = true
                                )
                            }
                            _uiState.update { it.copy(
                                playlistName = musicDetails.title,
                                description = musicDetails.description ?: "",
                                isPrivate = false,
                                videos = videos,
                                thumbnailUrl = musicDetails.thumbnailUrl,
                                isLocalPlaylist = false,
                                isSaved = false
                            )}
                        }
                    }
                } catch (e: Exception) {
                    // Error handling could be added here
                }
            }
        }
    }

    fun saveToLibrary() {
        viewModelScope.launch {
            val state = _uiState.value
            repository.saveExternalVideoPlaylist(
                id = playlistId,
                name = state.playlistName,
                description = state.description,
                thumbnailUrl = state.thumbnailUrl.ifEmpty {
                    state.videos.firstOrNull()?.thumbnailUrl ?: ""
                }
            )
            state.videos.forEachIndexed { index, video ->
                repository.addVideoToPlaylist(playlistId, video)
            }
            _uiState.update { it.copy(isLocalPlaylist = true, isSaved = true) }
            android.widget.Toast.makeText(context, "Playlist saved to library", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun unsaveFromLibrary() {
        viewModelScope.launch {
            repository.unsaveExternalPlaylist(playlistId)
            _uiState.update { it.copy(isLocalPlaylist = false, isSaved = false) }
            android.widget.Toast.makeText(context, "Playlist removed from library", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun removeVideo(videoId: String) {
        viewModelScope.launch {
            repository.removeVideoFromPlaylist(playlistId, videoId)
        }
    }

    fun updatePlaylist(name: String, description: String) {
        viewModelScope.launch {
            val currentInfo = repository.getPlaylistInfo(playlistId) ?: return@launch
            // We need to update the playlist info
            // For now, delete and recreate
            val videos = _uiState.value.videos
            repository.deletePlaylist(playlistId)
            repository.createPlaylist(playlistId, name, description, _uiState.value.isPrivate)
            // Re-add all videos
            videos.forEach { video ->
                repository.addVideoToPlaylist(playlistId, video)
            }
            _uiState.update { it.copy(
                playlistName = name,
                description = description
            )}
        }
    }

    fun togglePrivacy() {
        viewModelScope.launch {
            val newPrivacy = !_uiState.value.isPrivate
            updatePlaylist(_uiState.value.playlistName, _uiState.value.description)
            _uiState.update { it.copy(isPrivate = newPrivacy) }
        }
    }

    fun deletePlaylist() {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    private val enrichSemaphore = Semaphore(1)

    private fun enrichMetadata(stubs: List<Video>) {
        if (!enrichSemaphore.tryAcquire()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stubs.chunked(5).forEach { chunk ->
                    chunk.forEach { stub ->
                        try {
                            val enriched = youTubeRepository.getVideo(stub.id) ?: return@forEach
                            val e = VideoEntity.fromDomain(enriched)
                            videoDao.insertVideoOrIgnore(e)
                            videoDao.updateVideoMetadata(
                                id = e.id,
                                title = e.title,
                                channelName = e.channelName,
                                channelId = e.channelId,
                                thumbnailUrl = e.thumbnailUrl,
                                duration = e.duration,
                                viewCount = e.viewCount,
                                uploadDate = e.uploadDate,
                                description = e.description,
                                channelThumbnailUrl = e.channelThumbnailUrl
                            )
                        } catch (_: Exception) {}
                    }
                    delay(300L)
                }
            } finally {
                enrichSemaphore.release()
            }
        }
    }
}
