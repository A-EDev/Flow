package io.github.aedev.flow.data.localmedia

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANGE_DEBOUNCE_MS = 750L
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The device's videos and songs, read once and kept current, titled by their own files' tags. While
 * someone is watching, a [ContentObserver] reports new, changed and deleted files; on Android 11 and
 * later the MediaStore generation lets a reopened screen reuse the last read when nothing changed.
 */
@Singleton
class LocalMediaRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        embeddedTags: LocalEmbeddedTags,
    ) {
        private val loader =
            LocalLibraryLoader(
                source = MediaStoreSource(context, embeddedTags),
                scope = CoroutineScope(SupervisorJob() + PerformanceDispatcher.diskIO),
                changeDebounceMs = CHANGE_DEBOUNCE_MS,
                stopTimeoutMs = STOP_TIMEOUT_MS,
            )

        /** True from a pull to refresh until the device has been read again. */
        val refreshing: StateFlow<Boolean> = loader.refreshing

        val library: Flow<LocalLibrary> = loader.library

        /** Reads the device again, after a permission is granted or on pull to refresh. */
        fun refresh() = loader.refresh()
    }

private class MediaStoreSource(
    private val context: Context,
    private val embeddedTags: LocalEmbeddedTags,
) : LocalMediaSource {
    private val store = LocalMediaStore(context.contentResolver)

    override suspend fun read(): LocalLibrary = embeddedTags.withKnown(LocalLibrary(videos = store.videos(), music = store.music()))

    override suspend fun complete(read: LocalLibrary): LocalLibrary = embeddedTags.withAll(read)

    // Before Android 10 the scanner indexes a folder path as one file instead of walking it.
    override suspend fun reindex() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        context.scanFolders(foldersToReindex(store.filePaths()) { File(it).exists() })
    }

    override fun generation(): Long? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL) else null

    override fun changes(): Flow<Unit> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(Unit)
                    }
                }
            val resolver = context.contentResolver
            resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
            resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
            awaitClose { resolver.unregisterContentObserver(observer) }
        }
}
