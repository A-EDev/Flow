package io.github.aedev.flow.data.localmedia

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

private const val SCAN_TIMEOUT_MS = 30_000L

/**
 * The folders to scan again so MediaStore forgets the rows whose files are gone. A file manager
 * that writes the storage directly can rename files without MediaStore noticing; the old row keeps
 * the old name and opens nothing, so it shows no picture, until its folder is scanned (#1214).
 * A folder inside another listed one is left to that one's scan, which walks it too.
 */
internal fun foldersToReindex(
    paths: Iterable<String>,
    exists: (String) -> Boolean,
): List<String> {
    val folders = paths.filterNot(exists).mapNotNullTo(HashSet()) { it.substringBeforeLast('/', "").ifEmpty { null } }
    return folders.filter { folder -> folders.none { other -> folder.startsWith("$other/") } }.sorted()
}

/**
 * Scans [folders] and returns once MediaStore has finished with all of them, or after a time
 * limit, since the scanner runs in another process. Its scan of a folder adds the files it finds
 * and deletes the rows of files that are no longer there.
 */
internal suspend fun Context.scanFolders(folders: List<String>) {
    if (folders.isEmpty()) return
    withTimeoutOrNull(SCAN_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val left = AtomicInteger(folders.size)
            MediaScannerConnection.scanFile(applicationContext, folders.toTypedArray(), null) { _, _ ->
                if (left.decrementAndGet() == 0 && continuation.isActive) continuation.resume(Unit)
            }
        }
    }
}
