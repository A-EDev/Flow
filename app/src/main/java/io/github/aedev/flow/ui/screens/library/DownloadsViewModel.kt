package io.github.aedev.flow.ui.screens.library

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.video.DownloadProgressUpdate
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.data.video.downloader.FlowDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import io.github.aedev.flow.data.music.DownloadManager as MusicDownloadManager

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloadsViewModel
    @Inject
    constructor(
        private val videoDownloadManager: VideoDownloadManager,
        private val musicDownloadManager: MusicDownloadManager,
        @param:ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DownloadsUiState())
        val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

        // Hidden from the lists while their deletion runs; each id drops out once the row is gone.
        private val pendingDeleteIds = MutableStateFlow<Set<String>>(emptySet())
        private val allVideos = MutableStateFlow<List<DownloadedVideo>>(emptyList())
        private val allTracks = MutableStateFlow<List<DownloadedTrack>>(emptyList())
        private val query = MutableStateFlow("")
        private val sort = MutableStateFlow(DownloadSort.NEWEST)

        // Each pull to refresh reads the lists again, so a file deleted outside Flow drops out and
        // the free space is measured again, not only when the database changes.
        private val refreshTick = MutableStateFlow(0)

        init {
            observeDownloads()
            if (!videoDownloadManager.hasScannedThisSession) rescan()
        }

        fun setQuery(value: String) {
            query.value = value
            _uiState.update { it.copy(query = value) }
        }

        fun setSort(value: DownloadSort) {
            sort.value = value
            _uiState.update { it.copy(sort = value) }
        }

        private fun observeDownloads() {
            viewModelScope.launch {
                combine(refreshTick.flatMapLatest { musicDownloadManager.downloadedTracks }, pendingDeleteIds) { tracks, pending ->
                    tracks.filter { it.track.videoId !in pending }
                }.collect { allTracks.value = it }
            }
            viewModelScope.launch {
                combine(refreshTick.flatMapLatest { videoDownloadManager.downloadedVideos }, pendingDeleteIds) { videos, pending ->
                    videos.filter { it.video.id !in pending }
                }.collect { allVideos.value = it }
            }
            viewModelScope.launch {
                combine(allVideos, allTracks, query, sort) { videos, tracks, text, order ->
                    ShownDownloads(
                        videos = videos.filterAndSort(text, order),
                        tracks = tracks.filterAndSortTracks(text, order),
                        videoCount = videos.size,
                        musicCount = tracks.size,
                    )
                }.collect { shown ->
                    _uiState.update {
                        it.copy(
                            downloadedVideos = shown.videos,
                            downloadedMusic = shown.tracks,
                            totalVideoCount = shown.videoCount,
                            totalMusicCount = shown.musicCount,
                        )
                    }
                }
            }
            viewModelScope.launch {
                combine(allVideos, allTracks, refreshTick) { videos, tracks, _ ->
                    videos.sumOf { it.fileSize } to
                        tracks.sumOf { it.fileSize }
                }.collect { (videoBytes, musicBytes) ->
                    val free = withContext(Dispatchers.IO) { freeBytes() }
                    _uiState.update { it.copy(storage = DownloadStorage(videoBytes, musicBytes, free)) }
                }
            }
            viewModelScope.launch {
                combine(videoDownloadManager.allDownloads, pendingDeleteIds) { downloads, pending -> downloads to pending }
                    .collect { (downloads, pending) ->
                        val present = downloads.mapTo(HashSet()) { it.download.videoId }
                        if (!present.containsAll(pending)) pendingDeleteIds.update { it.intersect(present) }
                        val incomplete =
                            downloads.filter { it.download.videoId !in pending && it.overallStatus != DownloadItemStatus.COMPLETED }
                        _uiState.update { state ->
                            val activeIds = incomplete.mapTo(HashSet()) { it.download.videoId }
                            state.copy(
                                incompleteVideoDownloads = incomplete.filterNot { it.isAudioOnly },
                                incompleteMusicDownloads = incomplete.filter { it.isAudioOnly },
                                mergingVideoIds = state.mergingVideoIds.intersect(activeIds),
                                progress = state.progress.filterKeys { it in activeIds },
                            )
                        }
                    }
            }
            viewModelScope.launch {
                videoDownloadManager.progressUpdates.collect { update ->
                    _uiState.update { state ->
                        state.copy(
                            progress = state.progress + (update.videoId to update),
                            mergingVideoIds =
                                if (update.isMerging) state.mergingVideoIds + update.videoId else state.mergingVideoIds - update.videoId,
                        )
                    }
                }
            }
        }

        private fun freeBytes(): Long =
            runCatching { StatFs((appContext.getExternalFilesDir(null) ?: appContext.filesDir).path).availableBytes }
                .getOrDefault(0L)

        /**
         * A finished download is deleted here; one still running is cancelled through the service,
         * which waits for its writes to stop before deleting, so the file can't be recreated.
         */
        fun deleteVideoDownload(videoId: String) {
            pendingDeleteIds.update { it + videoId }
            viewModelScope.launch {
                val download = videoDownloadManager.getDownloadWithItems(videoId)
                if (download?.overallStatus == DownloadItemStatus.COMPLETED) {
                    videoDownloadManager.deleteDownload(videoId)
                } else {
                    FlowDownloadService.cancelDownload(appContext, videoId)
                }
            }
        }

        fun deleteMusicDownload(videoId: String) {
            pendingDeleteIds.update { it + videoId }
            viewModelScope.launch { musicDownloadManager.deleteDownload(videoId) }
        }

        fun deleteDownloads(ids: Set<String>) {
            val musicIds = allTracks.value.mapTo(HashSet()) { it.track.videoId }
            ids.forEach { id -> if (id in musicIds) deleteMusicDownload(id) else deleteVideoDownload(id) }
        }

        fun pauseVideoDownload(videoId: String) = FlowDownloadService.pauseDownload(appContext, videoId)

        fun resumeVideoDownload(videoId: String) = FlowDownloadService.resumeDownload(appContext, videoId)

        fun retryVideoDownload(videoId: String) = FlowDownloadService.retryDownload(appContext, videoId)

        /** Cancels the incomplete downloads of one tab; the service deletes each once it has stopped. */
        fun removeIncompleteDownloads(audioOnly: Boolean) {
            val state = _uiState.value
            val ids = (if (audioOnly) state.incompleteMusicDownloads else state.incompleteVideoDownloads).map { it.download.videoId }
            if (ids.isEmpty()) return
            pendingDeleteIds.update { it + ids }
            ids.forEach { videoId -> FlowDownloadService.cancelDownload(appContext, videoId) }
        }

        fun rescan() {
            viewModelScope.launch {
                _uiState.update { it.copy(isScanning = true) }
                videoDownloadManager.scanAndRecoverDownloads()
                refreshTick.update { it + 1 }
                _uiState.update { it.copy(isScanning = false) }
            }
        }

        private data class ShownDownloads(
            val videos: List<DownloadedVideo>,
            val tracks: List<DownloadedTrack>,
            val videoCount: Int,
            val musicCount: Int,
        )
    }

data class DownloadsUiState(
    val downloadedVideos: List<DownloadedVideo> = emptyList(),
    val downloadedMusic: List<DownloadedTrack> = emptyList(),
    val totalVideoCount: Int = 0,
    val totalMusicCount: Int = 0,
    val incompleteVideoDownloads: List<DownloadWithItems> = emptyList(),
    val incompleteMusicDownloads: List<DownloadWithItems> = emptyList(),
    val progress: Map<String, DownloadProgressUpdate> = emptyMap(),
    val mergingVideoIds: Set<String> = emptySet(),
    val storage: DownloadStorage = DownloadStorage(),
    val query: String = "",
    val sort: DownloadSort = DownloadSort.NEWEST,
    val isScanning: Boolean = false,
)
