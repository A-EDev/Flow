package com.flow.youtube.data.lyrics

/**
 * Available lyrics providers for user selection.
 */
enum class PreferredLyricsProvider(val displayName: String) {
    LRCLIB("LrcLib"),
    BETTER_LYRICS("Better Lyrics"),
    SIMPMUSIC("SimpMusic");
    
    companion object {
        fun fromString(name: String): PreferredLyricsProvider =
            values().find { it.name == name } ?: LRCLIB
    }
}
