package io.github.aedev.flow.data.repository

import android.util.LruCache
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object VideoCollaboratorResolver {
    private val cache = LruCache<String, List<VideoCollaborator>>(300)

    suspend fun resolve(videoId: String): List<VideoCollaborator> {
        if (videoId.isBlank()) return emptyList()
        cache[videoId]?.let { return it }

        return withContext(Dispatchers.IO) {
            cache[videoId]?.let { return@withContext it }
            val collaborators = withTimeoutOrNull(4_000L) {
                YouTube.videoCollaborators(videoId).getOrNull()
            }.orEmpty()
            if (collaborators.isNotEmpty()) {
                cache.put(videoId, collaborators)
            }
            collaborators
        }
    }
}
