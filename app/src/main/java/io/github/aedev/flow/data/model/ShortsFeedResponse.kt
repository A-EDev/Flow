package io.github.aedev.flow.data.model

data class ShortsFeedResponse(
    val videos: List<ShortItem>,
    val nextContinuationToken: String?
)
