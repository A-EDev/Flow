package io.github.aedev.flow.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class CreatePlaylistResponse(
    val playlistId: String
)
