package io.github.aedev.flow.ui.screens.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.stats.LedgerAction
import io.github.aedev.flow.data.stats.RankedItem
import io.github.aedev.flow.data.stats.RecapPeriod
import io.github.aedev.flow.data.stats.RecapSummary
import io.github.aedev.flow.data.stats.ViewFormat
import io.github.aedev.flow.ui.components.shared.FlowSegmentedGap
import io.github.aedev.flow.ui.components.shared.flowRowGroupShape
import io.github.aedev.flow.ui.components.stats.StatBigNumber
import io.github.aedev.flow.ui.components.stats.StatCalendar
import io.github.aedev.flow.ui.components.stats.StatClock
import io.github.aedev.flow.ui.components.stats.StatEntrance
import io.github.aedev.flow.ui.components.stats.StatRankRow
import io.github.aedev.flow.ui.components.stats.StatSplitRing
import io.github.aedev.flow.ui.components.stats.spentTimeLabel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val StoryMaxWidth = 520.dp
private val StorySpacing = 20.dp
private val SmallSpacing = 8.dp
private val SummaryPadding = 24.dp
private const val STORY_LIST = 5
private const val FORMAT_SECONDARY = 0.62f
private const val FORMAT_TERTIARY = 0.34f

/** The words and the visual of one story page, centred in a readable column. */
@Composable
internal fun StoryPageContent(
    page: StoryPage,
    summary: RecapSummary,
    periodLabel: String,
    entrance: StatEntrance,
    locale: Locale,
) {
    Column(
        modifier = Modifier.widthIn(max = StoryMaxWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StorySpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (page) {
            StoryPage.Intro -> {
                Intro(summary, periodLabel)
            }

            is StoryPage.TopChannel -> {
                TopChannel(page, summary)
            }

            is StoryPage.OnRepeat -> {
                Eyebrow(stringResource(R.string.recap_story_repeat))
                Headline(page.video.name)
                Body(stringResource(R.string.recap_story_repeat_body, timesLabel(page.video)))
                if (page.video.detail.isNotBlank()) Body(page.video.detail)
            }

            StoryPage.Topics -> {
                Topics(summary)
            }

            StoryPage.Clock -> {
                Eyebrow(stringResource(R.string.recap_story_clock))
                summary.insights.firstOrNull()?.let { insight ->
                    val (title, body) = insight.labels()
                    Headline(stringResource(title))
                    Body(stringResource(body))
                }
                StatClock(summary.combined.hourCounts, entrance, stringResource(R.string.recap_clock_subtitle))
            }

            StoryPage.Streak -> {
                Streak(summary, entrance, locale)
            }

            is StoryPage.TopArtist -> {
                Eyebrow(stringResource(R.string.recap_story_artist))
                Headline(page.artist.name)
                Body(stringResource(R.string.recap_story_artist_body, playsLabel(page.artist), page.sharePercent))
            }

            StoryPage.Songs -> {
                Eyebrow(stringResource(R.string.recap_story_songs))
                StoryRanks(summary.music.topTracks.take(STORY_LIST)) { playsLabel(it) }
            }

            StoryPage.NewToYou -> {
                NewToYou(summary)
            }

            StoryPage.PassedOn -> {
                PassedOn(summary)
            }

            StoryPage.Formats -> {
                Formats(summary, entrance)
            }

            StoryPage.Sponsor -> {
                Eyebrow(stringResource(R.string.recap_story_sponsor))
                Headline(spentTimeLabel(summary.video.sponsorSavedMs))
            }

            StoryPage.Summary -> {
                Unit
            }
        }
    }
}

@Composable
private fun Intro(
    summary: RecapSummary,
    periodLabel: String,
) {
    Eyebrow(stringResource(R.string.recap_story_intro, periodLabel))
    Headline(spentTimeLabel(summary.combined.totalMs))
    Body(pluralStringResource(R.plurals.recap_story_intro_body, summary.combined.activeDays, summary.combined.activeDays))
    Row(horizontalArrangement = Arrangement.spacedBy(StorySpacing)) {
        if (!summary.video.isEmpty) StatBigNumber(summary.video.views.toString(), stringResource(R.string.recap_views))
        if (!summary.music.isEmpty) StatBigNumber(summary.music.plays.toString(), stringResource(R.string.recap_plays))
    }
}

@Composable
private fun TopChannel(
    page: StoryPage.TopChannel,
    summary: RecapSummary,
) {
    Eyebrow(stringResource(R.string.recap_story_top_channel))
    Headline(page.channel.name)
    Body(stringResource(R.string.recap_story_top_channel_body, viewsLabel(page.channel), spentTimeLabel(page.channel.durationMs)))
    if (page.runnersUp.isNotEmpty()) {
        StoryRanks(summary.video.topChannels.take(STORY_LIST)) { viewsLabel(it) }
    }
}

@Composable
private fun Topics(summary: RecapSummary) {
    val topics = summary.video.topTopics
    Eyebrow(stringResource(R.string.recap_story_topics))
    Headline(topics.first().name.readable())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(SmallSpacing, Alignment.CenterHorizontally)) {
        topics.drop(1).take(STORY_LIST).forEach { Body(it.name.readable()) }
    }
    if (summary.video.newTopics.isNotEmpty()) {
        Body(stringResource(R.string.recap_topics_new, summary.video.newTopics.joinToString { it.readable() }))
    }
}

