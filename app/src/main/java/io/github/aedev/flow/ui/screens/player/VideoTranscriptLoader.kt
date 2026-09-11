package io.github.aedev.flow.ui.screens.player

import io.github.aedev.flow.data.transcript.TranscriptCue
import io.github.aedev.flow.data.transcript.TranscriptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the transcript surface shows: the lines, and whether a fetch is still running. */
data class TranscriptState(
    val cues: List<TranscriptCue> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * The transcript for one caption track, fetched when a surface first asks for it.
 *
 * A repeat ask for the same track is dropped, and the repository holds the parsed lines, so
 * reopening the panel or switching back to a track costs nothing.
 */
internal class VideoTranscriptLoader(
    private val repository: TranscriptRepository,
    private val scope: CoroutineScope,
    private val networkDispatcher: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(TranscriptState())
    val state: StateFlow<TranscriptState> = _state.asStateFlow()

    private var job: Job? = null
    private var loadedTrackUrl: String? = null

    fun load(trackUrl: String?) {
        if (trackUrl.isNullOrBlank()) {
            clear()
            return
        }
        if (loadedTrackUrl == trackUrl && (_state.value.cues.isNotEmpty() || _state.value.isLoading)) return
        loadedTrackUrl = trackUrl
        job?.cancel()
        _state.value = TranscriptState(isLoading = true)
        job =
            scope.launch(networkDispatcher) {
                try {
                    val cues = repository.cues(trackUrl)
                    if (loadedTrackUrl == trackUrl) _state.value = TranscriptState(cues = cues)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (loadedTrackUrl == trackUrl) _state.value = TranscriptState()
                }
            }
    }

    fun clear() {
        job?.cancel()
        job = null
        loadedTrackUrl = null
        _state.value = TranscriptState()
    }
}
