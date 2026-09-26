package io.github.aedev.flow.data.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.local.entity.DownloadEntity
import io.github.aedev.flow.data.local.entity.DownloadFileType
import io.github.aedev.flow.data.local.entity.DownloadItemEntity
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.video.VideoDownloadManager.Companion.VIDEO_DIR
import io.github.aedev.flow.data.video.storage.DownloadFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds video and audio files in the download folders that the database does not know about (after
 * a database wipe, for example) and records them as completed downloads, so they show in Downloads
 * and play offline. Files already recorded are skipped, so it is safe to run repeatedly.
 */
@Singleton
class DownloadRecoveryScanner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val downloadDao: DownloadDao,
        private val downloadManager: VideoDownloadManager,
        private val preferences: PlayerPreferences,
    ) {
        /** Set once a scan has run in this process; the Downloads screen scans again only on pull to refresh. */
        @Volatile
        var hasScannedThisSession: Boolean = false
            private set

        suspend fun scanAndRecoverDownloads() =
            withContext(Dispatchers.IO) {
                hasScannedThisSession = true
                try {
                    val chosenFolders = chosenFolders()
                    val exportedPaths = exportedDocumentPaths()
                    val dirsToScan =
                        buildList {
                            chosenFolders.forEach { add(File(it)) }
                            add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), VIDEO_DIR))
                            add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), VIDEO_DIR))
                            add(File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), VIDEO_DIR))
                            add(File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), VIDEO_DIR))
                        }

                    for (dir in dirsToScan.distinctBy { it.canonicalPath }) {
                        if (!dir.exists() || !dir.isDirectory) continue
                        val files = dir.listFiles() ?: continue
                        for (file in files) {
                            if (!file.isFile) continue
                            val ext = file.extension.lowercase()
                            if (ext !in VIDEO_EXTENSIONS && ext !in AUDIO_EXTENSIONS) continue
                            val filePath = file.absolutePath
                            if (!isNewFile(filePath, exportedPaths)) continue

                            val metadata = readMetadata(filePath)
                            recover(
                                filePath = filePath,
                                fileName = file.name,
                                title = metadata.title ?: file.nameWithoutExtension,
                                artist = metadata.artist ?: LOCAL_FILE_ARTIST,
                                durationMs = metadata.durationMs,
                                sizeBytes = file.length(),
                                createdAt = file.lastModified(),
                            )
                            Log.i(TAG, "scanAndRecoverDownloads: recovered '${file.name}'")
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        scanViaMediaStore(chosenFolders, exportedPaths)
                    } else {
                        Unit
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "scanAndRecoverDownloads failed", e)
                }
            }

        /**
         * MediaStore-based recovery scan. Queries the system media index for files in Downloads/Flow,
         * Movies/Flow, the app-private folders and the chosen download folders. Works with only
         * READ_MEDIA_VIDEO / READ_MEDIA_AUDIO, no MANAGE_EXTERNAL_STORAGE needed.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        private suspend fun scanViaMediaStore(
            chosenFolders: List<String>,
            exportedPaths: Set<String>,
        ) = withContext(Dispatchers.IO) {
            val projection =
                arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DURATION,
                )

            val pathPrefixes =
                buildList {
                    Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        ?.let { File(it, VIDEO_DIR).canonicalPath }
                        ?.let { add(it) }
                    Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                        ?.let { File(it, VIDEO_DIR).canonicalPath }
                        ?.let { add(it) }
                    context
                        .getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                        ?.canonicalPath
                        ?.let { add(it) }
                    context
                        .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?.canonicalPath
                        ?.let { add(it) }
                    chosenFolders.forEach { add(File(it).canonicalPath) }
                }

            val collections =
                listOf(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                )

            for (collectionUri in collections) {
                try {
                    context.contentResolver
                        .query(collectionUri, projection, null, null, null)
                        ?.use { cursor ->
                            val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                            val durIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)

                            while (cursor.moveToNext()) {
                                val filePath = cursor.getString(dataIdx) ?: continue
                                val fileName = cursor.getString(nameIdx) ?: continue
                                if (pathPrefixes.none { prefix -> filePath.startsWith(prefix) }) continue

                                val ext = fileName.substringAfterLast('.', "").lowercase()
                                if (ext !in VIDEO_EXTENSIONS && ext !in AUDIO_EXTENSIONS) continue
                                if (!isNewFile(filePath, exportedPaths)) continue

                                val file = File(filePath)
                                recover(
                                    filePath = filePath,
                                    fileName = fileName,
                                    title = fileName.substringBeforeLast('.'),
                                    artist = LOCAL_FILE_ARTIST,
                                    durationMs = cursor.getLong(durIdx),
                                    sizeBytes = cursor.getLong(sizeIdx),
                                    createdAt = if (file.exists()) file.lastModified() else System.currentTimeMillis(),
                                )
                                Log.i(TAG, "scanViaMediaStore: recovered '$fileName'")
                            }
                        }
                } catch (e: Exception) {
                    Log.w(TAG, "scanViaMediaStore: query failed for $collectionUri", e)
                }
            }
        }

        private suspend fun chosenFolders(): List<String> =
            listOf(preferences.downloadLocation.first(), preferences.musicDownloadLocation.first())
                .mapNotNull { location -> location.path?.takeIf { it.isNotBlank() } ?: location.treeUri?.let(DownloadFiles::treePath) }
                .distinct()

        // A file exported into a picked folder is stored as its document, yet a folder scan meets it by
        // its path; both spellings must count as already recorded.
        private suspend fun exportedDocumentPaths(): Set<String> =
            downloadDao
                .getAllDownloadsWithItemsOnce()
                .flatMap { it.items }
                .mapNotNull { item -> item.filePath.takeIf(DownloadFiles::isDocument)?.let(DownloadFiles::documentPath) }
                .toSet()

        private suspend fun isNewFile(
            filePath: String,
            exportedPaths: Set<String>,
        ): Boolean =
            filePath !in exportedPaths &&
                !downloadDao.existsByFilePath(filePath) &&
                !downloadManager.isBeingDeleted(filePath) &&
                !downloadManager.retryTombstonedDelete(filePath)

        private suspend fun recover(
            filePath: String,
            fileName: String,
            title: String,
            artist: String,
            durationMs: Long,
            sizeBytes: Long,
            createdAt: Long,
        ) {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val isVideo = ext in VIDEO_EXTENSIONS
            val pseudoId = "recovered_${filePath.hashCode().toLong() and 0xFFFFFFFFL}"
            downloadDao.insertDownload(
                DownloadEntity(
                    videoId = pseudoId,
                    title = title,
                    uploader = artist,
                    duration = durationMs / 1000,
                    thumbnailUrl = if (isVideo) frameThumbnail(filePath, pseudoId) else "",
                    createdAt = createdAt,
                ),
            )
            downloadDao.insertItem(
                DownloadItemEntity(
                    videoId = pseudoId,
                    fileType = if (isVideo) DownloadFileType.VIDEO else DownloadFileType.AUDIO,
                    fileName = fileName,
                    filePath = filePath,
                    format = ext,
                    quality = "Local",
                    mimeType = if (isVideo) "video/mp4" else "audio/mp4",
                    downloadedBytes = sizeBytes,
                    totalBytes = sizeBytes,
                    status = DownloadItemStatus.COMPLETED,
                ),
            )
        }

        private class FileMetadata(
            val title: String?,
            val artist: String?,
            val durationMs: Long,
        )

        private fun readMetadata(filePath: String): FileMetadata {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(filePath)
                FileMetadata(
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                )
            } catch (_: Exception) {
                FileMetadata(null, null, 0L)
            } finally {
                runCatching { retriever.release() }
            }
        }

        private fun frameThumbnail(
            filePath: String,
            pseudoId: String,
        ): String =
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(filePath)
                val bitmap =
                    retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                if (bitmap != null) {
                    val thumbFile = File(context.cacheDir, "thumb_$pseudoId.jpg")
                    thumbFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    bitmap.recycle()
                    if (thumbFile.exists() && thumbFile.length() > 0) "file://${thumbFile.absolutePath}" else ""
                } else {
                    ""
                }
            } catch (_: Exception) {
                ""
            }

        private companion object {
            const val TAG = "DownloadRecoveryScanner"
            const val LOCAL_FILE_ARTIST = "Local File"
            val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "avi", "mov")
            val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "opus", "ogg")
        }
    }
