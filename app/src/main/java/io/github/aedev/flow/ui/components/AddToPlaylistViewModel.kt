package io.github.aedev.flow.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SHARING_TIMEOUT_MS = 5_000L

@HiltViewModel
class AddToPlaylistViewModel
    @Inject
    constructor(
        private val repository: PlaylistRepository,
    ) : ViewModel() {
        private val _savedIds = MutableStateFlow<Set<String>>(emptySet())
        val savedIds: StateFlow<Set<String>> = _savedIds.asStateFlow()

        private val _membershipLoaded = MutableStateFlow(false)
        val membershipLoaded: StateFlow<Boolean> = _membershipLoaded.asStateFlow()

        private var loadedVideoId: String? = null

        val playlists: StateFlow<List<PlaylistInfo>> =
            repository
                .getAllPlaylistsFlow()
                .map { all -> all.filter { it.id != PlaylistRepository.WATCH_LATER_ID } }
                .distinctUntilChanged()
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARING_TIMEOUT_MS),
                    emptyList(),
                )

        val watchLaterVideos: StateFlow<List<Video>> =
            repository
                .getWatchLaterVideosFlow()
                .distinctUntilChanged()
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARING_TIMEOUT_MS),
                    emptyList(),
                )

        fun loadMembership(videoId: String) {
            if (loadedVideoId == videoId) return
            loadedVideoId = videoId
            _membershipLoaded.value = false
            viewModelScope.launch {
                _savedIds.value = repository.getPlaylistIdsForVideo(videoId).toSet()
                _membershipLoaded.value = true
            }
        }

        fun toggle(
            video: Video,
            collectionId: String,
        ) {
            if (!_membershipLoaded.value) return
            val wasSaved = collectionId in _savedIds.value
            _savedIds.update { if (wasSaved) it - collectionId else it + collectionId }
            viewModelScope.launch {
                runCatching {
                    when {
                        collectionId == PlaylistRepository.WATCH_LATER_ID && wasSaved -> {
                            repository.removeFromWatchLater(video.id)
                        }

                        collectionId == PlaylistRepository.WATCH_LATER_ID -> {
                            repository.addToWatchLater(video)
                        }

                        wasSaved -> {
                            repository.removeVideoFromPlaylist(collectionId, video.id)
                        }

                        else -> {
                            repository.addVideoToPlaylist(collectionId, video)
                        }
                    }
                }.onFailure {
                    _savedIds.update { ids -> if (wasSaved) ids + collectionId else ids - collectionId }
                }
            }
        }

        fun createAndAdd(
            video: Video,
            name: String,
            description: String,
        ) {
            viewModelScope.launch {
                val playlistId = System.currentTimeMillis().toString()
                repository.createPlaylist(playlistId, name, description, true)
                repository.addVideoToPlaylist(playlistId, video)
                _savedIds.update { it + playlistId }
            }
        }
    }
