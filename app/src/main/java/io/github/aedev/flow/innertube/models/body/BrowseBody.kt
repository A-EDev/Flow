package io.github.aedev.flow.innertube.models.body

import io.github.aedev.flow.innertube.models.Context
import io.github.aedev.flow.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?
)
