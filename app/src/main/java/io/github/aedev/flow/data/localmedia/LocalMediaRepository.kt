package io.github.aedev.flow.data.localmedia

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocalMediaRepository"
private const val CHANGE_DEBOUNCE_MS = 750L
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The device's videos and songs, read once and kept current. While someone is watching, a
 * [ContentObserver] reports new, changed and deleted files; on Android 11 and later the MediaStore
 * generation lets a reopened screen reuse the last read when nothing changed in between.
 */
@Singleton
class LocalMediaRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private enum class Trigger { OPEN, CHANGE, REFRESH }

        private val scope = CoroutineScope(SupervisorJob() + PerformanceDispatcher.diskIO)
        private val store = LocalMediaStore(context.contentResolver)
        private val refreshes = MutableSharedFlow<Trigger>(extraBufferCapacity = 1)

        private val _refreshing = MutableStateFlow(false)

        /** True from a pull to refresh until the device has been read again. */
        val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

        @Volatile private var cached: LocalLibrary? = null

        @Volatile private var cachedGeneration: Long? = null

        @OptIn(FlowPreview::class)
        val library: Flow<LocalLibrary> =
            merge(mediaStoreChanges(), refreshes)
                .onStart { emit(Trigger.OPEN) }
                .debounce { if (it == Trigger.CHANGE) CHANGE_DEBOUNCE_MS else 0L }
                .map { trigger ->
                    try {
                        load(force = trigger != Trigger.OPEN)
                    } finally {
                        if (trigger == Trigger.REFRESH) _refreshing.value = false
                    }
                }.flowOn(PerformanceDispatcher.diskIO)
                .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

        /** Reads the device again, after a permission is granted or on pull to refresh. */
        fun refresh() {
            _refreshing.value = true
            if (!refreshes.tryEmit(Trigger.REFRESH)) _refreshing.value = false
        }

        private fun load(force: Boolean): LocalLibrary {
            val generation = currentGeneration()
            val previous = cached
            if (!force && previous != null && generation != null && generation == cachedGeneration) return previous
            val loaded =
                try {
                    LocalLibrary(videos = store.videos(), music = store.music())
                } catch (e: SecurityException) {
                    Log.w(TAG, "No access to the media library", e)
                    LocalLibrary(failed = true)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Reading the media library failed", e)
                    LocalLibrary(failed = true)
                }
            if (!loaded.failed) {
                cached = loaded
                cachedGeneration = generation
            }
            return loaded
        }

        private fun currentGeneration(): Long? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL) }.getOrNull()
            } else {
                null
            }

        private fun mediaStoreChanges(): Flow<Trigger> =
            callbackFlow {
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Trigger.CHANGE)
                        }
                    }
                val resolver = context.contentResolver
                resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
                resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
                awaitClose { resolver.unregisterContentObserver(observer) }
            }
    }
