package io.github.aedev.flow.ui.screens.player

import android.util.Log
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.innertube.pages.VideoDescriptionPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "VideoDescriptionLoader"

/**
 * The watch page description, fetched the first time a surface asks for it.
 *
 * Nothing here runs on the playback path: the metadata line under the title keeps using what the
 * load already resolved, and a video whose description is never opened costs no request. Repeat
 * asks for the same video are dropped, and the repository holds the response so the comment
 * section does not fetch it again.
 */
internal class VideoDescriptionLoader(
    private val repository: YouTubeRepository,
    private val scope: CoroutineScope,
    private val networkDispatcher: CoroutineDispatcher,
) {
    private val _description = MutableStateFlow<VideoDescriptionPage?>(null)
    val description: StateFlow<VideoDescriptionPage?> = _description.asStateFlow()

    private var job: Job? = null
    private var loadedVideoId: String? = null

    fun load(videoId: String) {
        if (videoId.isBlank()) return
        if (loadedVideoId == videoId) return
        loadedVideoId = videoId
        job?.cancel()
        _description.value = null
        job =
            scope.launch(networkDispatcher) {
                try {
                    val page = repository.getVideoDescription(videoId)
                    if (loadedVideoId == videoId) _description.value = page
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "Description unavailable for $videoId: ${e.message}")
                }
            }
    }

    fun clear() {
        job?.cancel()
        job = null
        loadedVideoId = null
        _description.value = null
    }
}
