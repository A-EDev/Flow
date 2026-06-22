package io.github.aedev.flow.ui.screens.recognition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.entity.RecognitionHistoryEntity
import io.github.aedev.flow.data.recognition.MusicRecognitionRepository
import io.github.aedev.flow.data.recognition.RecognitionHistoryRepository
import io.github.aedev.flow.data.recognition.RecognitionResult
import io.github.aedev.flow.data.recognition.RecognitionStatus
import io.github.aedev.flow.ui.screens.music.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecognitionViewModel @Inject constructor(
    private val recognitionRepository: MusicRecognitionRepository,
    private val historyRepository: RecognitionHistoryRepository
) : ViewModel() {

    val status: StateFlow<RecognitionStatus> = recognitionRepository.status

    private val searchQuery = MutableStateFlow("")
    val query: StateFlow<String> = searchQuery

    val history: StateFlow<List<RecognitionHistoryEntity>> =
        searchQuery
            .flatMapLatest { historyRepository.search(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var recognitionJob: Job? = null
    private var savedResult: RecognitionResult? = null

    fun hasRecordPermission(): Boolean = recognitionRepository.hasRecordPermission()

    fun startRecognition() {
        if (recognitionJob?.isActive == true) return
        recognitionJob = viewModelScope.launch { recognitionRepository.recognize() }
    }

    fun cancel() {
        recognitionJob?.cancel()
        recognitionJob = null
        recognitionRepository.reset()
    }

    /** On screen entry, clear a previous result but never interrupt a running recognition. */
    fun clearResultOnEnter() {
        when (status.value) {
            is RecognitionStatus.Success,
            is RecognitionStatus.NoMatch,
            is RecognitionStatus.Error -> recognitionRepository.reset()
            else -> Unit
        }
    }

    /** Persist a successful match once; repeat Success emissions for the same result are ignored. */
    fun saveToHistory(result: RecognitionResult) {
        if (savedResult === result) return
        savedResult = result
        viewModelScope.launch(Dispatchers.IO) { historyRepository.save(result) }
    }

    fun onQueryChange(value: String) {
        searchQuery.value = value
    }

    fun delete(id: Long) = viewModelScope.launch { historyRepository.delete(id) }

    fun clearHistory() = viewModelScope.launch { historyRepository.clearAll() }

    fun toggleLiked(item: RecognitionHistoryEntity) =
        viewModelScope.launch { historyRepository.setLiked(item.id, !item.liked) }

    companion object {
        fun searchQueryFor(title: String, artist: String): String =
            listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")

        /** Build a playable track from a recognized result that carries a YouTube id. */
        fun toMusicTrack(result: RecognitionResult): MusicTrack? {
            val videoId = result.youtubeVideoId?.takeIf { it.isNotBlank() } ?: return null
            return MusicTrack(
                videoId = videoId,
                title = result.title,
                artist = result.artist,
                thumbnailUrl = result.coverArtHqUrl ?: result.coverArtUrl ?: "",
                duration = 0,
                album = result.album.orEmpty()
            )
        }
    }
}
