package io.github.aedev.flow.data.localmedia

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A subtitle file someone picked for a video: its document Uri and the name it had. */
@Serializable
data class PickedSubtitle(
    val uri: String,
    val name: String,
)

/**
 * The subtitle files picked for each video, so reopening the video brings them back. Kept for the
 * most recent [MAX_VIDEOS] videos only. Callers read and write off the main thread.
 */
@Singleton
class PickedSubtitleStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val lock = Mutex()
        private val file: File get() = File(context.filesDir, FILE_NAME)

        suspend fun forVideo(videoId: String): List<PickedSubtitle> = lock.withLock { readAll()[videoId].orEmpty() }

        suspend fun add(
            videoId: String,
            pick: PickedSubtitle,
        ) = lock.withLock {
            val all = LinkedHashMap(readAll())
            val kept = all.remove(videoId).orEmpty().filterNot { it.uri == pick.uri }
            all[videoId] = kept + pick
            while (all.size > MAX_VIDEOS) all.remove(all.keys.first())
            runCatching { file.writeText(json.encodeToString(serializer, all)) }
                .onFailure { Log.w(TAG, "Could not remember the subtitle picked for $videoId", it) }
            Unit
        }

        private fun readAll(): Map<String, List<PickedSubtitle>> =
            runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())

        private companion object {
            const val TAG = "PickedSubtitleStore"
            const val FILE_NAME = "picked_subtitles.json"
            const val MAX_VIDEOS = 200
            val json = Json { ignoreUnknownKeys = true }
            val serializer = MapSerializer(String.serializer(), ListSerializer(PickedSubtitle.serializer()))
        }
    }
