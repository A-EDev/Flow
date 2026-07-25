package io.github.aedev.flow.player.shorts

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal class ShortsPreferenceObservers(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private var scope: CoroutineScope? = null

    val isRunning: Boolean
        get() = scope != null

    fun start(
        preferredAudioLanguage: Flow<String>,
        playbackMode: Flow<String>,
        playbackSpeed: Flow<Float>,
        onPreferredAudioLanguage: (String) -> Unit,
        onPlaybackMode: (String) -> Unit,
        onPlaybackSpeed: (Float) -> Unit,
    ) {
        stop()
        val observerScope = CoroutineScope(SupervisorJob() + dispatcher)
        scope = observerScope
        observerScope.launch {
            preferredAudioLanguage.collect(onPreferredAudioLanguage)
        }
        observerScope.launch {
            playbackMode.collect(onPlaybackMode)
        }
        observerScope.launch {
            playbackSpeed.collect(onPlaybackSpeed)
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }
}
