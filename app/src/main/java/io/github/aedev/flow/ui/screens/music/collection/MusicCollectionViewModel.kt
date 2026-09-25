package io.github.aedev.flow.ui.screens.music.collection

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.engagement.LikedMediaUseCase
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.entity.PlaylistEntity
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.data.playlist.PlaylistFileCodec
import io.github.aedev.flow.data.playlist.PlaylistTransfer
import io.github.aedev.flow.data.recommendation.music.DailyMixStore
import io.github.aedev.flow.data.recommendation.music.graph.MusicGraphStore
import io.github.aedev.flow.data.video.BackgroundDownloadQueuer
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionUndo
import io.github.aedev.flow.ui.screens.music.MusicViewModel
import io.github.aedev.flow.ui.screens.music.saveMusicCollection
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import io.github.aedev.flow.data.music.PlaylistRepository as MusicLibrary

private const val REMOTE_TIMEOUT_MS = 12_000L
private const val TAG = "MusicCollectionVM"
private const val MAX_PAGES = 60
private const val SHARING_TIMEOUT_MS = 5_000L

/**
 * One album or playlist page. Each page has its own instance, keyed by the route's id, so Back
 * returns to a page as it was left, a slow load can never land on another page, and nothing here
 * keeps the music home loading behind it.
 */
