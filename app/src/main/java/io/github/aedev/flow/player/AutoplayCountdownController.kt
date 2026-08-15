package io.github.aedev.flow.player

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AutoplayCountdownController(
    private val scope: CoroutineScope,
    private val onElapsed: () -> Unit,
    private val log: (String) -> Unit = {},
) {
    private var job: Job? = null

    private val _state = MutableStateFlow(AutoplayCountdownState())
    val state: StateFlow<AutoplayCountdownState> = _state.asStateFlow()

    val isActive: Boolean
        get() = _state.value.isActive

    fun start(
        totalSeconds: Int,
        nextVideo: Video,
    ) {
        job?.cancel()
        job =
            scope.launch {
                var remaining = totalSeconds
                _state.value =
                    AutoplayCountdownState(
                        isActive = true,
                        secondsRemaining = remaining,
                        totalSeconds = totalSeconds,
                        nextVideoTitle = nextVideo.title,
                        nextVideoChannel = nextVideo.channelName,
                        nextVideoThumbnailUrl = nextVideo.thumbnailUrl,
                    )
                log("autoplay countdown start ${totalSeconds}s next=${nextVideo.id}")
                while (remaining > 0) {
                    delay(1000L)
                    remaining--
                    _state.value = _state.value.copy(secondsRemaining = remaining)
                }
                _state.value = AutoplayCountdownState()
                job = null
                log("autoplay countdown elapsed -> advance")
                onElapsed()
            }
    }

    /**
     * Cancels without advancing.
     *
     * @return whether a countdown was actually running, so a caller can skip the side effects that
     *   only make sense when the user interrupted one — releasing the advance wake lock, or
     *   advancing early on "play now".
     */
    fun stop(): Boolean {
        val wasActive = _state.value.isActive
        if (job == null && !wasActive) return false
        job?.cancel()
        job = null
        if (wasActive) _state.value = AutoplayCountdownState()
        return wasActive
    }
}
