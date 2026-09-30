package io.github.aedev.flow.data.localmedia

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "LocalLibraryLoader"

/** Where the loader reads the device's media from; MediaStore in the app, a fake in tests. */
internal interface LocalMediaSource {
    /** Reads everything; may throw. */
    suspend fun read(): LocalLibrary

    /** A number that changes whenever the media changed, or null when the platform has none. */
    fun generation(): Long?

    /** Emits whenever a file was added, changed or removed, for as long as it is collected. */
    fun changes(): Flow<Unit>
}

/**
 * Keeps the last read of [source] and reads again on open, on reported changes and on [refresh].
 * Every read runs under one lock and publishes into one state, so a refresh always ends, and clears
 * [refreshing], whether or not a screen is collecting when it finishes.
 */
@OptIn(FlowPreview::class)
internal class LocalLibraryLoader(
    private val source: LocalMediaSource,
    private val scope: CoroutineScope,
    changeDebounceMs: Long,
    stopTimeoutMs: Long,
) {
    private val lock = Mutex()
    private val latest = MutableStateFlow<LocalLibrary?>(null)
    private var lastGood: LocalLibrary? = null
    private var lastGoodGeneration: Long? = null
    private var refreshJob: Job? = null

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val library: Flow<LocalLibrary> =
        channelFlow {
            launch { reload(force = false) }
            launch { source.changes().debounce(changeDebounceMs).collect { reload(force = true) } }
            launch { latest.filterNotNull().collect { send(it) } }
            awaitClose()
        }.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMs, replayExpirationMillis = 0), replay = 1)

    fun refresh() {
        if (refreshJob?.isActive == true) return
        _refreshing.value = true
        refreshJob =
            scope.launch {
                try {
                    reload(force = true)
                } finally {
                    _refreshing.value = false
                }
            }
    }

    private suspend fun reload(force: Boolean) =
        lock.withLock {
            val generation = runCatching { source.generation() }.getOrNull()
            val previous = lastGood
            if (!force && previous != null && generation != null && generation == lastGoodGeneration) {
                latest.value = previous
                return@withLock
            }
            val loaded =
                try {
                    source.read()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Reading the media library failed", e)
                    LocalLibrary(failed = true)
                }
            if (!loaded.failed) {
                lastGood = loaded
                lastGoodGeneration = generation
            }
            latest.value = loaded
        }
}
