package io.github.aedev.flow.ui.components.library

import androidx.annotation.StringRes
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.utils.relativedate.RelativeUploadDateParser

private const val TIMESTAMP_TOLERANCE_MS = 30L * 60L * 1000L

enum class PlaylistSortOrder(
    val storageValue: String,
    @param:StringRes val labelRes: Int,
) {
    MANUAL("manual", R.string.playlist_sort_manual),
    DATE_ADDED_NEWEST("date_added_newest", R.string.playlist_sort_date_added_newest),
    DATE_ADDED_OLDEST("date_added_oldest", R.string.playlist_sort_date_added_oldest),
    MOST_POPULAR("most_popular", R.string.playlist_sort_most_popular),
    DATE_PUBLISHED_NEWEST("date_published_newest", R.string.playlist_sort_date_published_newest),
    DATE_PUBLISHED_OLDEST("date_published_oldest", R.string.playlist_sort_date_published_oldest),
    ;

    /** Rows show when each video was added only while the list is ordered by it. */
    val showsDateAdded: Boolean
        get() = this == DATE_ADDED_NEWEST || this == DATE_ADDED_OLDEST

    companion object {
        fun fromStorageValue(value: String?): PlaylistSortOrder = entries.firstOrNull { it.storageValue == value } ?: MANUAL

        /**
         * The orders a playlist has data for: a YouTube playlist carries no date a video was added,
         * and likes have no order of their own besides when each was liked.
         */
        fun availableFor(
            isLocalPlaylist: Boolean,
            isLikes: Boolean = false,
        ): List<PlaylistSortOrder> =
            when {
                isLikes -> entries.filterNot { it == MANUAL }
                isLocalPlaylist -> entries
                else -> entries.filterNot { it == DATE_ADDED_NEWEST || it == DATE_ADDED_OLDEST }
            }

        fun defaultFor(isLikes: Boolean): PlaylistSortOrder = if (isLikes) DATE_ADDED_NEWEST else MANUAL
    }
}

internal fun List<Video>.sortedForPlaylist(sortOrder: PlaylistSortOrder): List<Video> =
    when (sortOrder) {
        PlaylistSortOrder.MANUAL,
        PlaylistSortOrder.DATE_ADDED_NEWEST,
        -> this

        PlaylistSortOrder.DATE_ADDED_OLDEST -> asReversed()

        PlaylistSortOrder.MOST_POPULAR -> sortedByDescending { it.viewCount }

        PlaylistSortOrder.DATE_PUBLISHED_NEWEST -> sortedByPublishDate(descending = true)

        PlaylistSortOrder.DATE_PUBLISHED_OLDEST -> sortedByPublishDate(descending = false)
    }

private fun List<Video>.sortedByPublishDate(descending: Boolean): List<Video> {
    val now = System.currentTimeMillis()
    val keyed = map { it to it.effectivePlaylistUploadTimestamp(now) }
    val ordered = if (descending) keyed.sortedByDescending { it.second } else keyed.sortedBy { it.second }
    return ordered.map { it.first }
}

private fun Video.effectivePlaylistUploadTimestamp(now: Long): Long {
    val relative = RelativeUploadDateParser.parse(uploadDate, YouTube.locale.hl, now)
    if (timestamp <= 0L) return relative ?: 0L
    if (relative == null) return timestamp
    return if (timestamp < relative - TIMESTAMP_TOLERANCE_MS) timestamp else relative
}