@HiltViewModel
class MusicCollectionViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
        private val playlists: PlaylistRepository,
        private val dailyMixes: DailyMixStore,
        private val musicGraph: MusicGraphStore,
        private val likes: LikedVideosRepository,
        private val musicLibrary: MusicLibrary,
        private val likedMedia: LikedMediaUseCase,
        private val preferences: PlayerPreferences,
        private val transfer: PlaylistTransfer,
        private val downloads: BackgroundDownloadQueuer,
    ) : ViewModel() {
        val collectionId: String = checkNotNull(savedStateHandle[MUSIC_COLLECTION_ARG])

        private val _state = MutableStateFlow(MusicCollectionUiState())
        val state: StateFlow<MusicCollectionUiState> = _state.asStateFlow()

        private val _messages = Channel<CollectionMessage>(Channel.BUFFERED)

        /** Confirmations for the snackbar. */
        val messages: Flow<CollectionMessage> = _messages.receiveAsFlow()

        /** Your music playlists, for "Add all to playlist". */
        val mergeTargets: StateFlow<List<PlaylistInfo>> =
            playlists
                .getUserCreatedMusicPlaylistsFlow()
                .map { list -> list.filterNot { it.id == collectionId } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARING_TIMEOUT_MS), emptyList())

        val songSearch = MusicSongSearch(viewModelScope)

        /** The order the songs are shown in, remembered for this collection. */
        val sortOrder: StateFlow<MusicSortOrder> =
            preferences
                .playlistSortOrder(collectionId)
                .map { MusicSortOrder.fromStorage(it) ?: MusicSortOrder.COLLECTION }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARING_TIMEOUT_MS), MusicSortOrder.COLLECTION)

        fun setSortOrder(order: MusicSortOrder) {
            viewModelScope.launch { preferences.setPlaylistSortOrder(collectionId, order.storageValue) }
        }

        private var loadJob: Job? = null
        private var moreJob: Job? = null
        private var graphRecorded = false

        /** How far "Download all" has got through this collection, or null while none runs. */
        val downloadProgress: StateFlow<Float?> =
            downloads.batches
                .map { batches -> batches[collectionId]?.takeUnless { it.isFinished }?.let { it.processed.toFloat() / it.total } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARING_TIMEOUT_MS), null)

        init {
            load()
            viewModelScope.launch {
                downloads.batches.collect { batches ->
                    batches.values.filter { it.isFinished && it.collectionId.startsWith(collectionId) }.forEach { batch ->
                        downloads.clearBatch(batch.collectionId)
                        _messages.send(
                            if (batch.queued > 0) {
                                CollectionMessage(
                                    pluralRes = R.plurals.songs_download_queued,
                                    count = batch.queued,
                                    args = listOf(batch.queued),
                                )
                            } else {
                                CollectionMessage(stringRes = R.string.songs_download_nothing_new)
                            },
                        )
                    }
                }
            }
        }

        /** Downloads every song here, all pages of them, or just [songs]; it carries on after the page closes. */
        fun download(songs: List<MusicTrack>? = null) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                val tracks = songs ?: loadAll()?.tracks.orEmpty()
                if (tracks.isEmpty()) return@launch
                _messages.send(CollectionMessage(pluralRes = R.plurals.songs_downloading, count = tracks.size, args = listOf(tracks.size)))
                // A selection runs as its own batch, so it never blocks or borrows the whole page's progress.
                val batchId = if (songs == null) collectionId else "$collectionId:${System.currentTimeMillis()}"
                downloads.queueSongs(batchId, tracks)
            }
        }

        fun retry() {
            _state.update { it.copy(isLoading = true, failed = false) }
            load()
        }

        /** The next page of a YouTube playlist; a failure keeps the token so the next call retries. */
        fun loadMore() {
            val details = _state.value.details ?: return
            val token = details.continuation ?: return
            if (moreJob?.isActive == true) return
            moreJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    _state.update { it.copy(isLoadingMore = true, moreFailed = false) }
                    val (tracks, next) = YouTubeMusicService.fetchPlaylistContinuation(details.id, token)
                    _state.update { state ->
                        val current = state.details ?: return@update state
                        if (tracks.isEmpty() && next == null) {
                            state.copy(isLoadingMore = false, moreFailed = true)
                        } else {
                            state.copy(details = current.appending(tracks, next), isLoadingMore = false)
                        }
                    }
                }
        }

        /** Every page of a YouTube playlist, for saving, downloading or queueing the whole of it. */
        suspend fun loadAll(): PlaylistDetails? {
            moreJob?.join()
            var details = _state.value.details ?: return null
            var pages = 0
            while (details.continuation != null && pages < MAX_PAGES) {
                val (tracks, next) = YouTubeMusicService.fetchPlaylistContinuation(details.id, details.continuation.orEmpty())
                if (tracks.isEmpty() && next == null) break
                details = details.appending(tracks, next)
                pages++
            }
            _state.update { it.copy(details = details) }
            return details
        }

        /** Saves the whole collection to the library, or takes a saved one out. Your own are never saved. */
        fun toggleSaved() {
            val state = _state.value
            if (state.isOwn || state.kind == MusicCollectionKind.DAILY_MIX || state.kind == MusicCollectionKind.LIKED) return
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                runCatching {
                    if (state.isSaved) {
                        playlists.unsaveExternalPlaylist(collectionId)
                        _state.update { it.copy(isSaved = false, kind = remoteKind(collectionId)) }
                        R.string.toast_removed_playlist_from_library
                    } else {
                        val complete = loadAll() ?: return@launch
                        playlists.saveMusicCollection(complete)
                        _state.update { it.copy(isSaved = true, kind = MusicCollectionKind.SAVED) }
                        R.string.toast_saved_playlist_to_music_library
                    }
                }.onFailure { Log.w(TAG, "Saving $collectionId failed", it) }
                    .getOrDefault(R.string.toast_failed_to_save_playlist)
                    .let { _messages.send(CollectionMessage(stringRes = it)) }
            }
        }

        /** Takes songs out of your playlist, or unlikes them from Liked music, with an Undo. */
        fun removeTracks(videoIds: Set<String>) {
            if (videoIds.isEmpty()) return
            val kind = _state.value.kind
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val message =
                    when (kind) {
                        MusicCollectionKind.OWN -> {
                            val removed = playlists.takeVideosFromPlaylist(collectionId, videoIds)
                            if (removed.isEmpty()) return@launch
                            CollectionMessage(
                                pluralRes = R.plurals.songs_removed_from_playlist,
                                count = removed.size,
                                args = listOf(removed.size),
                                undo = QuickActionUndo.PlaylistRemoval(removed),
                            )
                        }

                        MusicCollectionKind.LIKED -> {
                            val removed = likedMedia.unlike(videoIds)
                            if (removed.isEmpty()) return@launch
                            CollectionMessage(
                                pluralRes = R.plurals.songs_unliked,
                                count = removed.size,
                                args = listOf(removed.size),
                                undo = QuickActionUndo.Unlike(removed),
                            )
                        }

                        else -> {
                            return@launch
                        }
                    }
                _messages.send(message)
            }
        }

        fun updateDetails(
            name: String,
            description: String,
        ) {
            if (!_state.value.isOwn) return
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val isPrivate = playlists.getPlaylistEntity(collectionId)?.isPrivate ?: true
                playlists.updatePlaylistMetadata(collectionId, name, description, isPrivate)
            }
        }

        /** Deletes your playlist; the page closes itself once it is gone. */
        fun delete() {
            if (!_state.value.isOwn) return
            _state.update { it.copy(isDeleted = true) }
            loadJob?.cancel()
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playlists.deletePlaylist(collectionId)
                _messages.send(CollectionMessage(stringRes = R.string.toast_playlist_deleted))
            }
        }

        /** A Daily Mix changes as you listen; this keeps today's songs as a playlist of your own. */
        fun saveAsPlaylist() {
            val details = _state.value.details ?: return
            if (_state.value.kind != MusicCollectionKind.DAILY_MIX || details.tracks.isEmpty()) return
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val saved =
                    runCatching {
                        playlists.importPlaylist(
                            details.title,
                            details.description.orEmpty(),
                            details.tracks.map { it.toStoredVideo() },
                            isMusic = true,
                        )
                    }.isSuccess
                _messages.send(
                    if (saved) {
                        CollectionMessage(stringRes = R.string.daily_mix_saved_as_playlist, args = listOf(details.title))
                    } else {
                        CollectionMessage(stringRes = R.string.toast_failed_to_save_playlist)
                    },
                )
            }
        }

        /** The name offered when the viewer saves this collection as a file. */
        val exportFileName: String get() =
            PlaylistFileCodec.fileName(
                _state.value.details
                    ?.title
                    .orEmpty(),
            )

        /** Saves every song, all pages of them, as a music playlist file in [target]. */
        fun exportTo(target: Uri) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                val details = loadAll()
                val saved =
                    details != null &&
                        transfer.writeTo(target, details.title, details.description.orEmpty(), fileVideos(details), isMusic = true)
                _messages.send(CollectionMessage(stringRes = if (saved) R.string.playlist_exported else R.string.playlist_export_failed))
            }
        }

        /** This collection as a file another app can read, or null when it could not be written. */
        suspend fun shareableFile(): Uri? {
            val details = loadAll() ?: return null
            return transfer.shareableCopy(details.title, details.description.orEmpty(), fileVideos(details), isMusic = true)
        }

        private fun fileVideos(details: PlaylistDetails) =
            details.tracks.map { track -> track.toStoredVideo().copy(addedAtInPlaylist = _state.value.addedAt[track.videoId]) }

        fun reorder(orderedVideoIds: List<String>) {
            if (!_state.value.isOwn) return
            viewModelScope.launch(PerformanceDispatcher.diskIO) { playlists.reorderVideosInPlaylist(collectionId, orderedVideoIds) }
        }

        /** Adds a song found in the catalogue to your playlist; the list shows it as the database does. */
        fun addTrack(track: MusicTrack) {
            if (!_state.value.isOwn) return
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val added = runCatching { playlists.addVideoToPlaylist(collectionId, track.toStoredVideo()) }.isSuccess
                _messages.send(
                    CollectionMessage(stringRes = if (added) R.string.toast_added_to_playlist else R.string.toast_failed_to_add_track),
                )
            }
        }

        /** Adds [songs] to another of your playlists; null means every song here, all pages of it. */
        fun addTo(
            target: PlaylistInfo,
            songs: List<MusicTrack>? = null,
        ) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                val tracks = songs ?: loadAll()?.tracks.orEmpty()
                if (tracks.isEmpty()) return@launch
                val added = runCatching { playlists.addVideosToPlaylist(target.id, tracks.map { it.toStoredVideo() }) }.isSuccess
                _messages.send(
                    if (added) {
                        CollectionMessage(
                            pluralRes = R.plurals.merge_playlist_success,
                            count = tracks.size,
                            args = listOf(tracks.size, target.name),
                        )
                    } else {
                        CollectionMessage(stringRes = R.string.toast_failed_to_merge_playlist)
                    },
                )
            }
        }

        private fun load() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch(PerformanceDispatcher.diskIO) {
                    when {
                        collectionId == PlaylistRepository.LIKED_MUSIC_ID -> observeLiked()
                        collectionId.startsWith(MusicViewModel.DAILY_MIX_ID_PREFIX) -> loadDailyMix()
                        else -> loadStoredOrRemote()
                    }
                }
        }

        /** Liked music, live: newest like first, with the details the favorites store kept. */
        private suspend fun observeLiked() {
            val title = context.getString(R.string.liked_music_playlist)
            combine(likes.getLikedMusicFlow(), musicLibrary.favorites) { liked, favorites ->
                val tracks = likedMusicTracks(liked, favorites)
                PlaylistDetails(
                    id = collectionId,
                    title = title,
                    thumbnailUrl = tracks.firstOrNull()?.thumbnailUrl.orEmpty(),
                    author = "",
                    trackCount = tracks.size,
                    tracks = tracks,
                ) to liked.associate { it.videoId to it.likedAt }
            }.collect { (details, likedAt) ->
                _state.update { it.copy(kind = MusicCollectionKind.LIKED, details = details, addedAt = likedAt, isLoading = false) }
            }
        }

        private suspend fun loadStoredOrRemote() {
            val stored = playlists.getPlaylistEntity(collectionId)
            when {
                stored?.isUserCreated == true -> observeStored(MusicCollectionKind.OWN)
                stored != null -> loadSaved()
                else -> loadRemote()
            }
        }

        /** Your own playlist, live from the database: edits, adds and removals show at once. */
        private suspend fun observeStored(kind: MusicCollectionKind) {
            combine(
                playlists.observePlaylistEntity(collectionId),
                playlists.getPlaylistVideosWithAddedAtFlow(collectionId),
            ) { entity, videos -> entity?.let { storedCollection(it, videos, context.getString(R.string.you)) } }
                .collect { collection ->
                    if (collection == null) {
                        _state.update { it.copy(isLoading = false, failed = true) }
                    } else {
                        _state.update {
                            it.copy(
                                kind = kind,
                                details = collection.details,
                                addedAt = collection.addedAt,
                                isLoading = false,
                                failed = false,
                            )
                        }
                    }
                }
        }

        /**
         * A saved album or playlist opens from the saved copy at once, so it works offline, then
         * refreshes from YouTube once. A complete refresh replaces the saved songs.
         */
        private suspend fun loadSaved() {
            val entity = playlists.getPlaylistEntity(collectionId) ?: return loadRemote()
            val videos = playlists.getPlaylistVideosWithAddedAtFlow(collectionId).first()
            val saved = storedCollection(entity, videos, author = "")
            _state.update {
                it.copy(
                    kind = MusicCollectionKind.SAVED,
                    details = saved.details,
                    addedAt = saved.addedAt,
                    isLoading = false,
                    isSaved = true,
                )
            }
            val remote = fetchRemote() ?: return
            _state.update { it.copy(details = remote) }
            recordInGraph(remote)
            if (remote.continuation == null && remote.tracks.isNotEmpty()) {
                runCatching { playlists.syncSavedPlaylistVideos(collectionId, remote.tracks.map { it.toStoredVideo() }) }
                    .onFailure { Log.w(TAG, "Saved copy of $collectionId not refreshed", it) }
            }
        }

        private suspend fun loadRemote() {
            val remote = fetchRemote()
            if (remote == null) {
                _state.update { it.copy(isLoading = false, failed = true) }
                return
            }
            _state.update {
                it.copy(kind = remoteKind(collectionId), details = remote, isLoading = false, failed = false)
            }
            recordInGraph(remote)
        }

        private fun loadDailyMix() {
            val index = collectionId.removePrefix(MusicViewModel.DAILY_MIX_ID_PREFIX).toIntOrNull()
            val section = index?.let { dailyMixes.mixes.value.getOrNull(it) }
            if (section == null || section.tracks.isEmpty()) {
                _state.update { it.copy(isLoading = false, failed = true) }
                return
            }
            val details =
                PlaylistDetails(
                    id = collectionId,
                    title = section.title,
                    thumbnailUrl = section.thumbnailUrl ?: section.tracks.first().thumbnailUrl,
                    author = "",
                    trackCount = section.tracks.size,
                    description = context.getString(R.string.daily_mix_page_description),
                    tracks = section.tracks,
                )
            _state.update { it.copy(kind = MusicCollectionKind.DAILY_MIX, details = details, isLoading = false) }
        }

        private suspend fun fetchRemote(): PlaylistDetails? =
            runCatching {
                withTimeoutOrNull(REMOTE_TIMEOUT_MS) { YouTubeMusicService.fetchPlaylistDetails(collectionId) }
            }.onFailure { Log.w(TAG, "Remote load failed for $collectionId", it) }
                .getOrNull()

        /** Albums and curated playlists teach the music graph, once per page. */
        private suspend fun recordInGraph(details: PlaylistDetails) {
            if (graphRecorded) return
            graphRecorded = true
            runCatching {
                when {
                    collectionId.startsWith("MPREb") -> musicGraph.recordAlbum(details)
                    !collectionId.startsWith("RD") && !collectionId.startsWith("OLAK") -> musicGraph.recordPlaylist(details)
                }
            }.onFailure { Log.w(TAG, "Music graph write failed for $collectionId", it) }
        }
    }

