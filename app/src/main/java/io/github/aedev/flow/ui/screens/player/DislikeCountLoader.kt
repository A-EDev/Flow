package io.github.aedev.flow.ui.screens.player

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Return YouTube Dislike's counts for the video being loaded. The same request answers both.
 *
 * Its like count is only adopted when the watch page gave none. That is common, not a sign of
 * hidden likes: the watch page often sends an `unset_like_count_entity_key` for videos whose likes
 * are visible, and the screen showed that as a flat zero.
 */
internal class DislikeCountLoader(
    private val uiState: MutableStateFlow<VideoPlayerUiState>,
    private val isLoadCurrent: (Long) -> Boolean,
    private val repository: YouTubeRepository,
    private val playerPreferences: PlayerPreferences,
    private val scope: CoroutineScope,
    private val networkDispatcher: CoroutineDispatcher,
) {
    fun start(load: LoadContext) {
        scope.launch(networkDispatcher) {
            if (playerPreferences.rytdEnabled.first()) {
                val counts = withTimeoutOrNull(RYD_TIMEOUT_MS) { repository.returnYouTubeDislikeCounts(load.videoId) }
                if (counts == null || !isLoadCurrent(load.token)) return@launch
                uiState.update { state ->
                    val cached = state.cachedVideo?.takeIf { it.id == load.videoId } ?: return@update state
                    val likes = counts.likes?.takeIf { it > 0L && cached.likeCount <= 0L }
                    state.copy(
                        dislikeCount = counts.dislikes ?: state.dislikeCount,
                        cachedVideo = likes?.let { cached.copy(likeCount = it) } ?: cached,
                    )
                }
            }
        }
    }

    private companion object {
        const val RYD_TIMEOUT_MS = 5_000L
    }
}
