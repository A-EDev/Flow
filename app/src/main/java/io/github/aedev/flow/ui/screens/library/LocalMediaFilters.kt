package io.github.aedev.flow.ui.screens.library

import androidx.annotation.StringRes
import io.github.aedev.flow.R
import io.github.aedev.flow.data.localmedia.LocalMediaItem

private const val MINUTE_MS = 60_000L
private const val SHORT_MAX_MS = 4 * MINUTE_MS
private const val MEDIUM_MAX_MS = 20 * MINUTE_MS
private const val NEW_WINDOW_MS = 7 * 24 * 60 * MINUTE_MS
private const val WATCHED_FRACTION = 0.9f

enum class LocalSort(
    @param:StringRes val labelRes: Int,
) {
    DATE_ADDED(R.string.local_sort_date_added),
    NAME(R.string.local_sort_name),
    LENGTH(R.string.local_sort_length),
    SIZE(R.string.local_sort_size),
    RECENTLY_PLAYED(R.string.local_sort_recently_played),
}

/** YouTube search's length bands. */
enum class LengthFilter(
    @param:StringRes val labelRes: Int,
) {
    ANY(R.string.local_filter_length_any),
    SHORT(R.string.local_filter_length_short),
    MEDIUM(R.string.local_filter_length_medium),
    LONG(R.string.local_filter_length_long),
    ;

    fun matches(durationMs: Long): Boolean =
        when (this) {
            ANY -> true
            SHORT -> durationMs < SHORT_MAX_MS
            MEDIUM -> durationMs in SHORT_MAX_MS..MEDIUM_MAX_MS
            LONG -> durationMs > MEDIUM_MAX_MS
        }
}

/** By the video's short side, so a portrait 1080x1920 clip counts as Full HD like a landscape one. */
enum class QualityFilter(
    @param:StringRes val labelRes: Int,
    private val minShortSide: Int,
    private val maxShortSide: Int,
) {
    ANY(R.string.local_filter_quality_any, 0, Int.MAX_VALUE),
    SD(R.string.local_filter_quality_sd, 0, 719),
    HD(R.string.local_filter_quality_hd, 720, 1079),
    FULL_HD(R.string.local_filter_quality_full_hd, 1080, 2159),
    UHD(R.string.local_filter_quality_4k, 2160, Int.MAX_VALUE),
    ;

    fun matches(item: LocalMediaItem): Boolean {
        if (this == ANY) return true
        val shortSide = minOf(item.width, item.height).takeIf { it > 0 } ?: return false
        return shortSide in minShortSide..maxShortSide
    }
}

/** Where a file stands with the viewer, from its local watch history. */
enum class WatchState { NEW, UNWATCHED, IN_PROGRESS, WATCHED }

data class LocalFilters(
    val query: String = "",
    val sort: LocalSort = LocalSort.DATE_ADDED,
    val length: LengthFilter = LengthFilter.ANY,
    val quality: QualityFilter = QualityFilter.ANY,
    val portraitOnly: Boolean = false,
    val unwatchedOnly: Boolean = false,
) {
    val isFiltering: Boolean
        get() = length != LengthFilter.ANY || quality != QualityFilter.ANY || portraitOnly || unwatchedOnly
}

/** Progress and the last time each file was played, keyed by its `local_` id. */
data class LocalPlayback(
    val fraction: Map<String, Float> = emptyMap(),
    val lastPlayedMs: Map<String, Long> = emptyMap(),
)

fun LocalMediaItem.watchState(
    playback: LocalPlayback,
    nowMs: Long,
): WatchState {
    val fraction = playback.fraction[mediaId]
    return when {
        fraction != null && fraction >= WATCHED_FRACTION -> WatchState.WATCHED
        fraction != null && fraction > 0f -> WatchState.IN_PROGRESS
        mediaId !in playback.lastPlayedMs && nowMs - dateAddedMs in 0..NEW_WINDOW_MS -> WatchState.NEW
        else -> WatchState.UNWATCHED
    }
}

fun List<LocalMediaItem>.applyLocalFilters(
    filters: LocalFilters,
    playback: LocalPlayback,
    nowMs: Long,
): List<LocalMediaItem> {
    val needle = filters.query.trim()
    return asSequence()
        .filter { item ->
            needle.isEmpty() ||
                listOf(item.title, item.folderName, item.artist, item.album).any { it.contains(needle, ignoreCase = true) }
        }.filter { filters.length.matches(it.durationMs) }
        .filter { !it.isVideo || filters.quality.matches(it) }
        .filter { !filters.portraitOnly || it.isPortrait }
        .filter { item ->
            !filters.unwatchedOnly || item.watchState(playback, nowMs).let { it == WatchState.NEW || it == WatchState.UNWATCHED }
        }.sortedWith(
            when (filters.sort) {
                LocalSort.DATE_ADDED -> {
                    compareByDescending { it.dateAddedMs }
                }

                LocalSort.NAME -> {
                    compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                }

                LocalSort.LENGTH -> {
                    compareByDescending { it.durationMs }
                }

                LocalSort.SIZE -> {
                    compareByDescending { it.sizeBytes }
                }

                LocalSort.RECENTLY_PLAYED -> {
                    compareByDescending<LocalMediaItem> {
                        playback.lastPlayedMs[it.mediaId] ?: 0L
                    }.thenByDescending { it.dateAddedMs }
                }
            },
        ).toList()
}

/** A folder of files, with the newest one as its cover. */
data class LocalFolder(
    val id: String,
    val name: String,
    val items: List<LocalMediaItem>,
) {
    val sizeBytes: Long get() = items.sumOf { it.sizeBytes }
}

fun List<LocalMediaItem>.folders(): List<LocalFolder> =
    groupBy { it.folderId }
        .map { (id, items) -> LocalFolder(id = id, name = items.first().folderName, items = items.sortedByDescending { it.dateAddedMs }) }
        .sortedByDescending { folder -> folder.items.first().dateAddedMs }

/** Files started and not finished, most recently played first. */
fun List<LocalMediaItem>.continueWatching(
    playback: LocalPlayback,
    nowMs: Long,
): List<LocalMediaItem> =
    filter { it.watchState(playback, nowMs) == WatchState.IN_PROGRESS }
        .sortedByDescending { playback.lastPlayedMs[it.mediaId] ?: 0L }
