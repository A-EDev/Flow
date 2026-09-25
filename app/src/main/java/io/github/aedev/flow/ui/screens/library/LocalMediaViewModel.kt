package io.github.aedev.flow.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.localmedia.LocalLibrary
import io.github.aedev.flow.data.localmedia.LocalMediaItem
import io.github.aedev.flow.data.localmedia.LocalMediaPreferences
import io.github.aedev.flow.data.localmedia.LocalMediaRepository
import io.github.aedev.flow.data.localmedia.LocalMediaSettings
import io.github.aedev.flow.data.localmedia.hiddenReason
import io.github.aedev.flow.ui.components.shared.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SHARING_TIMEOUT_MS = 5_000L

/** Whether a tab lists every file or its folders. */
enum class LocalView { ALL, FOLDERS }

/** What the viewer has chosen to look at, as opposed to what the device holds. */
data class LocalMediaSelection(
    val kind: MediaKind = MediaKind.Videos,
    val view: LocalView = LocalView.ALL,
    val openFolderId: String? = null,
    val filters: LocalFilters = LocalFilters(),
)

data class LocalMediaUiState(
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    val selection: LocalMediaSelection = LocalMediaSelection(),
    val settings: LocalMediaSettings = LocalMediaSettings(),
    /** The files the current tab shows after search, filters and sort, or the open folder's. */
    val items: List<LocalMediaItem> = emptyList(),
    val totalCount: Int = 0,
    val folders: List<LocalFolder> = emptyList(),
    val openFolder: LocalFolder? = null,
    val continueWatching: List<LocalMediaItem> = emptyList(),
    val hiddenCount: Int = 0,
    val playback: LocalPlayback = LocalPlayback(),
    val nowMs: Long = 0L,
)

@HiltViewModel
class LocalMediaViewModel
    @Inject
    constructor(
        private val repository: LocalMediaRepository,
        private val preferences: LocalMediaPreferences,
        viewHistory: ViewHistory,
    ) : ViewModel() {
        private val selection = MutableStateFlow(LocalMediaSelection())

        private val playback =
            viewHistory.getLocalHistoryFlow().map { entries ->
                LocalPlayback(
                    fraction = entries.filter { it.duration > 0 }.associate { it.videoId to (it.position.toFloat() / it.duration) },
                    lastPlayedMs = entries.associate { it.videoId to it.timestamp },
                )
            }

        val uiState: StateFlow<LocalMediaUiState> =
            combine(repository.library, preferences.settings, playback, selection) { library, settings, played, chosen ->
                buildState(library, settings, played, chosen, System.currentTimeMillis())
            }.flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARING_TIMEOUT_MS), LocalMediaUiState())

        val isRefreshing: StateFlow<Boolean> = repository.refreshing

        fun refresh() = repository.refresh()

        fun selectKind(kind: MediaKind) = selection.update { it.copy(kind = kind, openFolderId = null) }

        fun selectView(view: LocalView) = selection.update { it.copy(view = view, openFolderId = null) }

        fun openFolder(folderId: String?) = selection.update { it.copy(openFolderId = folderId) }

        fun updateFilters(change: (LocalFilters) -> LocalFilters) = selection.update { it.copy(filters = change(it.filters)) }

        fun hideFolder(folderId: String) {
            viewModelScope.launch { preferences.setFolderHidden(folderId, hidden = true) }
            selection.update { if (it.openFolderId == folderId) it.copy(openFolderId = null) else it }
        }

        fun setVideosAsGrid(grid: Boolean) {
            viewModelScope.launch { preferences.setVideosAsGrid(grid) }
        }
    }

/** Everything the screen shows, from what the device holds and what the viewer chose. Pure, for tests. */
internal fun buildState(
    library: LocalLibrary,
    settings: LocalMediaSettings,
    playback: LocalPlayback,
    selection: LocalMediaSelection,
    nowMs: Long,
): LocalMediaUiState {
    val all = if (selection.kind == MediaKind.Videos) library.videos else library.music
    val (hidden, shown) = all.partition { it.hiddenReason(settings) != null }
    val folders = shown.folders()
    val openFolder = selection.openFolderId?.let { id -> folders.firstOrNull { it.id == id } }
    val source = openFolder?.items ?: shown
    return LocalMediaUiState(
        isLoading = false,
        failed = library.failed,
        selection = selection,
        settings = settings,
        items = source.applyLocalFilters(selection.filters, playback, nowMs),
        totalCount = shown.size,
        folders = folders,
        openFolder = openFolder,
        continueWatching = if (selection.kind == MediaKind.Videos) shown.continueWatching(playback, nowMs) else emptyList(),
        hiddenCount = hidden.size,
        playback = playback,
        nowMs = nowMs,
    )
}
