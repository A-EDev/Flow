package io.github.aedev.flow.ui.screens.music

import android.content.Context
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import java.util.Locale

/** The "Playing from" label for a queue: known shelf keys become their section names, the rest is tidied. */
internal fun musicSourceLabel(
    context: Context,
    sourceName: String?,
    track: MusicTrack,
): String {
    val trimmed = sourceName?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return context.getString(R.string.radio_source_template, track.artist)
    }

    val key = trimmed.lowercase(Locale.getDefault())
    val mapped =
        when (key) {
            "listen_again" -> context.getString(R.string.section_listen_again)
            "on_repeat" -> context.getString(R.string.section_on_repeat)
            "rotation" -> context.getString(R.string.source_your_rotation)
            "rediscover" -> context.getString(R.string.section_rediscover)
            "deep_cuts" -> context.getString(R.string.section_deep_cuts)
            "daily_discover" -> context.getString(R.string.section_daily_discover)
            "quick_picks" -> context.getString(R.string.section_quick_picks)
            "speed_dial", "speed_dial_shuffle" -> context.getString(R.string.section_speed_dial)
            "recommended" -> context.getString(R.string.section_recommended)
            "recently_played" -> context.getString(R.string.section_recently_played)
            "music_videos" -> context.getString(R.string.section_music_videos)
            "music_videos_for_you" -> context.getString(R.string.section_music_videos_for_you)
            "live_performances" -> context.getString(R.string.section_live_performances)
            "new_releases" -> context.getString(R.string.section_new_releases)
            "popular_artists" -> context.getString(R.string.section_popular_artists)
            "mixed_for_you" -> context.getString(R.string.section_mixed_for_you)
            "moods_and_genres" -> context.getString(R.string.section_moods_and_genres)
            "mood_and_genres" -> context.getString(R.string.section_mood_and_genres)
            "from_the_community" -> context.getString(R.string.section_from_the_community)
            "top_albums" -> context.getString(R.string.section_top_albums)
            "top_picks" -> context.getString(R.string.top_picks_for_you)
            "trending" -> context.getString(R.string.trending)
            else -> null
        }

    if (mapped != null) return mapped

    if (key.startsWith("genre_")) {
        val genre = trimmed.substringAfter("genre_", "").replace('_', ' ').trim()
        if (genre.isNotBlank()) return genre
    }

    return cleanSource(trimmed)
}

private fun cleanSource(value: String): String =
    value
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        }
