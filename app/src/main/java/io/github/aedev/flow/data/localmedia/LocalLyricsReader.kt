package io.github.aedev.flow.data.localmedia

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.video.downloader.tags.DownloadTagReader
import io.github.aedev.flow.data.video.storage.DownloadFiles
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocalLyricsReader"
private const val MAX_LYRICS_FILE_BYTES = 512 * 1024L

/** Where a device song's lyrics were found. */
enum class LocalLyricsSource { FILE, EMBEDDED }

class LocalLyrics(
    val text: String,
    val source: LocalLyricsSource,
)

/**
 * A device song's own lyrics: a `.lrc` beside it, else the lyrics embedded in the file. Nothing
 * here touches the network. MediaStore indexes `.lrc` as a subtitle file, so the same media
 * permission that lists the song lets Flow read the file next to it.
 */
@Singleton
class LocalLyricsReader
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val tagReader: DownloadTagReader,
    ) {
        suspend fun read(trackId: String): LocalLyrics? {
            val uri = LocalMediaIds.audioUri(trackId) ?: return null
            val sidecar = withContext(PerformanceDispatcher.diskIO) { sidecarText(filePathOf(trackId)) }
            if (sidecar != null) return LocalLyrics(sidecar, LocalLyricsSource.FILE)
            return tagReader.read(uri)?.lyrics?.let { LocalLyrics(it, LocalLyricsSource.EMBEDDED) }
        }

        /** The lyrics Flow embedded into one of its own downloads, from a file path or a document Uri. */
        suspend fun readDownload(path: String): LocalLyrics? {
            val uri = if (DownloadFiles.isDocument(path)) Uri.parse(path) else Uri.fromFile(File(path))
            return tagReader.read(uri)?.lyrics?.let { LocalLyrics(it, LocalLyricsSource.EMBEDDED) }
        }

        private fun filePathOf(trackId: String): String? = LocalMediaIds.audioUri(trackId)?.let { context.mediaStoreDataPath(it) }

        private fun sidecarText(audioPath: String?): String? {
            val audio = audioPath?.let(::File) ?: return null
            val folder = audio.parentFile ?: return null
            return try {
                val direct = File(folder, "${audio.nameWithoutExtension}.lrc")
                val file =
                    direct.takeIf { it.isFile }
                        ?: matchingLyricsFile(audio.name, folder.list()?.toList().orEmpty())?.let { File(folder, it) }
                        ?: return null
                if (file.length() > MAX_LYRICS_FILE_BYTES) return null
                decodeTextFile(file.readBytes()) { legacyTextCharset(Locale.getDefault()) }.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read lyrics beside ${audio.name}: ${e.message}")
                null
            }
        }
    }
