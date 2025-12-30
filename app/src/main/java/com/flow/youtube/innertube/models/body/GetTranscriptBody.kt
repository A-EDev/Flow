package com.flow.youtube.innertube.models.body

import com.flow.youtube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptBody(
    val context: Context,
    val params: String,
)
