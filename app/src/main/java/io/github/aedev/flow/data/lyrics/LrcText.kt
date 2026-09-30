package io.github.aedev.flow.data.lyrics

import java.util.Locale

/** Lyrics as `.lrc` text: `[mm:ss.xx]` lines when [syncedLyrics] has timings, else [plainLyrics] as it is. */
fun lrcText(
    syncedLyrics: List<LyricsEntry>,
    plainLyrics: String?,
): String =
    if (syncedLyrics.isNotEmpty()) {
        syncedLyrics.joinToString("\n") { entry ->
            val minutes = entry.time / 60_000
            val seconds = (entry.time % 60_000) / 1_000
            val hundredths = (entry.time % 1_000) / 10
            String.format(Locale.US, "[%02d:%02d.%02d]%s", minutes, seconds, hundredths, entry.text)
        }
    } else {
        plainLyrics.orEmpty()
    }
