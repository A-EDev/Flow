package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.AlbumItem

data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)
