package io.github.aedev.flow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Music recognizer is built based on Metrolist's implementation, view https://github.com/MetrolistGroup/Metrolist

@Entity(
    tableName = "recognition_history",
    indices = [
        Index("trackId"),
        Index("recognizedAt"),
        Index("title"),
        Index("artist")
    ]
)
data class RecognitionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverArtUrl: String? = null,
    val coverArtHqUrl: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val label: String? = null,
    val shazamUrl: String? = null,
    val appleMusicUrl: String? = null,
    val spotifyUrl: String? = null,
    val isrc: String? = null,
    val youtubeVideoId: String? = null,
    val recognizedAt: Long = System.currentTimeMillis(),
    val liked: Boolean = false
)
