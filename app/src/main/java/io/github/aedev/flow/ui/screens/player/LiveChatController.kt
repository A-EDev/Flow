package io.github.aedev.flow.ui.screens.player

import io.github.aedev.flow.data.model.LiveChatMessage
import io.github.aedev.flow.data.repository.LiveChatRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The live chat transcript and the loop that drips it in.
 *
 * Availability is probed as soon as a live video starts, because the whole chat affordance is
 * hidden until it resolves. The polling loop behind it is gated on [setPanelVisible]: a chat
 * nobody is looking at is a network round trip and a state write every few hundred milliseconds
 * for the length of a stream, and the player keeps its surfaces composed while they are hidden.
 * Leaving the panel keeps the transcript, so reopening it is instant.
 */
internal class LiveChatController(
    private val repository: LiveChatRepository,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private val _messages = MutableStateFlow<List<LiveChatMessage>>(emptyList())
    val messages: StateFlow<List<LiveChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private var videoId: String? = null
    private var panelVisible = false
    private var probeJob: Job? = null
    private var dripJob: Job? = null
    private var pendingContinuation: String? = null
    private val seen = LinkedHashSet<String>()

    fun start(videoId: String) {
        if (this.videoId == videoId) return
        stop()
        this.videoId = videoId
        seen.clear()
        _messages.value = emptyList()
        _isLoading.value = true
        _isAvailable.value = false

        probeJob =
            scope.launch(dispatcher) {
                val seed = repository.initialContinuation(videoId)
                if (this@LiveChatController.videoId != videoId) return@launch
                if (seed == null) {
                    _isAvailable.value = false
                    _isLoading.value = false
                    return@launch
                }
                _isAvailable.value = true
                _isLoading.value = false
                pendingContinuation = seed
                if (panelVisible) startDrip(videoId)
            }
    }

    fun stop() {
        probeJob?.cancel()
        probeJob = null
        dripJob?.cancel()
        dripJob = null
        videoId = null
        pendingContinuation = null
    }

    fun setPanelVisible(visible: Boolean) {
        if (panelVisible == visible) return
        panelVisible = visible
        if (!visible) {
            dripJob?.cancel()
            dripJob = null
            return
        }
        videoId?.takeIf { _isAvailable.value }?.let(::startDrip)
    }

    private fun startDrip(videoId: String) {
        if (dripJob?.isActive == true) return

        dripJob =
            scope.launch(dispatcher) {
                var continuation = pendingContinuation ?: repository.initialContinuation(videoId)
                pendingContinuation = null
                var consecutiveFailures = 0
                var isInitialPage = true
                while (isActive && continuation != null && this@LiveChatController.videoId == videoId) {
                    val page = repository.poll(continuation)
                    if (page == null) {
                        consecutiveFailures++
                        if (consecutiveFailures >= MAX_FAILURES) break
                        delay(RETRY_MS)
                        continue
                    }
                    consecutiveFailures = 0

                    val fresh = page.messages.filter { seen.add(it.id) }
                    val visibleFresh =
                        if (isInitialPage) {
                            isInitialPage = false
                            fresh.takeLast(INITIAL_BACKFILL_MESSAGES)
                        } else {
                            fresh
                        }
                    while (seen.size > MAX_SEEN_IDS) {
                        val it = seen.iterator()
                        if (it.hasNext()) {
                            it.next()
                            it.remove()
                        } else {
                            break
                        }
                    }
                    continuation = page.nextContinuation

                    if (visibleFresh.isEmpty()) {
                        delay(page.timeoutMs)
                    } else {
                        val interval =
                            (page.timeoutMs / visibleFresh.size)
                                .coerceIn(MIN_DRIP_MS, MAX_DRIP_MS)
                        var consumed = 0L
                        for (msg in visibleFresh) {
                            if (!isActive || this@LiveChatController.videoId != videoId) break
                            append(msg)
                            delay(interval)
                            consumed += interval
                        }
                        if (consumed < page.timeoutMs) delay(page.timeoutMs - consumed)
                    }
                }
            }
    }

    private fun append(message: LiveChatMessage) {
        _messages.update { current ->
            val combined = current + message
            if (combined.size > MAX_MESSAGES) combined.takeLast(MAX_MESSAGES) else combined
        }
    }

    private companion object {
        const val MAX_MESSAGES = 200
        const val MAX_SEEN_IDS = 1500
        const val RETRY_MS = 3000L
        const val MAX_FAILURES = 6
        const val INITIAL_BACKFILL_MESSAGES = 12
        const val MIN_DRIP_MS = 90L
        const val MAX_DRIP_MS = 250L
    }
}
