package io.github.aedev.flow.data.recognition

// Music recognizer is built based on Metrolist's implementation, view https://github.com/MetrolistGroup/Metrolist

/** App-layer result of a music recognition, decoupled from the Shazam JSON shape. */
data class RecognitionResult(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverArtUrl: String? = null,
    val coverArtHqUrl: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val label: String? = null,
    val lyrics: List<String>? = null,
    val shazamUrl: String? = null,
    val appleMusicUrl: String? = null,
    val spotifyUrl: String? = null,
    val isrc: String? = null,
    val youtubeVideoId: String? = null
)

sealed interface RecognitionStatus {
    data object Ready : RecognitionStatus
    data object Listening : RecognitionStatus
    data object Processing : RecognitionStatus
    data class Success(val result: RecognitionResult) : RecognitionStatus
    data class NoMatch(val message: String) : RecognitionStatus
    data class Error(val message: String) : RecognitionStatus
}
