package com.flow.youtube.innertube.pages

import com.flow.youtube.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
