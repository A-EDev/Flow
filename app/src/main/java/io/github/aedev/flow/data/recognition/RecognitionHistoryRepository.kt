package io.github.aedev.flow.data.recognition

import io.github.aedev.flow.data.local.dao.RecognitionHistoryDao
import io.github.aedev.flow.data.local.entity.RecognitionHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// Music recognizer is built based on Metrolist's implementation, view https://github.com/MetrolistGroup/Metrolist

@Singleton
class RecognitionHistoryRepository @Inject constructor(
    private val dao: RecognitionHistoryDao
) {
    val history: Flow<List<RecognitionHistoryEntity>> = dao.getAll()

    fun search(query: String): Flow<List<RecognitionHistoryEntity>> =
        if (query.isBlank()) dao.getAll() else dao.search(query.trim())

    suspend fun save(result: RecognitionResult): Long = dao.insert(
        RecognitionHistoryEntity(
            trackId = result.trackId,
            title = result.title,
            artist = result.artist,
            album = result.album,
            coverArtUrl = result.coverArtUrl,
            coverArtHqUrl = result.coverArtHqUrl,
            genre = result.genre,
            releaseDate = result.releaseDate,
            label = result.label,
            shazamUrl = result.shazamUrl,
            appleMusicUrl = result.appleMusicUrl,
            spotifyUrl = result.spotifyUrl,
            isrc = result.isrc,
            youtubeVideoId = result.youtubeVideoId
        )
    )

    suspend fun setLiked(id: Long, liked: Boolean) = dao.setLiked(id, liked)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clearAll() = dao.clearAll()
}
