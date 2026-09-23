package io.github.aedev.flow.ui.screens.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.dao.ChannelVideoCount
import io.github.aedev.flow.data.stats.LedgerAction
import io.github.aedev.flow.data.stats.MusicRecap
import io.github.aedev.flow.data.stats.RankedItem
import io.github.aedev.flow.data.stats.VideoRecap
import io.github.aedev.flow.data.stats.ViewFormat
import io.github.aedev.flow.ui.components.shared.FlowSegmentedGap
import io.github.aedev.flow.ui.components.shared.flowArtistShape
import io.github.aedev.flow.ui.components.shared.flowRowGroupShape
import io.github.aedev.flow.ui.components.stats.StatBigNumber
import io.github.aedev.flow.ui.components.stats.StatCard
import io.github.aedev.flow.ui.components.stats.StatLegendItem
import io.github.aedev.flow.ui.components.stats.StatRankRow
import io.github.aedev.flow.ui.components.stats.StatSplitRing
import io.github.aedev.flow.ui.components.stats.rememberStatEntrance
import io.github.aedev.flow.ui.components.stats.spentTimeLabel
import io.github.aedev.flow.utils.sponsorCategoryLabelRes

private val ChipSpacing = 8.dp
private val SectionSpacing = 16.dp
private const val RANKED_ROWS = 5
private const val SHORT_LIST = 3
private const val FORMAT_SECONDARY = 0.62f
private const val FORMAT_TERTIARY = 0.34f

/** A ranked list of up to five items, the figure beside each chosen by the caller. */
@Composable
internal fun RankCard(
    title: String,
    items: List<RankedItem>,
    value: @Composable (RankedItem) -> String,
    subtitle: String? = null,
    portraits: Boolean = false,
) {
    StatCard(title = title, subtitle = subtitle) {
        RankedRows(items.take(RANKED_ROWS), portraits, value)
    }
}

@Composable
private fun RankedRows(
    items: List<RankedItem>,
    portraits: Boolean = false,
    value: @Composable (RankedItem) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FlowSegmentedGap)) {
        items.forEachIndexed { index, item ->
            StatRankRow(
                rank = index + 1,
                title = item.name.ifBlank { stringResource(R.string.recap_unnamed_item) },
                value = value(item),
                detail = item.detail,
                shape = flowRowGroupShape(index, items.size),
                imageUrl = item.imageUrl,
                imageShape = if (portraits) flowArtistShape() else MaterialTheme.shapes.medium,
            )
        }
    }
}

@Composable
internal fun viewsLabel(item: RankedItem): String = pluralStringResource(R.plurals.recap_views_count, item.count, item.count)

@Composable
internal fun playsLabel(item: RankedItem): String = pluralStringResource(R.plurals.recap_plays_count, item.count, item.count)

@Composable
internal fun timesLabel(item: RankedItem): String = pluralStringResource(R.plurals.recap_times_count, item.count, item.count)

@Composable
internal fun TopicsCard(video: VideoRecap) {
    StatCard(
        title = stringResource(R.string.recap_topics_title),
        subtitle =
            video.newTopics.takeIf { it.isNotEmpty() }?.let {
                stringResource(
                    R.string.recap_topics_new,
                    it.joinToString { topic ->
                        topic.readable()
                    },
                )
            },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ChipSpacing), verticalArrangement = Arrangement.spacedBy(ChipSpacing)) {
            video.topTopics.forEach { topic -> TopicLabel(topic.name.readable()) }
        }
    }
}

/** How time split between long videos, Shorts, live and music. */
@Composable
internal fun FormatsCard(
    video: VideoRecap,
    music: MusicRecap?,
    key: Any,
) {
    val primary = MaterialTheme.colorScheme.primary
    val shares =
        listOf(
            Triple(R.string.recap_format_long, video.formatMs[ViewFormat.LONG] ?: 0L, primary),
            Triple(R.string.recap_format_shorts, video.formatMs[ViewFormat.SHORT] ?: 0L, primary.copy(alpha = FORMAT_SECONDARY)),
            Triple(R.string.recap_format_live, video.formatMs[ViewFormat.LIVE] ?: 0L, primary.copy(alpha = FORMAT_TERTIARY)),
            Triple(R.string.recap_format_music, music?.activity?.totalMs ?: 0L, MaterialTheme.colorScheme.secondary),
        ).filter { it.second > 0L }
    if (shares.isEmpty()) return
    val entrance = rememberStatEntrance(key)
    val labels = shares.map { (label, ms, _) -> stringResource(R.string.recap_format_share, stringResource(label), spentTimeLabel(ms)) }
    StatCard(title = stringResource(R.string.recap_formats_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SectionSpacing), verticalAlignment = Alignment.CenterVertically) {
            StatSplitRing(
                shares = shares.map { it.second.toFloat() to it.third },
                entrance = entrance,
                description = labels.joinToString(),
                modifier = Modifier.weight(1f),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChipSpacing)) {
                shares.zip(labels).forEach { (share, label) -> StatLegendItem(label, share.third) }
            }
        }
    }
}

