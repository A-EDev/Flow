package io.github.aedev.flow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscription_feed_cache")
data class SubscriptionFeedEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Int,
    val viewCount: Long,
    val uploadDate: String,
    val timestamp: Long = System.currentTimeMillis(),
    val channelThumbnailUrl: String,
    val isShort: Boolean = false,
    val isLive: Boolean = false,
    val isUpcoming: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis(),
    /**
     * Blank for a channel's own upload. For a collaboration it is the followed channel that brought
     * the row in, while [channelId] stays the uploader the viewer may not follow.
     */
    @ColumnInfo(defaultValue = "") val feedChannelId: String = "",
    /** The collaborators as JSON, so a cached row keeps its avatar stack. */
    @ColumnInfo(defaultValue = "") val collaboratorsJson: String = "",
    /** [timestamp] is a real publish time (RSS), not one read back from "3 weeks ago". */
    @ColumnInfo(defaultValue = "0") val timestampIsExact: Boolean = false,
)

@Entity(tableName = "music_home_cache")
data class MusicHomeCacheEntity(
    @PrimaryKey val sectionId: String, // e.g., "quick_picks", "trending", "section_0"
    val title: String,
    val subtitle: String?,
    val tracksJson: String, // Store list of tracks as JSON string
    val orderBy: Int,
)

@Entity(tableName = "music_home_chips_cache")
data class MusicHomeChipEntity(
    @PrimaryKey val title: String,
    val browseId: String?,
    val params: String?,
    val deselectBrowseId: String?,
    val deselectParams: String?,
    val orderBy: Int,
)
