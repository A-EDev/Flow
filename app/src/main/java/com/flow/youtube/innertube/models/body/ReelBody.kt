package com.flow.youtube.innertube.models.body

import com.flow.youtube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class ReelBody(
    val context: Context,
    val params: String? = null,
    val sequenceParams: String? = "CA8%3D", // Default param often used for initial reels fetch
)
