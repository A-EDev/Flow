package io.github.aedev.flow.data.localmedia

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.downloader.tags.DownloadTagReader
import io.github.aedev.flow.data.video.downloader.tags.Mp4TextAtoms
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val CACHED_FILES = 32

/** What a device video's own tags add to the player. */
internal data class LocalFileTags(
    val artist: String?,
    val description: String?,
)

/** The text shown as a device file's description: yt-dlp keeps the watch URL in the comment, so it comes last. */
internal fun shownDescription(
    description: String?,
    longDescription: String?,
    comment: String?,
): String? = listOf(description, longDescription, comment).firstOrNull { !it.isNullOrBlank() }?.trim()

/** Reads a device video's embedded artist and description once per file, when it is opened. */
@Singleton
class LocalMediaDetails
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val tagReader: DownloadTagReader,
    ) {
        private val cache =
            object : LinkedHashMap<String, LocalFileTags>(CACHED_FILES, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LocalFileTags>?) = size > CACHED_FILES
            }

        /** [video] with the channel and description its file carries, or null when the file adds nothing. */
        suspend fun enrich(video: Video): Video? {
            val uri = LocalMediaIds.videoUri(video.id) ?: return null
            val tags = synchronized(cache) { cache[video.id] } ?: read(uri).also { synchronized(cache) { cache[video.id] = it } }
            val enriched =
                video.copy(
                    channelName = tags.artist ?: video.channelName,
                    description = tags.description ?: video.description,
                )
            return enriched.takeIf { it != video }
        }

        private suspend fun read(uri: Uri): LocalFileTags =
            withContext(PerformanceDispatcher.diskIO) {
                val embedded = tagReader.read(uri)
                val atoms =
                    runCatching {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                            FileInputStream(descriptor.fileDescriptor).channel.use {
                                Mp4TextAtoms.read(it, setOf(Mp4TextAtoms.DESCRIPTION, Mp4TextAtoms.LONG_DESCRIPTION))
                            }
                        }
                    }.getOrNull().orEmpty()
                LocalFileTags(
                    artist = embedded?.artist?.trim()?.takeIf(String::isNotEmpty),
                    description =
                        shownDescription(
                            description = atoms[Mp4TextAtoms.DESCRIPTION] ?: embedded?.description,
                            longDescription = atoms[Mp4TextAtoms.LONG_DESCRIPTION],
                            comment = embedded?.comment,
                        ),
                )
            }
    }
