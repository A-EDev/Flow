package io.github.aedev.flow.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class EditPlaylistResponse(
    val newHeader: BrowseResponse.Header?,
)
