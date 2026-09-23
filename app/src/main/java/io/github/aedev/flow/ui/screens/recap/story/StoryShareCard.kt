package io.github.aedev.flow.ui.screens.recap.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.stats.RecapSummary
import io.github.aedev.flow.ui.components.shared.FlowMorphingPortrait
import io.github.aedev.flow.ui.components.stats.spentTimeLabel
import io.github.aedev.flow.ui.screens.recap.labels
import io.github.aedev.flow.ui.screens.recap.readable

/** A 9:16 poster, the shape story apps expect; the image shared is always this size. */
internal val ShareCardWidth = 360.dp
internal val ShareCardHeight = 640.dp
private val CardPadding = 24.dp
private val CardSpacing = 12.dp
private val TileSpacing = 8.dp
private val PortraitSize = 124.dp
private val PortraitOverlap = (-30).dp
private val BadgeSize = 40.dp
private val BadgeIcon = 20.dp
private val TilePadding = 12.dp
private val EyebrowTracking = 2.sp
private const val STATIC_BACKDROP = 0.35f

/**
 * The card the story ends on and the image that gets shared: the period's two faces, its time in a
 * headline figure, four tiles, the trait that stood out and the Flow mark. It inverts its page's
 * tint (accent ground, container ink) so it reads as an object on the page and as a poster alone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StoryShareCard(
    summary: RecapSummary,
    periodLabel: String,
    tint: StoryTint,
    modifier: Modifier = Modifier,
) {
    val channel = summary.video.topChannels.firstOrNull()
    val artist = summary.music.topArtists.firstOrNull()
    val ground = tint.accent
    val ink = tint.container
    Box(
        modifier =
            modifier
                .size(ShareCardWidth, ShareCardHeight)
                .clip(MaterialTheme.shapes.extraLarge)
                .storyBackdrop(ground, ink, seed = SHARE_SEED) { STATIC_BACKDROP },
    ) {
        Column(Modifier.fillMaxSize().padding(CardPadding), verticalArrangement = Arrangement.spacedBy(CardSpacing)) {
            Text(
                text = stringResource(R.string.recap_title).uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = EyebrowTracking),
                color = ink,
                fontWeight = FontWeight.Bold,
            )
            Text(periodLabel, style = MaterialTheme.typography.headlineMedium, color = ink, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically) {
                channel?.let { FlowMorphingPortrait(it.imageUrl, it.name, PortraitSize, tint.raised, tint.onContainer) }
                artist?.let {
                    FlowMorphingPortrait(
                        imageUrl = it.imageUrl,
                        fallback = it.name,
                        diameter = PortraitSize,
                        accent = ink,
                        onAccent = ground,
                        modifier = Modifier.offset(x = if (channel != null) PortraitOverlap else 0.dp),
                        delayIndex = 2,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Column {
                Text(
                    spentTimeLabel(summary.combined.totalMs),
                    style = MaterialTheme.typography.displaySmall,
                    color = ink,
                    fontWeight = FontWeight.Black,
                )
                Text(stringResource(R.string.recap_total_time), style = MaterialTheme.typography.titleSmall, color = ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                Tile(summary.video.views.toString(), stringResource(R.string.recap_views), tint, Modifier.weight(1f))
                Tile(summary.music.plays.toString(), stringResource(R.string.recap_plays), tint, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                Tile(summary.combined.activeDays.toString(), stringResource(R.string.recap_active_days), tint, Modifier.weight(1f))
                Tile(summary.combined.longestStreak.toString(), stringResource(R.string.recap_streak), tint, Modifier.weight(1f))
            }
            NamedLine(stringResource(R.string.recap_summary_top_channel), channel?.name, ink)
            NamedLine(stringResource(R.string.recap_summary_top_artist), artist?.name, ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                summary.insights.firstOrNull()?.let { insight ->
                    Surface(
                        shape = MaterialShapes.Sunny.toShape(),
                        color = ink,
                        contentColor = ground,
                        modifier = Modifier.size(BadgeSize),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(BadgeIcon)) }
                    }
                    Text(
                        stringResource(insight.labels().first),
                        style = MaterialTheme.typography.titleMedium,
                        color = ink,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.recap_story_made_with), style = MaterialTheme.typography.labelMedium, color = ink)
            }
        }
    }
}

private const val SHARE_SEED = 3

@Composable
private fun Tile(
    value: String,
    label: String,
    tint: StoryTint,
    modifier: Modifier,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = tint.raised, contentColor = tint.onContainer) {
        Column(Modifier.padding(TilePadding)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NamedLine(
    label: String,
    value: String?,
    ink: Color,
) {
    if (value.isNullOrBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ink)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = ink,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
