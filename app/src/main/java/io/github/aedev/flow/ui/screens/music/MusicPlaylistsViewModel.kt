package io.github.aedev.flow.ui.screens.music

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.entity.DownloadCollectionKind
import io.github.aedev.flow.data.local.entity.VideoEntity
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.data.music.model.toMusicTrack
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.video.BackgroundDownloadQueuer
import io.github.aedev.flow.data.video.downloader.collection.CollectionSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class MusicPlaylistsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playlistRepository: PlaylistRepository,
        private val downloadQueuer: BackgroundDownloadQueuer,
        private val youTubeRepository: YouTubeRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MusicPlaylistsUiState())
        val uiState: StateFlow<MusicPlaylistsUiState> = _uiState.asStateFlow()

        private val isEnrichingMusic = AtomicBoolean(false)

        init {
            loadPlaylists()
        }

        /**
         * Background-enriches imported music playlist/album stubs — tracks missing a title OR a
         * thumbnail (e.g. synced album tracks arrive with a title but no artwork). Mirrors the lazy
         * enrichment PlaylistDetailScreen does, but runs proactively so the library shows proper
         * titles/thumbnails without opening each one. Afterwards it recovers any blank album cover from
         * the first (now-enriched) track, so synced albums get a poster.
         */
        fun enrichMusicPlaylistStubs() {
            if (!isEnrichingMusic.compareAndSet(false, true)) return
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val db =
                        io.github.aedev.flow.data.local.AppDatabase
                            .getDatabase(context)
                    val videoDao = db.videoDao()
                    val playlistDao = db.playlistDao()
                    val stubs = playlistDao.getMusicPlaylistStubVideos()
                    if (stubs.isNotEmpty()) {
                        Log.d("MusicPlaylistsVM", "Enriching ${stubs.size} music playlist stubs")
                        stubs.chunked(5).forEach { chunk ->
                            chunk.forEach { stub ->
                                try {
                                    val video = youTubeRepository.getVideo(stub.id) ?: return@forEach
                                    val e = VideoEntity.fromDomain(video)
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
                                        timestamp = e.timestamp,
                                        description = e.description,
                                        channelThumbnailUrl = e.channelThumbnailUrl,
                                    )
                                } catch (e: Exception) {
                                    Log.w("MusicPlaylistsVM", "Failed to enrich stub ${stub.id}", e)
                                }
                            }
                            delay(300L)
                        }
                    }
                    recoverBlankAlbumCovers(playlistDao)
                } catch (e: Exception) {
                    Log.e("MusicPlaylistsVM", "enrichMusicPlaylistStubs failed", e)
                } finally {
                    isEnrichingMusic.set(false)
                }
            }
        }

        /** Seed a blank music playlist/album cover from its first track's thumbnail (post-enrichment). */
        private suspend fun recoverBlankAlbumCovers(playlistDao: io.github.aedev.flow.data.local.dao.PlaylistDao) {
            playlistDao.getMusicPlaylistsMissingThumbnail().forEach { id ->
                val thumb = playlistDao.getFirstVideoThumbnail(id)
                if (!thumb.isNullOrBlank()) playlistDao.updatePlaylistThumbnail(id, thumb)
            }
        }

        private fun loadPlaylists() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                launch {
                    playlistRepository.getUserCreatedMusicPlaylistsFlow().collect { playlists ->
                        _uiState.update { it.copy(playlists = playlists, isLoading = false) }
                    }
                }
                launch {
                    playlistRepository.getSavedMusicPlaylistsFlow().collect { saved ->
                        _uiState.update { it.copy(savedPlaylists = saved) }
                    }
                }
            }
        }

        fun createPlaylist(
            name: String,
            description: String,
        ) {
            viewModelScope.launch {
                val id = UUID.randomUUID().toString()
                playlistRepository.createPlaylist(
                    playlistId = id,
                    name = name,
                    description = description,
                    isPrivate = true,
                    isMusic = true,
                )
            }
        }

        fun deletePlaylist(playlistId: String) {
            viewModelScope.launch {
                playlistRepository.deletePlaylist(playlistId)
                Toast.makeText(context, context.getString(R.string.toast_playlist_deleted), Toast.LENGTH_SHORT).show()
            }
        }

        fun renamePlaylist(
            playlistId: String,
            newName: String,
        ) {
            viewModelScope.launch {
                playlistRepository.updatePlaylistName(playlistId, newName)
                Toast.makeText(context, context.getString(R.string.toast_playlist_renamed), Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * Downloads one of your music playlists as one item in its own folder. The queue keeps
         * going after this screen closes; a second download picks up only what is new.
         */
        fun downloadPlaylist(playlist: PlaylistInfo) {
            viewModelScope.launch {
                val videos = playlistRepository.getPlaylistVideosFlow(playlist.id).first()
                if (videos.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.ui_playlist_empty), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.toast_starting_playlist_download, playlist.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                downloadQueuer.queueCollectionSongs(
                    spec =
                        CollectionSpec(
                            id = playlist.id,
                            kind = DownloadCollectionKind.MUSIC_PLAYLIST,
                            title = playlist.name,
                            thumbnailUrl = playlist.thumbnailUrl.takeIf { it.isNotBlank() },
                        ),
                    tracks = videos.map { it.toMusicTrack() },
                    complete = true,
                )
            }
        }
    }

data class MusicPlaylistsUiState(
    val playlists: List<PlaylistInfo> = emptyList(),
    val savedPlaylists: List<PlaylistInfo> = emptyList(),
    val isLoading: Boolean = false,
)
