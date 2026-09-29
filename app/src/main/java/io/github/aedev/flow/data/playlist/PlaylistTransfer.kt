package io.github.aedev.flow.data.playlist

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.utils.sharedFileUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val SHARE_DIR = "playlists"

// Far above any real playlist (5,000 videos is about 2.5 MB), low enough that a wrong file can't exhaust memory.
private const val MAX_FILE_BYTES = 16L * 1024 * 1024

/** What an import did. */
sealed interface PlaylistImport {
    data class Imported(
        val playlistId: String,
        val name: String,
        val videoCount: Int,
        val isMusic: Boolean = false,
    ) : PlaylistImport

    data object NotAPlaylist : PlaylistImport

    data object TooNew : PlaylistImport

    data object Empty : PlaylistImport

    data object Unreadable : PlaylistImport
}

/** Writes playlists to files, for saving or sharing, and reads them back as new playlists. */
@Singleton
class PlaylistTransfer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val playlists: PlaylistRepository,
    ) {
        /** Saves the playlist into [target], a document the viewer picked; false when it could not be written. */
        suspend fun writeTo(
            target: Uri,
            name: String,
            description: String,
            videos: List<Video>,
            isMusic: Boolean = false,
        ): Boolean =
            withContext(Dispatchers.IO) {
                runCatching {
                    val text = PlaylistFileCodec.encode(name, description, videos, System.currentTimeMillis(), isMusic)
                    checkNotNull(context.contentResolver.openOutputStream(target, "wt")).use { it.write(text.toByteArray()) }
                }.isSuccess
            }

        /** A copy in the cache another app can read, replacing the last one shared; null when it could not be written. */
        suspend fun shareableCopy(
            name: String,
            description: String,
            videos: List<Video>,
            isMusic: Boolean = false,
        ): Uri? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
                    dir.listFiles()?.forEach(File::delete)
                    val file = File(dir, PlaylistFileCodec.fileName(name))
                    file.writeText(PlaylistFileCodec.encode(name, description, videos, System.currentTimeMillis(), isMusic))
                    sharedFileUri(context, file)
                }.getOrNull()
            }

        /** Adds the playlist in [source] as a new playlist of the viewer's own. */
        suspend fun import(
            source: Uri,
            fallbackName: String,
        ): PlaylistImport =
            withContext(Dispatchers.IO) {
                val text =
                    runCatching { context.contentResolver.openInputStream(source)?.use { it.readBounded() } }.getOrNull()
                        ?: return@withContext PlaylistImport.Unreadable
                when (val read = PlaylistFileCodec.decode(text)) {
                    PlaylistFileRead.NotAPlaylist -> {
                        PlaylistImport.NotAPlaylist
                    }

                    PlaylistFileRead.TooNew -> {
                        PlaylistImport.TooNew
                    }

                    is PlaylistFileRead.Read -> {
                        val videos = read.file.videos.map(PlaylistFileVideo::toVideo)
                        if (videos.isEmpty()) return@withContext PlaylistImport.Empty
                        val name =
                            read.file.playlist.name
                                .trim()
                                .ifEmpty { fallbackName }
                        val isMusic = read.file.playlist.isMusic
                        val id = playlists.importPlaylist(name, read.file.playlist.description, videos, isMusic)
                        PlaylistImport.Imported(id, name, videos.size, isMusic)
                    }
                }
            }

        private fun InputStream.readBounded(): String? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_FILE_BYTES) return null
                out.write(buffer, 0, read)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
