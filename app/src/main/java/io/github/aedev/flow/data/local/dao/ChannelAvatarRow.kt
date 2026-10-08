package io.github.aedev.flow.data.local.dao

/** A channel's avatar as a cached row recorded it. */
data class ChannelAvatarRow(
    val channelId: String,
    val channelThumbnailUrl: String,
)
