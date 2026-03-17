package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
