package io.github.aedev.flow.data.video.storage

import android.content.Context
import java.io.File

/** The cover each download keeps in app storage, so it shows offline and survives a cache clear. */
object DownloadCovers {
    private const val DIRECTORY = "download_covers"
    private val UNSAFE = Regex("[^A-Za-z0-9_-]")

    /** Saves [cover] for [videoId] and returns its path, or null when it could not be written. */
    fun save(
        context: Context,
        videoId: String,
        cover: ByteArray,
    ): String? =
        runCatching {
            val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            File(directory, "${videoId.replace(UNSAFE, "_")}.jpg")
                .apply { writeBytes(cover) }
                .absolutePath
        }.getOrNull()
}
