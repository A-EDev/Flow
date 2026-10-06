package io.github.aedev.flow.ui.screens.settings.playback

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.DoubleTapSeekZone
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog

private const val DIAGRAM_ASPECT_RATIO = 16f / 9f
private val SectionSpacing = 12.dp
private val OptionPadding = 8.dp
private val OptionSpacing = 10.dp
private val RadioLabelSpacing = 4.dp
private val CellPadding = 4.dp
private val CellSpacing = 6.dp
private val ZoneIconSize = 20.dp
private val DividerWidth = 1.dp
private val DashLength = 4.dp
private val DashGap = 4.dp

/**
 * Picks how wide the double-tap seek sides are. Each option draws the player split the way that
 * width splits it, so the choice is made by looking at the zones rather than reading fractions.
 */
@Composable
internal fun SeekZoneWidthDialog(
    selected: DoubleTapSeekZone,
    stepSeconds: Int,
    onSelect: (DoubleTapSeekZone) -> Unit,
    onDismiss: () -> Unit,
) {
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.player_settings_seek_zone_width)) },
        text = {
            Column(
                modifier = Modifier.selectableGroup().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                Text(
                    text = stringResource(R.string.player_settings_seek_zone_width_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                DoubleTapSeekZone.entries.forEachIndexed { index, zone ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SeekZoneOption(
                        zone = zone,
                        selected = zone == selected,
                        stepSeconds = stepSeconds,
                        onClick = {
                            onSelect(zone)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@StringRes
internal fun DoubleTapSeekZone.labelRes(): Int =
    when (this) {
        DoubleTapSeekZone.NORMAL -> R.string.player_settings_seek_zone_normal
        DoubleTapSeekZone.NARROW -> R.string.player_settings_seek_zone_narrow
    }

@StringRes
private fun DoubleTapSeekZone.captionRes(): Int =
    when (this) {
        DoubleTapSeekZone.NORMAL -> R.string.player_settings_seek_zone_normal_desc
        DoubleTapSeekZone.NARROW -> R.string.player_settings_seek_zone_narrow_desc
    }

@Composable
private fun SeekZoneOption(
    zone: DoubleTapSeekZone,
    selected: Boolean,
    stepSeconds: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = OptionPadding),
        verticalArrangement = Arrangement.spacedBy(OptionSpacing),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RadioLabelSpacing),
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(text = stringResource(zone.labelRes()), style = MaterialTheme.typography.titleMedium)
        }
        SeekZoneDiagram(sideFraction = zone.sideFraction, stepSeconds = stepSeconds)
        Text(
            text = stringResource(zone.captionRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The player split into its tap zones, always left to right as the gesture layer reads them. */
@Composable
private fun SeekZoneDiagram(
    sideFraction: Float,
    stepSeconds: Int,
) {
    val colors = MaterialTheme.colorScheme
    val dividerColor = colors.outline
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(DIAGRAM_ASPECT_RATIO)
                    .clip(MaterialTheme.shapes.large)
                    .drawWithContent {
                        drawContent()
                        val dashes = PathEffect.dashPathEffect(floatArrayOf(DashLength.toPx(), DashGap.toPx()))
                        listOf(sideFraction, 1f - sideFraction).forEach { fraction ->
                            val x = size.width * fraction
                            drawLine(
                                color = dividerColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = DividerWidth.toPx(),
                                pathEffect = dashes,
                            )
                        }
                    }.clearAndSetSemantics {},
        ) {
            ZoneCell(
                weight = sideFraction,
                icons = listOf(Icons.Rounded.FastRewind),
                label = stringResource(R.string.player_settings_seek_zone_back, stepSeconds),
                container = colors.secondaryContainer,
                content = colors.onSecondaryContainer,
            )
            ZoneCell(
                weight = 1f - 2f * sideFraction,
                icons = listOf(Icons.Rounded.PlayArrow, Icons.Rounded.Pause),
                label = stringResource(R.string.player_settings_seek_zone_play_pause),
                container = colors.primaryContainer,
                content = colors.onPrimaryContainer,
            )
            ZoneCell(
                weight = sideFraction,
                icons = listOf(Icons.Rounded.FastForward),
                label = stringResource(R.string.player_settings_seek_zone_forward, stepSeconds),
                container = colors.secondaryContainer,
                content = colors.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun RowScope.ZoneCell(
    weight: Float,
    icons: List<ImageVector>,
    label: String,
    container: Color,
    content: Color,
) {
    CompositionLocalProvider(LocalContentColor provides content) {
        Column(
            modifier =
                Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(container)
                    .padding(horizontal = CellPadding),
            verticalArrangement = Arrangement.spacedBy(CellSpacing, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                icons.forEach { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(ZoneIconSize)) }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