@Composable
internal fun DiscoveriesCard(
    video: VideoRecap?,
    music: MusicRecap?,
) {
    val channels = video?.discoveredChannels.orEmpty()
    val artists = music?.discoveredArtists.orEmpty()
    if (channels.isEmpty() && artists.isEmpty()) return
    StatCard(title = stringResource(R.string.recap_discoveries_title)) {
        if (channels.isNotEmpty()) {
            NamedGroup(pluralStringResource(R.plurals.recap_new_channels, channels.size, channels.size), channels)
        }
        if (artists.isNotEmpty()) {
            NamedGroup(pluralStringResource(R.plurals.recap_new_artists, artists.size, artists.size), artists)
        }
    }
}

@Composable
private fun NamedGroup(
    heading: String,
    items: List<RankedItem>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ChipSpacing)) {
        Text(heading, style = MaterialTheme.typography.titleSmall)
        Text(
            text = items.take(TOP_NAMES).joinToString { it.name },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val TOP_NAMES = 8

/** Skips, dislikes and blocks: shown plainly, never as a judgement. */
@Composable
internal fun PassedOnCard(
    video: VideoRecap?,
    music: MusicRecap?,
) {
    val dislikes = video?.dislikes.orEmpty()
    val notInterested = video?.actions?.get(LedgerAction.NOT_INTERESTED) ?: 0
    val blocked = (video?.actions?.get(LedgerAction.BLOCK_CHANNEL) ?: 0) + (music?.blockedArtists?.size ?: 0)
    val skippedVideos = video?.skippedVideos.orEmpty()
    val skippedTracks = music?.skippedTracks.orEmpty()
    if (dislikes.isEmpty() && notInterested == 0 && blocked == 0 && skippedVideos.isEmpty() && skippedTracks.isEmpty()) return
    StatCard(title = stringResource(R.string.recap_passed_title), subtitle = stringResource(R.string.recap_passed_subtitle)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SectionSpacing * 2)) {
            if (video != null) StatBigNumber(dislikes.size.toString(), stringResource(R.string.recap_dislikes))
            if (video != null) StatBigNumber(notInterested.toString(), stringResource(R.string.recap_not_interested))
            StatBigNumber(blocked.toString(), stringResource(R.string.recap_blocked))
        }
        if (skippedVideos.isNotEmpty()) {
            Text(stringResource(R.string.recap_skipped_videos), style = MaterialTheme.typography.titleSmall)
            RankedRows(skippedVideos.take(SHORT_LIST)) { timesLabel(it) }
        }
        if (skippedTracks.isNotEmpty()) {
            Text(stringResource(R.string.recap_skipped_tracks), style = MaterialTheme.typography.titleSmall)
            RankedRows(skippedTracks.take(SHORT_LIST)) { timesLabel(it) }
        }
    }
}

@Composable
internal fun LibraryCard(video: VideoRecap) {
    val actions = video.actions
    StatCard(title = stringResource(R.string.recap_library_title)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SectionSpacing * 2),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            StatBigNumber((actions[LedgerAction.LIKE] ?: 0).toString(), stringResource(R.string.recap_likes))
            StatBigNumber((actions[LedgerAction.SAVE] ?: 0).toString(), stringResource(R.string.recap_saves))
            StatBigNumber((actions[LedgerAction.DOWNLOAD] ?: 0).toString(), stringResource(R.string.recap_downloads))
            StatBigNumber((actions[LedgerAction.SEARCH] ?: 0).toString(), stringResource(R.string.recap_searches))
        }
        if (video.topQueries.isNotEmpty()) {
            Text(stringResource(R.string.recap_top_searches), style = MaterialTheme.typography.titleSmall)
            RankedRows(video.topQueries.take(SHORT_LIST)) { timesLabel(it) }
        }
    }
}

@Composable
internal fun SponsorCard(video: VideoRecap) {
    StatCard(title = stringResource(R.string.recap_sponsor_title)) {
        StatBigNumber(spentTimeLabel(video.sponsorSavedMs), stringResource(R.string.recap_sponsor_label), emphasized = true)
        Column(verticalArrangement = Arrangement.spacedBy(ChipSpacing)) {
            video.sponsorSkippedMs.entries.sortedByDescending { it.value }.forEach { (category, ms) ->
                Row {
                    Text(sponsorLabel(category), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(spentTimeLabel(ms), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun sponsorLabel(category: String): String = sponsorCategoryLabelRes(category)?.let { stringResource(it) } ?: category.readable()

@Composable
internal fun HistoryCard(channels: List<ChannelVideoCount>) {
    StatCard(title = stringResource(R.string.recap_history_title), subtitle = stringResource(R.string.recap_history_subtitle)) {
        RankedRows(
            channels.take(RANKED_ROWS).map { RankedItem(it.channelId, it.channelName.ifBlank { it.channelId }, it.videos) },
        ) { pluralStringResource(R.plurals.recap_videos_count, it.count, it.count) }
    }
}

/** A topic name on a tonal pill; it is a label, not a control. */
@Composable
private fun TopicLabel(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = TopicPaddingH, vertical = TopicPaddingV),
        )
    }
}

private val TopicPaddingH = 12.dp
private val TopicPaddingV = 6.dp

internal fun String.readable(): String = replace('_', ' ').trim().replaceFirstChar { it.titlecase() }
