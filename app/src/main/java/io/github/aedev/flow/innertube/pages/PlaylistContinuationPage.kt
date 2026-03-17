package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
