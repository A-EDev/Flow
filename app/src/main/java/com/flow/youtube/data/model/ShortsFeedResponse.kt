package com.flow.youtube.data.model

data class ShortsFeedResponse(
    val videos: List<ShortItem>,
    val nextContinuationToken: String?
)
