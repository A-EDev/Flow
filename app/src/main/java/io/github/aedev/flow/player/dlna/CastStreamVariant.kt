package io.github.aedev.flow.player.dlna

data class CastStreamVariant(
    val url: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val mime: String = "video/mp4",
    val codec: String = "avc1.64001F"
)
