package io.github.aedev.flow.data.localmedia

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
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

/** What is remembered for one video: the subtitle files picked for it and its caption timing. */
@Serializable
data class VideoSubtitleState(
    val picks: List<PickedSubtitle> = emptyList(),
    val offsetMs: Long = 0L,
)

/**
 * The subtitle files picked for each device video or download, and the timing set for it, so
 * reopening the video brings both back. Kept for the most recent [MAX_VIDEOS] videos only.
 * Callers read and write off the main thread.
 */
@Singleton
class LocalSubtitleStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val lock = Mutex()
        private val file: File get() = File(context.filesDir, FILE_NAME)

        suspend fun forVideo(videoId: String): VideoSubtitleState = lock.withLock { readAll()[videoId] ?: VideoSubtitleState() }

        suspend fun addPick(
            videoId: String,
            pick: PickedSubtitle,
        ) = update(videoId) { state -> state.copy(picks = state.picks.filterNot { it.uri == pick.uri } + pick) }

        suspend fun setOffset(
            videoId: String,
            offsetMs: Long,
        ) = update(videoId) { it.copy(offsetMs = offsetMs) }

        private suspend fun update(
            videoId: String,
            change: (VideoSubtitleState) -> VideoSubtitleState,
        ) = lock.withLock {
            val all = LinkedHashMap(readAll())
            val updated = change(all.remove(videoId) ?: VideoSubtitleState())
            if (updated != VideoSubtitleState()) all[videoId] = updated
            while (all.size > MAX_VIDEOS) all.remove(all.keys.first())
            runCatching { file.writeText(json.encodeToString(serializer, all)) }
                .onFailure { Log.w(TAG, "Could not remember the subtitles of $videoId", it) }
            Unit
        }

        private fun readAll(): Map<String, VideoSubtitleState> =
            runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())

        private companion object {
            const val TAG = "LocalSubtitleStore"
            const val FILE_NAME = "local_subtitles.json"
            const val MAX_VIDEOS = 200
            val json = Json { ignoreUnknownKeys = true }
            val serializer = MapSerializer(String.serializer(), VideoSubtitleState.serializer())
        }
    }
