package io.github.aedev.flow.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class ImageUploadResponse(
    val encryptedBlobId: String
)
