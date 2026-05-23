package io.github.aedev.flow.data.lyrics

/**
 * Available lyrics providers for user selection.
 */
enum class PreferredLyricsProvider(val displayName: String) {
    LRCLIB("LrcLib"),
    BETTER_LYRICS("Better Lyrics"),
    SIMPMUSIC("SimpMusic"),
    LYRICS_PLUS("LyricsPlus");
    
    companion object {
        fun fromString(name: String): PreferredLyricsProvider =
            values().find { it.name == name } ?: LRCLIB
    }
}
