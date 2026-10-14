package io.github.aedev.flow.ui.components.videoplayer.subtitle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.text.Cue
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowColorPickerDialog
import io.github.aedev.flow.ui.components.shared.FlowConnectedToggleGroup
import io.github.aedev.flow.ui.components.shared.FlowFilterChip
import io.github.aedev.flow.ui.components.shared.FlowSwitch
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import io.github.aedev.flow.ui.theme.SubtitleBackgroundSwatches
import io.github.aedev.flow.ui.theme.SubtitleEdgeSwatches
import io.github.aedev.flow.ui.theme.SubtitleTextSwatches

private enum class ColorTarget { TEXT, BACKGROUND, WINDOW, EDGE }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubtitleCustomizer(
    currentStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<ColorTarget?>(null) }
    var editingVertical by rememberSaveable { mutableStateOf(false) }
    val previewText = stringResource(R.string.subtitle_preview_text)
    val previewCues = remember(previewText) { listOf(Cue.Builder().setText(previewText).build()) }

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.subtitle_customization_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        // The preview is drawn by the same view as playback, so every style shows exactly as it plays.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PreviewHeight)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            StyledSubtitles(
                cues = previewCues,
                style = currentStyle,
                modifier = Modifier.fillMaxSize().padding(bottom = (currentStyle.bottomPadding * PREVIEW_PADDING_SCALE).dp),
            )
        }

        LabeledSlider(
            label = stringResource(R.string.subtitle_font_size_template, currentStyle.fontSize.toInt()),
            value = currentStyle.fontSize,
            onValueChange = { onStyleChange(currentStyle.copy(fontSize = it)) },
            valueRange = 12f..32f,
            steps = 10,
        )
        LabeledSlider(
            label = stringResource(R.string.subtitle_position_template, currentStyle.bottomPadding.toInt()),
            value = currentStyle.bottomPadding,
            onValueChange = { onStyleChange(currentStyle.copy(bottomPadding = it)) },
            valueRange = POSITION_RANGE,
            steps = POSITION_STEPS,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val fullscreenPosition =
                if (editingVertical) currentStyle.verticalFullscreenBottomPadding else currentStyle.fullscreenBottomPadding
            FlowConnectedToggleGroup(
                options =
                    listOf(
                        FlowToggleOption(value = false, label = stringResource(R.string.subtitle_fullscreen_landscape)),
                        FlowToggleOption(value = true, label = stringResource(R.string.subtitle_fullscreen_vertical)),
                    ),
                selected = editingVertical,
                onSelected = { editingVertical = it },
            )
            LabeledSlider(
                label = stringResource(R.string.subtitle_fullscreen_position_template, fullscreenPosition.toInt()),
                value = fullscreenPosition,
                onValueChange = { position ->
                    onStyleChange(
                        if (editingVertical) {
                            currentStyle.copy(verticalFullscreenBottomPadding = position)
                        } else {
                            currentStyle.copy(fullscreenBottomPadding = position)
                        },
                    )
                },
                valueRange = POSITION_RANGE,
                steps = POSITION_STEPS,
            )
        }

        SubtitleColorRow(
            label = stringResource(R.string.subtitle_text_color),
            swatches = SubtitleTextSwatches,
            selected = currentStyle.textColor,
            onSelect = { onStyleChange(currentStyle.copy(textColor = it)) },
            onCustom = { editing = ColorTarget.TEXT },
        )

        SubtitleColorRow(
            label = stringResource(R.string.subtitle_background_color),
            swatches = SubtitleBackgroundSwatches,
            selected = currentStyle.backgroundColor,
            onSelect = { onStyleChange(currentStyle.copy(backgroundColor = it.withAlphaOf(currentStyle.backgroundColor))) },
            onCustom = { editing = ColorTarget.BACKGROUND },
        )
        LabeledSlider(
            label = stringResource(R.string.subtitle_background_opacity_template, (currentStyle.backgroundColor.alpha * 100).toInt()),
            value = currentStyle.backgroundColor.alpha,
            onValueChange = { onStyleChange(currentStyle.copy(backgroundColor = currentStyle.backgroundColor.copy(alpha = it))) },
        )

        SubtitleColorRow(
            label = stringResource(R.string.subtitle_window_color),
            swatches = SubtitleBackgroundSwatches,
            selected = currentStyle.windowColor,
            onSelect = { onStyleChange(currentStyle.copy(windowColor = it.withAlphaOf(currentStyle.windowColor))) },
            onCustom = { editing = ColorTarget.WINDOW },
        )
        LabeledSlider(
            label = stringResource(R.string.subtitle_window_opacity_template, (currentStyle.windowColor.alpha * 100).toInt()),
            value = currentStyle.windowColor.alpha,
            onValueChange = { onStyleChange(currentStyle.copy(windowColor = currentStyle.windowColor.copy(alpha = it))) },
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.subtitle_edge_style), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitleEdgeType.entries.forEach { type ->
                    FlowFilterChip(
                        label = stringResource(type.labelRes),
                        selected = currentStyle.edgeType == type,
                        onClick = { onStyleChange(currentStyle.copy(edgeType = type)) },
                    )
                }
            }
        }
        if (currentStyle.edgeType != SubtitleEdgeType.NONE) {
            SubtitleColorRow(
                label = stringResource(R.string.subtitle_edge_color),
                swatches = SubtitleEdgeSwatches,
                selected = currentStyle.edgeColor,
                onSelect = { onStyleChange(currentStyle.copy(edgeColor = it)) },
                onCustom = { editing = ColorTarget.EDGE },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.subtitle_bold_text), style = MaterialTheme.typography.labelLarge)
            FlowSwitch(
                checked = currentStyle.isBold,
                onCheckedChange = { onStyleChange(currentStyle.copy(isBold = it)) },
            )
        }

        OutlinedButton(
            onClick = { onStyleChange(SubtitleStyle()) },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.subtitle_reset_default))
        }
    }

    editing?.let { target ->
        CustomColorDialog(
            target = target,
            style = currentStyle,
            onApply = { onStyleChange(it) },
            onDismiss = { editing = null },
        )
    }
}

