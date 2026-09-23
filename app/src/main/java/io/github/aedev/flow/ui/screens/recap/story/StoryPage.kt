package io.github.aedev.flow.ui.screens.recap.story

import io.github.aedev.flow.data.stats.RankedItem
import io.github.aedev.flow.data.stats.RecapSummary

/** One card of the recap story. Each exists only when its period has the data to fill it. */
internal sealed interface StoryPage {
    data object Intro : StoryPage

    data class TopChannel(
        val channel: RankedItem,
        val runnersUp: List<RankedItem>,
    ) : StoryPage

    data class OnRepeat(
        val video: RankedItem,
    ) : StoryPage

    data object Topics : StoryPage

    data object Clock : StoryPage

    data object Streak : StoryPage

    data class TopArtist(
        val artist: RankedItem,
        val sharePercent: Int,
    ) : StoryPage

    data object Songs : StoryPage

    data object NewToYou : StoryPage

    data object PassedOn : StoryPage

    data object Formats : StoryPage

    data object Sponsor : StoryPage

    data object Summary : StoryPage
}

private const val MIN_REPEAT = 2
private const val RUNNERS_UP = 4
private const val MIN_STREAK = 2
private const val PERCENT = 100

/** The story for [summary]: always an intro and a summary, and between them what the period earned. */
internal fun storyPages(summary: RecapSummary): List<StoryPage> {
    val video = summary.video
    val music = summary.music
    return buildList {
        add(StoryPage.Intro)
        video.topChannels.firstOrNull()?.let { add(StoryPage.TopChannel(it, video.topChannels.drop(1).take(RUNNERS_UP))) }
        video.topVideos
            .firstOrNull()
            ?.takeIf { it.count >= MIN_REPEAT }
            ?.let { add(StoryPage.OnRepeat(it)) }
        if (video.topTopics.isNotEmpty()) add(StoryPage.Topics)
        if (summary.combined.hourCounts.any { it > 0 }) add(StoryPage.Clock)
        if (summary.combined.longestStreak >= MIN_STREAK) add(StoryPage.Streak)
        music.topArtists.firstOrNull()?.let { artist ->
            val share = if (music.plays > 0) artist.count * PERCENT / music.plays else 0
            add(StoryPage.TopArtist(artist, share))
        }
        if (music.topTracks.size >= MIN_REPEAT) add(StoryPage.Songs)
        if (video.discoveredChannels.isNotEmpty() || music.discoveredArtists.isNotEmpty()) add(StoryPage.NewToYou)
        if (video.dislikes.isNotEmpty() || video.skippedVideos.isNotEmpty() || music.skippedTracks.isNotEmpty()) add(StoryPage.PassedOn)
        if (video.formatMs.size + (if (music.isEmpty) 0 else 1) >= MIN_REPEAT) add(StoryPage.Formats)
        if (video.sponsorSavedMs > 0L) add(StoryPage.Sponsor)
        add(StoryPage.Summary)
    }
}
