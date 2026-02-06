package com.flow.youtube.innertube.models

data class MediaInfo(
    val videoId: String,
    val title: String? = null,
    val author: String? = null,
    val authorId: String? = null,
    val authorThumbnail: String? = null,
    val description: String? = null,
    val uploadDate: String? = null,
    val subscribers: String? = null,
    val viewCount: Int? = null,
    val like: Int? = null,
    val dislike: Int? = null,
    val mimeType: String? = null,
    val bitrate: Long? = null,
    val sampleRate: Int? = null,
    val frameRate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val contentLength: String? = null,
    val qualityLabel: String? = null,
    val videoId_tag: Int? = null // itag
)
