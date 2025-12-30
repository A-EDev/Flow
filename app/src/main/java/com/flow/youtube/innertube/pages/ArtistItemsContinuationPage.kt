package com.flow.youtube.innertube.pages

import com.flow.youtube.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
