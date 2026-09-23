package io.github.aedev.flow.ui.screens.settings.appearance.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val CardPadding = 16.dp
private val CardSpacing = 16.dp
private val GridSpacing = 12.dp
private val MinCardWidth = 300.dp
private val TileWidth = 64.dp
private val TileHeight = 52.dp
private val PillWidth = 9.dp
private val PillHeight = 30.dp
private val PillSpacing = 5.dp
private val CheckSize = 18.dp
private val TitleCheckSpacing = 8.dp
private val SelectedBorderWidth = 1.5.dp
private val BorderWidth = 1.dp

/**
 * One palette in the theme picker: a swatch tile of three colour pills, the palette's name and a
 * one-line description. The selected card lifts to a higher surface with a neutral outline — the
 * palette's own colours are the only colour on the card.
 */
@Composable
internal fun ThemeCard(
    name: String,
    description: String,
    swatch: ThemeSwatch?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        modifier =
            modifier.semantics {
                role = Role.RadioButton
                this.selected = selected
            },
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = if (selected) colors.surfaceContainerHighest else colors.surfaceContainerLow,
            ),
        border =
            BorderStroke(
                width = if (selected) SelectedBorderWidth else BorderWidth,
                color = if (selected) colors.outline else colors.outlineVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(CardPadding),
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeSwatchTile(swatch)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (selected) {
                        Spacer(Modifier.width(TitleCheckSpacing))
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(CheckSize),
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

/**
 * The palette preview: three pills on the palette's own background. Compose has no palette-swatch
 * component, so this is drawn from boxes; every colour comes from the resolved scheme.
 */
@Composable
internal fun ThemeSwatchTile(swatch: ThemeSwatch?) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(width = TileWidth, height = TileHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(swatch?.tile ?: colors.surfaceContainerHighest)
                .border(BorderWidth, colors.outlineVariant, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        if (swatch != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(PillSpacing)) {
                listOf(swatch.primary, swatch.secondary, swatch.neutral).forEach { color -> SwatchPill(color) }
            }
        }
    }
}

@Composable
private fun SwatchPill(color: Color) {
    Box(
        modifier =
            Modifier
                .size(width = PillWidth, height = PillHeight)
                .clip(CircleShape)
                .background(color),
    )
}

/** One card in a [ThemeCardGrid]: a built-in palette or a custom theme. */
@Immutable
internal data class ThemeCardItem(
    val key: String,
    val name: String,
    val description: String,
    val swatch: ThemeSwatch?,
    val selected: Boolean,
    val onClick: () -> Unit,
    val trailing: (@Composable () -> Unit)? = null,
)

/**
 * Theme cards in as many columns as the space allows — one on a phone, two in a tablet's detail
 * pane — measured from the space itself, not the window, since a pane is narrower than its window.
 */
@Composable
internal fun ThemeCardGrid(items: List<ThemeCardItem>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + GridSpacing) / (MinCardWidth + GridSpacing)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(GridSpacing)) {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                ) {
                    rowItems.forEach { item ->
                        key(item.key) {
                            ThemeCard(
                                name = item.name,
                                description = item.description,
                                swatch = item.swatch,
                                selected = item.selected,
                                onClick = item.onClick,
                                trailing = item.trailing,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                            )
                        }
                    }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(GridSpacing))
        }
    }
}