/** The route argument the page reads its id from. */
const val MUSIC_COLLECTION_ARG = "playlistId"

/** What kind of collection a page shows, which decides what it can do. */
enum class MusicCollectionKind {
    ALBUM,
    PLAYLIST,
    OWN,
    SAVED,
    DAILY_MIX,
    LIKED,
}

data class MusicCollectionUiState(
    val kind: MusicCollectionKind? = null,
    val details: PlaylistDetails? = null,
    /** When each song was added, for stored collections; empty for YouTube pages. */
    val addedAt: Map<String, Long> = emptyMap(),
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    val isLoadingMore: Boolean = false,
    val moreFailed: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
) {
    val isOwn: Boolean get() = kind == MusicCollectionKind.OWN

    /** Your own playlist or Liked music, where songs can be taken out. */
    val canRemove: Boolean get() = kind == MusicCollectionKind.OWN || kind == MusicCollectionKind.LIKED

    /** Collections kept on the device share and save as a file; YouTube's own share their link. */
    val sharesAsFile: Boolean get() =
        kind == MusicCollectionKind.OWN || kind == MusicCollectionKind.LIKED ||
            kind == MusicCollectionKind.DAILY_MIX

    val canExport: Boolean get() = kind == MusicCollectionKind.OWN || kind == MusicCollectionKind.SAVED || kind == MusicCollectionKind.LIKED
}

