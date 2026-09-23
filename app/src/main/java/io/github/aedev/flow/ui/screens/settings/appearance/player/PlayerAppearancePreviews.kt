package io.github.aedev.flow.ui.screens.settings.appearance.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.GestureOverlayStyle
import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle
import io.github.aedev.flow.data.local.SliderStyle
import io.github.aedev.flow.ui.components.musicplayer.ExpressivePlayerSlider
import io.github.aedev.flow.ui.components.musicplayer.ExpressiveWavySlider
import io.github.aedev.flow.ui.components.musicplayer.PlayerBackground
import io.github.aedev.flow.ui.components.musicplayer.SquigglySlider
import io.github.aedev.flow.ui.components.musicplayer.expressiveSliderSpec
import io.github.aedev.flow.ui.components.videoplayer.overlay.GestureLevelHudPreview
import io.github.aedev.flow.ui.theme.PlayerGround

private const val PREVIEW_PROGRESS = 0.4f
private const val PREVIEW_DURATION = 100f
private const val SEEKBAR_PREVIEW_PROGRESS = 0.36f
private val SeekbarPreviewHeight = 44.dp
private val SeekbarPreviewInset = 8.dp
private val SeekbarTrackHeight = 4.dp
private val BackgroundPreviewHeight = 72.dp
private val GesturePreviewHeight = 184.dp

/** The real player slider in [style], parked at 40 %. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SliderStylePreview(style: SliderStyle) {
    val position = PREVIEW_DURATION * PREVIEW_PROGRESS
    val range = 0f..PREVIEW_DURATION
    when (style) {
        SliderStyle.SQUIGGLY -> {
            SquigglySlider(
                value = position,
                onValueChange = {},
                valueRange = range,
                colors =
                    SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        thumbColor = MaterialTheme.colorScheme.primary,
                    ),
                isPlaying = true,
            )
        }

        SliderStyle.EXPRESSIVE_WAVY -> {
            ExpressiveWavySlider(
                value = position,
                onValueChange = {},
                onValueChangeFinished = {},
                valueRange = range,
                isPlaying = true,
            )
        }

        else -> {
            val spec = expressiveSliderSpec(style)
            ExpressivePlayerSlider(
                value = position,
                onValueChange = {},
                onValueChangeFinished = {},
                valueRange = range,
                trackHeight = spec.trackHeight,
                thumbHeight = spec.thumbHeight,
                thumbTrackGap = spec.thumbTrackGap,
            )
        }
    }
}

/**
 * A seek bar inside a stand-in for the video, inset by [padding] on each side. The inset animates
 * with an effects spec, which never overshoots — a spring would push the padding below zero.
 */
@Composable
internal fun SeekbarWidthPreview(padding: Dp) {
    val animated by animateDpAsState(
        targetValue = padding,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "seekbarWidthPreview",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(SeekbarPreviewHeight)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = SeekbarPreviewInset),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = animated.coerceAtLeast(0.dp))
                    .height(SeekbarTrackHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(SEEKBAR_PREVIEW_PROGRESS)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * The music player's own background in [style], painted around Flow's icon in place of album art,
 * so the preview is exactly what the player draws rather than a sketch of it.
 */
@Composable
internal fun MusicBackgroundPreview(style: MusicPlayerBackgroundStyle) {
    val context = LocalContext.current
    val artwork = remember { "android.resource://${context.packageName}/${R.drawable.ic_launcher_foreground}" }
    PlayerBackground(
        thumbnailUrl = artwork,
        style = style,
        paletteBaseColor = MaterialTheme.colorScheme.primary,
        paletteAccentColor = MaterialTheme.colorScheme.tertiary,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(BackgroundPreviewHeight)
                .clip(MaterialTheme.shapes.medium),
    )
}

/** The volume and brightness read-out in [style], over the player's own backdrop. */
@Composable
internal fun GestureOverlayPreview(style: GestureOverlayStyle) {
    GestureLevelHudPreview(
        style = style,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(GesturePreviewHeight)
                .clip(MaterialTheme.shapes.large)
                .background(PlayerGround),
    )
}
