package io.github.aedev.flow.data.local

/** How large a video thumbnail is fetched. HIGH is what every card loaded before this was a choice. */
enum class ThumbnailQuality {
    HIGH,
    MEDIUM,
    LOW,
    ;

    companion object {
        fun fromName(name: String?): ThumbnailQuality = entries.firstOrNull { it.name == name } ?: HIGH

        fun effective(
            isWifi: Boolean,
            wifi: ThumbnailQuality,
            cellular: ThumbnailQuality,
        ): ThumbnailQuality = if (isWifi) wifi else cellular
    }
}
