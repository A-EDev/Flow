package io.github.aedev.flow.ui.screens.library

import androidx.annotation.StringRes
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.video.DownloadedVideo

/** How the Downloads lists are ordered. */
enum class DownloadSort(
    @param:StringRes val labelRes: Int,
) {
    NEWEST(R.string.downloads_sort_newest),
    OLDEST(R.string.downloads_sort_oldest),
    LARGEST(R.string.downloads_sort_largest),
    TITLE(R.string.downloads_sort_title),
    CHANNEL(R.string.downloads_sort_channel),
}

/** Space the downloads take, and what is left on the volume they are saved to. */
data class DownloadStorage(
    val videoBytes: Long = 0L,
    val musicBytes: Long = 0L,
    val freeBytes: Long = 0L,
) {
    val usedBytes: Long get() = videoBytes + musicBytes
}

internal fun List<DownloadedVideo>.filterAndSort(
    query: String,
    sort: DownloadSort,
): List<DownloadedVideo> =
    filter { matches(query, it.video.title, it.video.channelName) }
        .sortedWith(
            when (sort) {
                DownloadSort.NEWEST -> compareByDescending { it.downloadedAt }
                DownloadSort.OLDEST -> compareBy { it.downloadedAt }
                DownloadSort.LARGEST -> compareByDescending { it.fileSize }
                DownloadSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.video.title }
                DownloadSort.CHANNEL -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.video.channelName }
            },
        )

internal fun List<DownloadedTrack>.filterAndSortTracks(
    query: String,
    sort: DownloadSort,
): List<DownloadedTrack> =
    filter { matches(query, it.track.title, it.track.artist, it.track.album) }
        .sortedWith(
            when (sort) {
                DownloadSort.NEWEST -> compareByDescending { it.downloadedAt }
                DownloadSort.OLDEST -> compareBy { it.downloadedAt }
                DownloadSort.LARGEST -> compareByDescending { it.fileSize }
                DownloadSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.track.title }
                DownloadSort.CHANNEL -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.track.artist }
            },
        )

private fun matches(
    query: String,
    vararg fields: String,
): Boolean {
    val needle = query.trim()
    return needle.isEmpty() || fields.any { it.contains(needle, ignoreCase = true) }
}
