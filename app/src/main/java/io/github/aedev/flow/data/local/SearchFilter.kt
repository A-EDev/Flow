package io.github.aedev.flow.data.local

/**
 * Everything the search feed can be narrowed by. Every field maps to a server-side `params` token
 * except [watchState], which YouTube only honours for a signed-in account, so it is applied to each
 * page against local watch history instead.
 */
data class SearchFilter(
    val contentType: ContentType = ContentType.ALL,
    val duration: Duration = Duration.ANY,
    val uploadDate: UploadDate = UploadDate.ANY,
    val sortType: SortType = SortType.RELEVANCE,
    val features: Set<SearchFeature> = emptySet(),
    val watchState: WatchState = WatchState.ANY,
) {
    val isDefault: Boolean
        get() = this == DEFAULT

    /** How many narrowing choices are active, for the badge on the filter button. */
    val activeCount: Int
        get() =
            listOf(
                contentType != ContentType.ALL,
                duration != Duration.ANY,
                uploadDate != UploadDate.ANY,
                sortType != SortType.RELEVANCE,
                watchState != WatchState.ANY,
            ).count { it } + features.size

    companion object {
        val DEFAULT = SearchFilter()
    }
}

enum class ContentType {
    ALL,
    VIDEOS,
    SHORTS,
    CHANNELS,
    PLAYLISTS,
    MOVIES,
    LIVE,
}

/**
 * YouTube's own dialog offers exactly these two. Sort by upload date and by rating were retired —
 * their tokens still parse but no longer reorder anything (verified live 2026-09-14), so recency is
 * an [UploadDate] filter instead.
 */
enum class SortType {
    RELEVANCE,
    VIEW_COUNT,
}

/** YouTube moved these boundaries from four minutes to three when Shorts grew to three minutes. */
enum class Duration {
    ANY,
    UNDER_3_MINUTES,
    THREE_TO_20_MINUTES,
    OVER_20_MINUTES,
}

enum class UploadDate {
    ANY,
    LAST_HOUR,
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    THIS_YEAR,
}

/** Read against the user's watched threshold, the same line Home and Subscriptions hide by. */
enum class WatchState {
    ANY,
    UNWATCHED,
    WATCHED,
}

enum class SearchFeature {
    HD,
    FOUR_K,
    HDR,
    SUBTITLES,
    CREATIVE_COMMONS,
    THREE_SIXTY,
    VR180,
    THREE_D,
    LOCATION,
    PURCHASED,
}
