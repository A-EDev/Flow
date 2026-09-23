package io.github.aedev.flow.ui.components.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R

private val CardPadding = 20.dp
private val CardSpacing = 16.dp
private val RankWidth = 28.dp
private val NumberSpacing = 2.dp
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60

/** One section of a stats page: a titled tonal surface. */
@Composable
fun StatCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(CardSpacing)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/** A headline figure with its label under it. */
@Composable
fun StatBigNumber(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NumberSpacing)) {
        Text(
            text = value,
            style =
                (if (emphasized) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium)
                    .copy(fontFeatureSettings = "tnum"),
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A row of a ranked list: position, name, a detail line and the figure it ranks by. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatRankRow(
    rank: Int,
    title: String,
    value: String,
    shape: Shape,
    detail: String? = null,
    imageUrl: String = "",
    imageShape: Shape = MaterialTheme.shapes.medium,
) {
    SegmentedListItem(
        verticalAlignment = Alignment.CenterVertically,
        shapes = ListItemDefaults.shapes(shape = shape),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RankImageGap)) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = RankWidth),
                )
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(RankImageSize).clip(imageShape).background(MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
        },
        supportingContent = detail?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = { Text(value, style = MaterialTheme.typography.labelLarge) },
    ) {
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A small legend swatch and its label, for charts drawn in more than one colour. */
@Composable
fun StatLegendItem(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(CardSpacing / 2), verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(LegendSwatch), shape = MaterialTheme.shapes.extraSmall, color = color) {}
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private val LegendSwatch = 12.dp
private val RankImageSize = 44.dp
private val RankImageGap = 12.dp

/** Time spent, as people say it: "3 h 12 min", or minutes alone under an hour. */
@Composable
fun spentTimeLabel(millis: Long): String {
    val totalMinutes = (millis / MILLIS_PER_MINUTE).toInt()
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
    }
}
