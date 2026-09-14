package io.github.aedev.flow.data.local

/**
 * Everything the search feed can be narrowed by. Every field maps to a server-side `params` token —
 * nothing here is applied to the results after they arrive.
 */
data class SearchFilter(
    val contentType: ContentType = ContentType.ALL,
    val duration: Duration = Duration.ANY,
    val uploadDate: UploadDate = UploadDate.ANY,
    val sortType: SortType = SortType.RELEVANCE,
    val features: Set<SearchFeature> = emptySet(),
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