internal fun remoteKind(id: String): MusicCollectionKind =
    if (id.startsWith("MPREb_") || id.startsWith("FEmusic_library_privately_owned_release_")) {
        MusicCollectionKind.ALBUM
    } else {
        MusicCollectionKind.PLAYLIST
    }

internal fun PlaylistDetails.appending(
    more: List<MusicTrack>,
    next: String?,
): PlaylistDetails {
    val known = tracks.mapTo(HashSet()) { it.videoId }
    val added = more.filter { known.add(it.videoId) }
    return copy(tracks = tracks + added, continuation = next, trackCount = maxOf(trackCount, tracks.size + added.size))
}

internal class StoredCollection(
    val details: PlaylistDetails,
    val addedAt: Map<String, Long>,
)

internal fun storedCollection(
    entity: PlaylistEntity,
    videos: List<io.github.aedev.flow.data.model.Video>,
    author: String,
): StoredCollection {
    val tracks = videos.map { it.toCollectionTrack() }
    return StoredCollection(
        details =
            PlaylistDetails(
                id = entity.id,
                title = entity.name,
                thumbnailUrl = entity.thumbnailUrl.ifBlank { tracks.firstOrNull()?.thumbnailUrl.orEmpty() },
                author = author,
                trackCount = tracks.size,
                description = entity.description.ifBlank { null },
                tracks = tracks,
            ),
        addedAt = videos.mapNotNull { video -> video.addedAtInPlaylist?.let { video.id to it } }.toMap(),
    )
}
