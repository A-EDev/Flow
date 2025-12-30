package com.flow.youtube.innertube.pages

import com.flow.youtube.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
