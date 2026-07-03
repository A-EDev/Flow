package io.github.aedev.flow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "home_feed_cache",
    indices = [
        Index(value = ["bucket", "expiresAt"]),
        Index(value = ["bucket", "source"]),
        Index(value = ["relatedSeedId"]),
        Index(value = ["videoId"]),
        Index(value = ["channelId"])
    ]
)
data class HomeFeedCacheEntity(
    @PrimaryKey val cacheKey: String,
    val bucket: String,
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Int,
    val viewCount: Long,
    val likeCount: Long,
    val uploadDate: String,
    val timestamp: Long,
    val description: String,
    val channelThumbnailUrl: String,
    val tagsJson: String,
    val isMusic: Boolean,
    val isLive: Boolean,
    val isShort: Boolean,
    val isUpcoming: Boolean,
    val commentCountText: String,
    val source: String,
    val relatedSeedId: String?,
    val cachedAt: Long,
    val expiresAt: Long,
    val orderIndex: Int
)
