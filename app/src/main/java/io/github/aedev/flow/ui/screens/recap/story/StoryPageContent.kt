package io.github.aedev.flow.ui.screens.recap.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.stats.LedgerAction
import io.github.aedev.flow.data.stats.RankedItem
import io.github.aedev.flow.data.stats.RecapPeriod
import io.github.aedev.flow.data.stats.RecapSummary
import io.github.aedev.flow.data.stats.ViewFormat
import io.github.aedev.flow.ui.components.shared.FlowMorphingPortrait
import io.github.aedev.flow.ui.components.shared.FlowPopIn
import io.github.aedev.flow.ui.components.stats.StatCalendar
import io.github.aedev.flow.ui.components.stats.StatClock
import io.github.aedev.flow.ui.components.stats.StatLegendItem
import io.github.aedev.flow.ui.components.stats.StatSplitRing
import io.github.aedev.flow.ui.components.stats.rememberStatEntrance
import io.github.aedev.flow.ui.components.stats.spentTimeLabel
import io.github.aedev.flow.ui.screens.recap.labels
import io.github.aedev.flow.ui.screens.recap.playsLabel
import io.github.aedev.flow.ui.screens.recap.readable
import io.github.aedev.flow.ui.screens.recap.timesLabel
import io.github.aedev.flow.ui.screens.recap.viewsLabel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val StoryMaxWidth = 560.dp
private val StorySpacing = 20.dp
private val SmallSpacing = 10.dp
private val HeroPortrait = 176.dp
private val SmallPortrait = 56.dp
private val ArtworkSize = 52.dp
private val BadgeSize = 88.dp
private val PillPaddingH = 18.dp
private val PillPaddingV = 10.dp
private const val STORY_LIST = 5
private const val NEW_FACES = 8
private const val FORMAT_SECONDARY = 0.62f
private const val FORMAT_TERTIARY = 0.34f

/** The words, figures and portraits of one story page, centred in a readable column. */
@Composable
internal fun StoryPageContent(
    page: StoryPage,
    summary: RecapSummary,
    periodLabel: String,
    tint: StoryTint,
    locale: Locale,
) {
    val pop = rememberPagePop()
    Column(
        modifier = Modifier.widthIn(max = StoryMaxWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StorySpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (page) {
            StoryPage.Intro -> {
                Eyebrow(stringResource(R.string.recap_story_intro, periodLabel), tint)
                Hero(CountUp(summary.combined.totalMs, pop) { spentTimeLabel(it) })
                Body(pluralStringResource(R.plurals.recap_story_intro_body, summary.combined.activeDays, summary.combined.activeDays))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SmallSpacing, Alignment.CenterHorizontally)) {
                    var index = 0
                    if (!summary.video.isEmpty) {
                        StatPill(summary.video.views.toString(), stringResource(R.string.recap_views), tint, index++)
                    }
                    if (!summary.music.isEmpty) {
                        StatPill(summary.music.plays.toString(), stringResource(R.string.recap_plays), tint, index++)
                    }
                    StatPill(summary.combined.longestStreak.toString(), stringResource(R.string.recap_streak), tint, index)
                }
            }

            is StoryPage.TopChannel -> {
                FlowMorphingPortrait(page.channel.imageUrl, page.channel.name, HeroPortrait, tint.accent, tint.container)
                Eyebrow(stringResource(R.string.recap_story_top_channel), tint)
                Headline(page.channel.name)
                val watched = spentTimeLabel(page.channel.durationMs)
                Body(stringResource(R.string.recap_story_top_channel_body, viewsLabel(page.channel), watched))
                FaceRow(page.runnersUp, tint)
            }

            is StoryPage.OnRepeat -> {
                RepeatArtwork(page.video, tint)
                Eyebrow(stringResource(R.string.recap_story_repeat), tint)
                Headline(page.video.name.ifBlank { stringResource(R.string.recap_unnamed_item) })
                if (page.video.detail.isNotBlank()) Body(page.video.detail)
            }

            StoryPage.Topics -> {
                Topics(summary, tint)
            }

            StoryPage.Clock -> {
                Eyebrow(stringResource(R.string.recap_story_clock), tint)
                summary.insights.firstOrNull()?.let { insight ->
                    val (title, body) = insight.labels()
                    Headline(stringResource(title))
                    Body(stringResource(body))
                }
                val entrance = rememberStatEntrance(page)
                StatClock(summary.combined.hourCounts, entrance, stringResource(R.string.recap_clock_subtitle))
            }

            StoryPage.Streak -> {
                Streak(summary, tint, locale)
            }

            is StoryPage.TopArtist -> {
                FlowMorphingPortrait(page.artist.imageUrl, page.artist.name, HeroPortrait, tint.accent, tint.container)
                Eyebrow(stringResource(R.string.recap_story_artist), tint)
                Headline(page.artist.name)
                Body(stringResource(R.string.recap_story_artist_body, playsLabel(page.artist), page.sharePercent))
            }

            StoryPage.Songs -> {
                Eyebrow(stringResource(R.string.recap_story_songs), tint)
                ArtworkList(summary.music.topTracks.take(STORY_LIST), tint) { playsLabel(it) }
            }

            StoryPage.NewToYou -> {
                NewToYou(summary, tint)
            }

            StoryPage.PassedOn -> {
                PassedOn(summary, tint)
            }

            StoryPage.Formats -> {
                Formats(summary)
            }

            StoryPage.Sponsor -> {
                Eyebrow(stringResource(R.string.recap_story_sponsor), tint)
                Hero(CountUp(summary.video.sponsorSavedMs, pop) { spentTimeLabel(it) })
                Body(stringResource(R.string.recap_sponsor_label))
            }

            StoryPage.Summary -> {
                Unit
            }
        }
    }
}

