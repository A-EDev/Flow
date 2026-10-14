package io.github.aedev.flow.data.localmedia

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.video.downloader.tags.DownloadTagReader
import io.github.aedev.flow.data.video.downloader.tags.MatroskaTextTags
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One file's read, kept until the file changes. [titleless] is whether the platform's reader finds
 * no title either; it is asked only when a file without one is no longer titled by its name.
 */
@Serializable
internal data class StoredEmbeddedText(
    val stamp: String,
    val text: EmbeddedText,
    val titleless: Boolean? = null,
)

/**
 * Reads the title, artist and album straight from each device file's container, the way players
 * with their own parsers show them: Media3 for MP4, MP3, FLAC and Ogg, [MatroskaTextTags] for
 * Matroska and WebM. Every file is read once; the results are kept on disk until it changes.
 */
@OptIn(UnstableApi::class)
@Singleton
class LocalEmbeddedTags
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val tagReader: DownloadTagReader,
    ) {
        private val lock = Mutex()
        private var stored: MutableMap<Long, StoredEmbeddedText>? = null
        private val file: File get() = File(context.filesDir, FILE_NAME)

        /** [library] with every read already kept, after reading the files whose MediaStore text looks damaged. */
        internal suspend fun withKnown(library: LocalLibrary): LocalLibrary {
            readMissing(library.items().filter { it.looksDamaged() })
            return applied(library)
        }

        /** [library] with every file read, reading the ones not read yet; forgets files that are gone. */
        internal suspend fun withAll(library: LocalLibrary): LocalLibrary {
            readMissing(library.items())
            checkRenamed(library.items())
            lock.withLock {
                val ids = library.items().mapTo(HashSet()) { it.id }
                val all = loaded()
                if (all.keys.retainAll(ids)) save(all)
            }
            return applied(library)
        }

        /** The text of the device file [uri] points at, from the kept read when there is one. */
        suspend fun forFile(
            mediaStoreId: Long,
            uri: Uri,
        ): EmbeddedText = lock.withLock { loaded()[mediaStoreId]?.text } ?: read(uri)

        private suspend fun readMissing(items: List<LocalMediaItem>) {
            val missing = lock.withLock { loaded().let { all -> items.filter { all[it.id]?.stamp != it.fileStamp } } }
            if (missing.isEmpty()) return
            val reads = mutableListOf<Pair<Long, StoredEmbeddedText>>()
            try {
                missing.chunked(PARALLEL_READS).forEach { round ->
                    reads +=
                        coroutineScope {
                            round
                                .map { item -> async { item.id to StoredEmbeddedText(item.fileStamp, read(Uri.parse(item.contentUri))) } }
                                .awaitAll()
                        }
                }
            } finally {
                withContext(NonCancellable) {
                    lock.withLock {
                        val all = loaded()
                        reads.forEach { (id, read) -> all[id] = read }
                        if (reads.isNotEmpty()) save(all)
                    }
                }
            }
        }

        /**
         * Asks the platform's reader about each untitled file whose MediaStore title no longer
         * matches its name. Media3 skips some tags MediaStore reads (ID3v1, AVI, ASF), so only a
         * file neither finds a title in was titled by a name it has since lost.
         */
        private suspend fun checkRenamed(items: List<LocalMediaItem>) {
            val unknown =
                lock.withLock {
                    val all = loaded()
                    items.filter { item ->
                        val stored = all[item.id]
                        stored != null &&
                            stored.stamp == item.fileStamp &&
                            stored.text.title == null &&
                            stored.titleless == null &&
                            item.fileTitle.let { it != null && it != item.title }
                    }
                }
            if (unknown.isEmpty()) return
            val checks = withContext(PerformanceDispatcher.diskIO) { unknown.map { it.id to platformTitleless(Uri.parse(it.contentUri)) } }
            lock.withLock {
                val all = loaded()
                checks.forEach { (id, titleless) -> all[id]?.let { all[id] = it.copy(titleless = titleless) } }
                save(all)
            }
        }

        private fun platformTitleless(uri: Uri): Boolean {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).isNullOrBlank()
            } catch (e: Exception) {
                Log.w(TAG, "Reading the title of $uri failed: ${e.message}")
                false
            } finally {
                retriever.release()
            }
        }

        private suspend fun applied(library: LocalLibrary): LocalLibrary {
            val all = lock.withLock { HashMap(loaded()) }

            fun List<LocalMediaItem>.applied() =
                map { item ->
                    all[item.id]?.takeIf { it.stamp == item.fileStamp }?.let { item.withEmbeddedText(it.text, it.titleless == true) }
                        ?: item
                }
            return library.copy(videos = library.videos.applied(), music = library.music.applied())
        }

        private suspend fun read(uri: Uri): EmbeddedText =
            withContext(PerformanceDispatcher.diskIO) {
                try {
                    readMatroska(uri) ?: readWithMedia3(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Reading the tags of $uri failed: ${e.message}")
                    EmbeddedText()
                }
            }

        private fun readMatroska(uri: Uri): EmbeddedText? {
            val tags =
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    FileInputStream(descriptor.fileDescriptor).channel.use(MatroskaTextTags::read)
                } ?: return null
            return EmbeddedText(
                title = embeddedField(tags[MatroskaTextTags.TITLE]),
                artist = embeddedField(tags[MatroskaTextTags.ARTIST]),
                album = embeddedField(tags[MatroskaTextTags.ALBUM]),
            )
        }

        private suspend fun readWithMedia3(uri: Uri): EmbeddedText {
            val builder = MediaMetadata.Builder()
            tagReader.entries(uri).forEach { it.populateMediaMetadata(builder) }
            val metadata = builder.build()
            return EmbeddedText(
                title = embeddedField(metadata.title),
                artist = embeddedField(metadata.artist),
                album = embeddedField(metadata.albumTitle),
            )
        }

        private fun loaded(): MutableMap<Long, StoredEmbeddedText> =
            stored ?: runCatching { json.decodeFromString(serializer, file.readText()) }
                .getOrDefault(emptyMap())
                .toMutableMap()
                .also { stored = it }

        private fun save(all: Map<Long, StoredEmbeddedText>) {
            runCatching { file.writeText(json.encodeToString(serializer, all)) }
                .onFailure { Log.w(TAG, "Could not keep the files' tags", it) }
        }

        private companion object {
            const val TAG = "LocalEmbeddedTags"
            const val FILE_NAME = "local_embedded_tags.json"
            const val PARALLEL_READS = 4
            val json = Json { ignoreUnknownKeys = true }
            val serializer = MapSerializer(Long.serializer(), StoredEmbeddedText.serializer())
        }
    }

private fun LocalLibrary.items(): List<LocalMediaItem> = videos + music