/** Opacity has its own slider, so the picker edits only the colour and keeps the alpha. */
@Composable
private fun CustomColorDialog(
    target: ColorTarget,
    style: SubtitleStyle,
    onApply: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val (titleRes, current) =
        when (target) {
            ColorTarget.TEXT -> R.string.subtitle_text_color to style.textColor
            ColorTarget.BACKGROUND -> R.string.subtitle_background_color to style.backgroundColor
            ColorTarget.WINDOW -> R.string.subtitle_window_color to style.windowColor
            ColorTarget.EDGE -> R.string.subtitle_edge_color to style.edgeColor
        }
    FlowColorPickerDialog(
        title = stringResource(titleRes),
        initialArgb = current.copy(alpha = 1f).toArgb().toLong() and ARGB_MASK,
        allowAlpha = false,
        onDismiss = onDismiss,
        onApply = { argb ->
            val picked = Color(argb.toInt())
            onApply(
                when (target) {
                    ColorTarget.TEXT -> style.copy(textColor = picked)
                    ColorTarget.BACKGROUND -> style.copy(backgroundColor = picked.withAlphaOf(style.backgroundColor))
                    ColorTarget.WINDOW -> style.copy(windowColor = picked.withAlphaOf(style.windowColor))
                    ColorTarget.EDGE -> style.copy(edgeColor = picked)
                },
            )
            onDismiss()
        },
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

/** A colour chosen for a see-through layer: its current opacity, or a readable one if it had none. */
private fun Color.withAlphaOf(current: Color): Color = copy(alpha = current.alpha.takeIf { it > 0f } ?: DEFAULT_LAYER_ALPHA)

private val SubtitleEdgeType.labelRes: Int
    get() =
        when (this) {
            SubtitleEdgeType.NONE -> R.string.subtitle_edge_none
            SubtitleEdgeType.OUTLINE -> R.string.subtitle_edge_outline
            SubtitleEdgeType.DROP_SHADOW -> R.string.subtitle_edge_drop_shadow
            SubtitleEdgeType.RAISED -> R.string.subtitle_edge_raised
            SubtitleEdgeType.DEPRESSED -> R.string.subtitle_edge_depressed
        }

private val PreviewHeight = 150.dp
private val POSITION_RANGE = 0f..300f
private const val POSITION_STEPS = 29
private const val PREVIEW_PADDING_SCALE = 0.35f
private const val DEFAULT_LAYER_ALPHA = 0.6f
private const val ARGB_MASK = 0xFFFFFFFFL
