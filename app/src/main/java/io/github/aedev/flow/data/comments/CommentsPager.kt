package io.github.aedev.flow.data.comments

import android.util.Log
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.distinctByNonBlankKey
import io.github.aedev.flow.data.model.mergeDistinctByNonBlankKey
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.player.PlaybackStartupPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.Page

private const val TAG = "CommentsPager"

/** How long a comment fetch waits for playback to stop competing with it before giving up. */
private const val STARTUP_GATE_TIMEOUT_MS = 20_000L

/** What the pager needs to know about the surface's playback before it spends the network on comments. */
internal data class CommentsPlaybackState(
    val isPlaybackLoading: Boolean,
    val currentVideoId: String?,
) {
    companion object {
        /** For a surface whose playback never competes with the comment fetch. */
        val READY = CommentsPlaybackState(isPlaybackLoading = false, currentVideoId = null)
    }
}

/**
 * The comment list for one video: first page, further pages, and a comment's replies.
 *
 * The player and Shorts each had their own copy of this and drifted — only one of them deduplicated
 * by comment id, and neither guarded against a second request landing while the first was still in
 * flight, so re-opening the sheet fetched page one twice.
 *
 * One request per (video, page) is in flight at a time. A request for a different video supersedes
 * the one before it; a repeat of the request already running is dropped.
 *
 * [isCurrentVideo] is the owner's decision about whether the answer still belongs on screen, and it
 * is asked both before and after the fetch, because the fetch outlives a fast swipe to the next
 * video. [fetchTimeoutMs] bounds the fetch itself for a surface that wants one.
 */
internal class CommentsPager(
    private val repository: YouTubeRepository,
    private val scope: CoroutineScope,
    private val playbackState: Flow<CommentsPlaybackState> = flowOf(CommentsPlaybackState.READY),
    private val isCurrentVideo: (String) -> Boolean = { true },
    private val fetchTimeoutMs: Long? = null,
) {
    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var nextPage: Page? = null
    private var loadJob: Job? = null
    private var loadingVideoId: String? = null
    private val repliesInFlight = mutableSetOf<String>()

    /** Empties the list, for a video with no comments to fetch and for a player being torn down. */
    fun clear() {
        loadJob?.cancel()
        loadJob = null
        loadingVideoId = null
        nextPage = null
        _comments.value = emptyList()
        _isLoading.value = false
        _hasMore.value = false
        _isLoadingMore.value = false
    }

    fun load(videoId: String) {
        if (loadingVideoId == videoId && loadJob?.isActive == true) return
        loadJob?.cancel()
        loadingVideoId = videoId
        _comments.value = emptyList()
        nextPage = null
        _hasMore.value = false
        _isLoading.value = true
        loadJob =
            scope.launch {
                try {
                    withTimeoutOrNull(STARTUP_GATE_TIMEOUT_MS) {
                        playbackState.first { state ->
                            !PlaybackStartupPolicy.shouldDelaySecondaryContent(
                                isPlaybackLoading = state.isPlaybackLoading,
                                currentVideoId = state.currentVideoId,
                                requestedVideoId = videoId,
                            )
                        }
                    }
                    if (!isCurrentVideo(videoId)) return@launch
                    val page = fetch { repository.getComments(videoId) } ?: return@launch
                    if (!isCurrentVideo(videoId)) return@launch
                    _comments.value = page.first.distinctByNonBlankKey(Comment::id)
                    nextPage = page.second
                    _hasMore.value = page.second != null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading comments", e)
                } finally {
                    if (loadingVideoId == videoId) _isLoading.value = false
                }
            }
    }

    fun loadMore(videoId: String) {
        val page = nextPage ?: return
        if (_isLoadingMore.value) return
        scope.launch {
            _isLoadingMore.value = true
            try {
                val (newComments, newNextPage) = repository.getMoreComments(videoId, page)
                _comments.value = _comments.value.mergeDistinctByNonBlankKey(newComments, Comment::id)
                nextPage = newNextPage
                _hasMore.value = newNextPage != null
            } catch (e: Exception) {
                Log.e(TAG, "Error loading more comments", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun loadReplies(
        videoId: String,
        comment: Comment,
    ) = loadReplies(videoId, comment, append = false)

    fun loadMoreReplies(
        videoId: String,
        comment: Comment,
    ) = loadReplies(videoId, comment, append = true)

    private fun loadReplies(
        videoId: String,
        comment: Comment,
        append: Boolean,
    ) {
        val repliesPage = comment.repliesPage ?: return
        if (!repliesInFlight.add(comment.id)) return
        scope.launch {
            try {
                val url = "https://www.youtube.com/watch?v=$videoId"
                val (replies, nextRepliesPage) = repository.getCommentReplies(url, repliesPage)
                _comments.value =
                    _comments.value.map { current ->
                        if (current.id != comment.id) {
                            current
                        } else {
                            current.copy(
                                replies =
                                    if (append) {
                                        current.replies.mergeDistinctByNonBlankKey(replies, Comment::id)
                                    } else {
                                        replies.distinctByNonBlankKey(Comment::id)
                                    },
                                repliesPage = nextRepliesPage,
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading replies", e)
            } finally {
                repliesInFlight.remove(comment.id)
            }
        }
    }

    private suspend fun fetch(block: suspend () -> Pair<List<Comment>, Page?>): Pair<List<Comment>, Page?>? =
        if (fetchTimeoutMs == null) block() else withTimeoutOrNull(fetchTimeoutMs) { block() }
}