@Composable
private fun StatPill(
    value: String,
    label: String,
    tint: StoryTint,
    index: Int,
) {
    FlowPopIn(index) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = tint.raised, contentColor = tint.onContainer) {
            Column(
                modifier = Modifier.padding(horizontal = PillPaddingH, vertical = PillPaddingV),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun FaceRow(
    items: List<RankedItem>,
    tint: StoryTint,
) {
    if (items.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(SmallSpacing), verticalAlignment = Alignment.Top) {
        items.forEachIndexed { index, item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = SmallPortrait * 1.6f)) {
                FlowMorphingPortrait(item.imageUrl, item.name, SmallPortrait, tint.accent, tint.container, delayIndex = index + 2)
                Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RepeatArtwork(
    video: RankedItem,
    tint: StoryTint,
) {
    Box(Modifier.widthIn(max = StoryMaxWidth).fillMaxWidth()) {
        FlowPopIn(0) {
            AsyncImage(
                model = video.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT).clip(MaterialTheme.shapes.extraLarge),
            )
        }
        FlowPopIn(3, Modifier.align(Alignment.TopEnd).offset(x = SmallSpacing, y = -SmallSpacing)) {
            Surface(
                shape = MaterialShapes.SoftBurst.toShape(),
                color = tint.accent,
                contentColor = tint.container,
                modifier = Modifier.size(BadgeSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("×${video.count}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

private const val VIDEO_ASPECT = 16f / 9f

@Composable
private fun Topics(
    summary: RecapSummary,
    tint: StoryTint,
) {
    val topics = summary.video.topTopics
    Eyebrow(stringResource(R.string.recap_story_topics), tint)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(SmallSpacing, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(SmallSpacing),
    ) {
        topics.take(STORY_LIST + 2).forEachIndexed { index, topic ->
            FlowPopIn(index) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (index == 0) tint.accent else tint.raised,
                    contentColor = if (index == 0) tint.container else tint.onContainer,
                ) {
                    Text(
                        text = topic.name.readable(),
                        style = if (index == 0) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = PillPaddingH, vertical = PillPaddingV),
                    )
                }
            }
        }
    }
    if (summary.video.newTopics.isNotEmpty()) {
        Body(stringResource(R.string.recap_topics_new, summary.video.newTopics.joinToString { it.readable() }))
    }
}

@Composable
private fun Streak(
    summary: RecapSummary,
    tint: StoryTint,
    locale: Locale,
) {
    val activity = summary.combined
    val pop = rememberPagePop()
    Eyebrow(stringResource(R.string.recap_story_streak), tint)
    Hero(CountUp(activity.longestStreak.toLong(), pop) { pluralStringResource(R.plurals.recap_days, it.toInt(), it.toInt()) })
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
        val entrance = rememberStatEntrance(period)
        StatCalendar(
            month = period.month,
            dayMs = activity.dayMs,
            entrance = entrance,
            description = stringResource(R.string.recap_calendar_title),
            locale = locale,
        )
    }
}

@Composable
private fun NewToYou(
    summary: RecapSummary,
    tint: StoryTint,
) {
    val channels = summary.video.discoveredChannels
    val artists = summary.music.discoveredArtists
    Eyebrow(stringResource(R.string.recap_story_new), tint)
    if (channels.isNotEmpty()) Headline(pluralStringResource(R.plurals.recap_new_channels, channels.size, channels.size))
    if (artists.isNotEmpty()) Headline(pluralStringResource(R.plurals.recap_new_artists, artists.size, artists.size))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(SmallSpacing, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(SmallSpacing),
    ) {
        (channels + artists).take(NEW_FACES).forEachIndexed { index, item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = SmallPortrait * 1.6f)) {
                FlowMorphingPortrait(item.imageUrl, item.name, SmallPortrait, tint.accent, tint.container, delayIndex = index)
                Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PassedOn(
    summary: RecapSummary,
    tint: StoryTint,
) {
    val video = summary.video
    Eyebrow(stringResource(R.string.recap_story_passed), tint)
    Row(horizontalArrangement = Arrangement.spacedBy(SmallSpacing)) {
        StatPill(video.dislikes.size.toString(), stringResource(R.string.recap_dislikes), tint, 0)
        StatPill((video.actions[LedgerAction.NOT_INTERESTED] ?: 0).toString(), stringResource(R.string.recap_not_interested), tint, 1)
    }
    val skipped = video.skippedVideos.ifEmpty { summary.music.skippedTracks }
    if (skipped.isNotEmpty()) ArtworkList(skipped.take(SHORT_LIST), tint) { timesLabel(it) }
}

private const val SHORT_LIST = 3

@Composable
private fun Formats(summary: RecapSummary) {
    val primary = MaterialTheme.colorScheme.primary
    val shares =
        listOf(
            Triple(R.string.recap_format_long, summary.video.formatMs[ViewFormat.LONG] ?: 0L, primary),
            Triple(R.string.recap_format_shorts, summary.video.formatMs[ViewFormat.SHORT] ?: 0L, primary.copy(alpha = FORMAT_SECONDARY)),
            Triple(R.string.recap_format_live, summary.video.formatMs[ViewFormat.LIVE] ?: 0L, primary.copy(alpha = FORMAT_TERTIARY)),
            Triple(R.string.recap_format_music, summary.music.activity.totalMs, MaterialTheme.colorScheme.tertiary),
        ).filter { it.second > 0L }
    val labels = shares.map { (label, ms, _) -> stringResource(R.string.recap_format_share, stringResource(label), spentTimeLabel(ms)) }
    val entrance = rememberStatEntrance(StoryPage.Formats)
    Text(stringResource(R.string.recap_story_formats), style = MaterialTheme.typography.titleLarge)
    StatSplitRing(shares.map { it.second.toFloat() to it.third }, entrance, labels.joinToString())
    Column(verticalArrangement = Arrangement.spacedBy(SmallSpacing)) {
        shares.zip(labels).forEach { (share, label) -> StatLegendItem(label, share.third) }
    }
}

/** A short ranked list with artwork beside each row, landing one row at a time. */
@Composable
private fun ArtworkList(
    items: List<RankedItem>,
    tint: StoryTint,
    value: @Composable (RankedItem) -> String,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SmallSpacing)) {
        items.forEachIndexed { index, item ->
            FlowPopIn(index) {
                Surface(shape = MaterialTheme.shapes.large, color = tint.raised, contentColor = tint.onContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(SmallSpacing),
                        horizontalArrangement = Arrangement.spacedBy(SmallSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.titleLarge, color = tint.accent, fontWeight = FontWeight.Bold)
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(ArtworkSize).clip(MaterialTheme.shapes.medium),
                        )
                        Text(
                            text = item.name.ifBlank { stringResource(R.string.recap_unnamed_item) },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(value(item), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun Eyebrow(
    text: String,
    tint: StoryTint,
) = Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    color = tint.accent,
    fontWeight = FontWeight.SemiBold,
    textAlign = TextAlign.Center,
)

@Composable
private fun Hero(text: String) =
    Text(text, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)

@Composable
private fun Headline(text: String) =
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
