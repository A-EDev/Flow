package io.github.aedev.flow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.aedev.flow.data.video.downloader.tags.DownloadKind

/**
 * Represents a downloaded media item (video or audio-only).
 * One DownloadEntity can have multiple DownloadItemEntity children
 * (e.g., separate video + audio files for DASH, or a single muxed file).
 *
 * The metadata columns are a snapshot taken when the download was requested, so a download can be
 * shown, tagged and retried with no network.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val uploader: String,
    val duration: Long = 0L,
    val thumbnailUrl: String = "",
    val thumbnailPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** JSON-serialized List<SponsorBlockSegment>; null if not yet fetched. */
    val sponsorBlockSegmentsJson: String? = null,
    @ColumnInfo(defaultValue = "VIDEO")
    val kind: DownloadKind = DownloadKind.VIDEO,
    @ColumnInfo(defaultValue = "")
    val channelId: String = "",
    @ColumnInfo(defaultValue = "")
    val description: String = "",
    /** ISO `YYYY-MM-DD`. */
    val releaseDate: String? = null,
    @ColumnInfo(defaultValue = "0")
    val viewCount: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val likeCount: Long = 0L,
    val album: String? = null,
    val albumId: String? = null,
    /** JSON list of the track's artists, each a name and an optional channel id. */
    val artistsJson: String? = null,
    val trackNumber: Int? = null,
    /** The serialized request, so a retry or a resume after the app was killed asks for the same thing. */
    val requestJson: String? = null,
)
