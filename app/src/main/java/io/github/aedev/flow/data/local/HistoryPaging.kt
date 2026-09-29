package io.github.aedev.flow.data.local

import io.github.aedev.flow.data.local.dao.WatchHistoryDao
import io.github.aedev.flow.data.local.dao.WatchProgress
import io.github.aedev.flow.data.local.entity.WatchHistoryEntity

internal const val HISTORY_PAGE_SIZE = 500
internal const val PROGRESS_PAGE_SIZE = 2_000

/**
 * Reads every row newest first, one keyset page at a time. A row that moves between two pages
 * is kept once: the table can change mid-read, and the next invalidation reads it again anyway.
 */
internal suspend fun <T> readAllPages(
    pageSize: Int,
    readPage: suspend (beforeTimestamp: Long, afterVideoId: String, limit: Int) -> List<T>,
    timestamp: (T) -> Long,
    videoId: (T) -> String,
): List<T> {
    val rows = ArrayList<T>()
    val seen = HashSet<String>()
    var beforeTimestamp = Long.MAX_VALUE
    var afterVideoId = ""
    while (true) {
        val page = readPage(beforeTimestamp, afterVideoId, pageSize)
        page.filterTo(rows) { seen.add(videoId(it)) }
        if (page.size < pageSize) return rows
        val last = page.last()
        beforeTimestamp = timestamp(last)
        afterVideoId = videoId(last)
    }
}

internal suspend fun WatchHistoryDao.readHistory(
    isMusic: Int = WatchHistoryDao.ANY,
    isLocal: Int = WatchHistoryDao.ANY,
): List<WatchHistoryEntity> =
    readAllPages(
        pageSize = HISTORY_PAGE_SIZE,
        readPage = { before, after, limit -> getHistoryPage(isMusic, isLocal, before, after, limit) },
        timestamp = { it.timestamp },
        videoId = { it.videoId },
    )

internal suspend fun WatchHistoryDao.readProgress(
    isMusic: Int = WatchHistoryDao.ANY,
    isLocal: Int = WatchHistoryDao.ANY,
): List<WatchProgress> =
    readAllPages(
        pageSize = PROGRESS_PAGE_SIZE,
        readPage = { before, after, limit -> getProgressPage(isMusic, isLocal, before, after, limit) },
        timestamp = { it.timestamp },
        videoId = { it.videoId },
    )
