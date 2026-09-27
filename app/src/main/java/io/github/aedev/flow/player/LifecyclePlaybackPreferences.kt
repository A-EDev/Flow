package io.github.aedev.flow.player

import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The playback preferences the activity lifecycle callbacks have to read synchronously. */
data class LifecyclePlaybackSettings(
    val autoPipEnabled: Boolean = false,
    val backgroundPlayEnabled: Boolean = false,
    val shortsBackgroundPlay: Boolean = false,
    val shortsPipEnabled: Boolean = false,
    val clipboardLinkOpenEnabled: Boolean = true,
)

/**
 * Process-scoped snapshot of [LifecyclePlaybackSettings].
 *
 * Android 12+ relaunches every activity when the wallpaper changes (dynamic colours swap the app's
 * resources, and there is no opt-out). That relaunch runs onCreate through onStop inside a single
 * main-thread message, so an activity-scoped DataStore collector cannot emit inside the window and
 * onStop read the cold `false` defaults as "background playback disabled" — pausing the video and
 * releasing the media session mid-`startForegroundService` (#817). Surviving the activity keeps the
 * already-loaded values readable across the relaunch.
 */
@Singleton
class LifecyclePlaybackPreferences internal constructor(
    private val settingsFlow: Flow<LifecyclePlaybackSettings>,
) {
    @Inject
    constructor(playerPreferences: PlayerPreferences) : this(playerPreferences.lifecyclePlaybackSettings())

    @Volatile
    var settings: LifecyclePlaybackSettings = LifecyclePlaybackSettings()
        private set

    private val settingsLoaded = CompletableDeferred<Unit>()

    /** Returns the latest settings snapshot, suspending until the settings flow first emits. */
    suspend fun awaitLoadedSettings(): LifecyclePlaybackSettings {
        settingsLoaded.await()
        return settings
    }

    /** Keeps [settings] current for as long as [scope] lives; the last value outlives the scope. */
    fun observeIn(scope: CoroutineScope) {
        scope.launch {
            settingsFlow.collect {
                settings = it
                settingsLoaded.complete(Unit)
            }
        }
    }
}

private fun PlayerPreferences.lifecyclePlaybackSettings(): Flow<LifecyclePlaybackSettings> =
    combine(
        autoPipEnabled,
        backgroundPlayEnabled,
        shortsBackgroundPlay,
        shortsPipEnabled,
        clipboardLinkOpenEnabled,
    ) { autoPip, backgroundPlay, shortsBackgroundPlay, shortsPip, clipboardLinkOpen ->
        LifecyclePlaybackSettings(
            autoPipEnabled = autoPip,
            backgroundPlayEnabled = backgroundPlay,
            shortsBackgroundPlay = shortsBackgroundPlay,
            shortsPipEnabled = shortsPip,
            clipboardLinkOpenEnabled = clipboardLinkOpen,
        )
    }
