package com.flow.youtube.data.model

data class DeArrowTitle(
    val title: String = "",
    val votes: Int = 0,
    val locked: Boolean = false,
    val original: Boolean = false
)

data class DeArrowThumbnail(
    val timestamp: Float? = null,
    val thumbnail: String? = null,
    val votes: Int = 0,
    val locked: Boolean = false,
    val original: Boolean = false
)

data class DeArrowContent(
    val titles: List<DeArrowTitle> = emptyList(),
    val thumbnails: List<DeArrowThumbnail> = emptyList(),
    val randomTime: Float? = null,
    val videoDuration: Float? = null
)

/**
 * The resolved deArrow overrides for a video. Non-null fields should replace the originals.
 */
data class DeArrowResult(
    val title: String? = null,
    val thumbnailUrl: String? = null
)