@Composable
private fun Streak(
    summary: RecapSummary,
    entrance: StatEntrance,
    locale: Locale,
) {
    val activity = summary.combined
    Eyebrow(stringResource(R.string.recap_story_streak))
    Headline(pluralStringResource(R.plurals.recap_days, activity.longestStreak, activity.longestStreak))
    activity.busiestDay?.let { (day, ms) ->
        Body(
            stringResource(
                R.string.recap_story_streak_body,
                day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
                spentTimeLabel(ms),
            ),
        )
    }
    val period = summary.period
    if (period is RecapPeriod.Month) {
        StatCalendar(period.month, activity.dayMs, entrance, stringResource(R.string.recap_calendar_title), locale = locale)
    }
}

@Composable
private fun NewToYou(summary: RecapSummary) {
    val channels = summary.video.discoveredChannels
    val artists = summary.music.discoveredArtists
    Eyebrow(stringResource(R.string.recap_story_new))
    if (channels.isNotEmpty()) {
        Headline(pluralStringResource(R.plurals.recap_new_channels, channels.size, channels.size))
        Body(channels.take(STORY_LIST).joinToString { it.name })
    }
    if (artists.isNotEmpty()) {
        Headline(pluralStringResource(R.plurals.recap_new_artists, artists.size, artists.size))
        Body(artists.take(STORY_LIST).joinToString { it.name })
    }
}

@Composable
private fun PassedOn(summary: RecapSummary) {
    val video = summary.video
    Eyebrow(stringResource(R.string.recap_story_passed))
    Row(horizontalArrangement = Arrangement.spacedBy(StorySpacing)) {
        StatBigNumber(video.dislikes.size.toString(), stringResource(R.string.recap_dislikes))
        StatBigNumber((video.actions[LedgerAction.NOT_INTERESTED] ?: 0).toString(), stringResource(R.string.recap_not_interested))
    }
    val skipped = video.skippedVideos.ifEmpty { summary.music.skippedTracks }
    if (skipped.isNotEmpty()) StoryRanks(skipped.take(STORY_LIST)) { timesLabel(it) }
}

@Composable
private fun Formats(
    summary: RecapSummary,
    entrance: StatEntrance,
) {
    val primary = MaterialTheme.colorScheme.primary
    val shares =
        listOf(
            Triple(R.string.recap_format_long, summary.video.formatMs[ViewFormat.LONG] ?: 0L, primary),
            Triple(R.string.recap_format_shorts, summary.video.formatMs[ViewFormat.SHORT] ?: 0L, primary.copy(alpha = FORMAT_SECONDARY)),
            Triple(R.string.recap_format_live, summary.video.formatMs[ViewFormat.LIVE] ?: 0L, primary.copy(alpha = FORMAT_TERTIARY)),
            Triple(R.string.recap_format_music, summary.music.activity.totalMs, MaterialTheme.colorScheme.secondary),
        ).filter { it.second > 0L }
    val labels = shares.map { (label, ms, _) -> stringResource(R.string.recap_format_share, stringResource(label), spentTimeLabel(ms)) }
    Eyebrow(stringResource(R.string.recap_story_formats))
    StatSplitRing(shares.map { it.second.toFloat() to it.third }, entrance, labels.joinToString())
    labels.forEach { Body(it) }
}

/** The card the story ends on, and the one that gets shared as an image. */
@Composable
internal fun StorySummaryCard(
    summary: RecapSummary,
    periodLabel: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = StoryMaxWidth).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(SummaryPadding), verticalArrangement = Arrangement.spacedBy(StorySpacing)) {
            Text(stringResource(R.string.recap_story_intro, periodLabel), style = MaterialTheme.typography.titleMedium)
            Text(spentTimeLabel(summary.combined.totalMs), style = MaterialTheme.typography.displayMedium)
            SummaryLine(
                stringResource(R.string.recap_summary_top_channel),
                summary.video.topChannels
                    .firstOrNull()
                    ?.name,
            )
            SummaryLine(
                stringResource(R.string.recap_summary_top_artist),
                summary.music.topArtists
                    .firstOrNull()
                    ?.name,
            )
            SummaryLine(
                stringResource(R.string.recap_summary_top_topic),
                summary.video.topTopics
                    .firstOrNull()
                    ?.name
                    ?.readable(),
            )
            SummaryLine(stringResource(R.string.recap_active_days), summary.combined.activeDays.toString())
            summary.insights.firstOrNull()?.let {
                SummaryLine(
                    stringResource(R.string.recap_insights_title),
                    stringResource(it.labels().first),
                )
            }
            Text(stringResource(R.string.recap_story_made_with), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String?,
) {
    if (value.isNullOrBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StoryRanks(
    items: List<RankedItem>,
    value: @Composable (RankedItem) -> String,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FlowSegmentedGap)) {
        items.forEachIndexed { index, item ->
            StatRankRow(
                rank = index + 1,
                title =
                    item.name.ifBlank {
                        stringResource(R.string.recap_unnamed_item)
                    },
                value = value(item),
                detail = item.detail,
                shape = flowRowGroupShape(index, items.size),
            )
        }
    }
}

@Composable
private fun Eyebrow(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)

@Composable
private fun Headline(text: String) =
    Text(text, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)

@Composable
private fun Body(text: String) =
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
