package com.flow.youtube.innertube.models.body

import com.flow.youtube.innertube.models.Context
import com.flow.youtube.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?
)
